package com.cake.clockify.addon.core.auth.filter;

import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import com.cake.clockify.addon.core.auth.ClaimsNormalizer;
import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.core.webhook.WebhookTokenLookup;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Three-check verification for every webhook delivery:
 * <ol>
 *   <li>RS256 signature on {@code Clockify-Signature} JWT (SDK parser does iss/type/sub/alg/sig).
 *   <li>Route path matches a webhook registered at INSTALLED time (lookup by workspaceId + addonId + path).
 *   <li>Incoming {@code Clockify-Signature} token matches the stored per-webhook authToken (after decryption).
 * </ol>
 * Failures return 401 (signature) or 403 (token-mismatch/unknown webhook). On success, sets
 * verified claims and event type on the request for downstream webhook handlers.
 */
public class ClockifyWebhookAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClockifyWebhookAuthFilter.class);
    private static final String SIGNATURE_HEADER = "Clockify-Signature";
    private static final String EVENT_TYPE_HEADER = "Clockify-Webhook-Event-Type";
    public static final String ATTR_EVENT_TYPE = "clockify.addon.webhook-event-type";

    private final ClockifySignatureParser parser;
    private final WebhookTokenLookup tokenLookup;
    private final TokenCodec codec;
    private final com.cake.clockify.addon.core.config.ClockifyAddonProperties props;

    public ClockifyWebhookAuthFilter(
            ClockifySignatureParser parser,
            WebhookTokenLookup tokenLookup,
            TokenCodec codec,
            com.cake.clockify.addon.core.config.ClockifyAddonProperties props) {
        this.parser = parser;
        this.tokenLookup = tokenLookup;
        this.codec = codec;
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/webhook/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String signatureToken = firstHeader(request, SIGNATURE_HEADER);
        if (signatureToken == null) {
            log.debug("webhook.auth.missing-signature path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Map<String, Object> claims;
        try {
            claims = parser.parseClaims(signatureToken);
        } catch (Exception e) {
            log.debug("webhook.auth.signature-failed path={} reason={}", request.getRequestURI(), e.getClass().getSimpleName());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!"clockify".equals(claims.get("iss")) || !"addon".equals(claims.get("type"))) {
            log.debug("webhook.auth.invalid-issuer-or-type path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!props.key().equals(claims.get("sub"))) {
            log.debug("webhook.auth.invalid-sub path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        NormalizedClaims normalized;
        try {
            normalized = ClaimsNormalizer.normalize(claims);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (normalized.workspaceId() == null || normalized.addonId() == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String normalizedPath = normalizePath(request.getRequestURI(), request.getContextPath());
        Optional<WebhookTokenLookup.StoredWebhookToken> stored =
                tokenLookup.findByWorkspaceAddonPath(normalized.workspaceId(), normalized.addonId(), normalizedPath);
        if (stored.isEmpty()) {
            log.debug("webhook.auth.no-stored-token workspace={} path={}", normalized.workspaceId(), normalizedPath);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String storedPlaintext;
        try {
            byte[] cipher = Base64.getDecoder().decode(stored.get().encryptedToken());
            storedPlaintext = codec.decrypt(stored.get().keyId(), cipher);
        } catch (RuntimeException e) {
            log.warn("webhook.auth.decrypt-failed workspace={}", normalized.workspaceId());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        boolean tokenMatched = safeEquals(storedPlaintext, signatureToken);
        if (!tokenMatched) {
            log.debug("webhook.auth.token-mismatch workspace={}", normalized.workspaceId());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String headerEvent = firstHeader(request, EVENT_TYPE_HEADER);
        if (headerEvent == null) {
            log.debug("webhook.auth.missing-event-header workspace={}", normalized.workspaceId());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!headerEvent.equals(stored.get().eventType())) {
            log.debug("webhook.auth.event-type-mismatch workspace={} expected={} actual={}", 
                    normalized.workspaceId(), stored.get().eventType(), headerEvent);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        request.setAttribute(RequestAttributes.NORMALIZED_CLAIMS, normalized);
        request.setAttribute(ATTR_EVENT_TYPE, headerEvent);
        chain.doFilter(request, response);
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    /** Strip /webhook prefix and context path so lookup key is canonical (e.g. "/new-time-entry"). */
    private static String normalizePath(String uri, String contextPath) {
        if (uri == null) return "";
        String pathWithoutContext = uri;
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            pathWithoutContext = uri.substring(contextPath.length());
        }
        String stripped = pathWithoutContext.startsWith("/webhook") ? pathWithoutContext.substring("/webhook".length()) : pathWithoutContext;
        return stripped.isEmpty() ? "/" : stripped;
    }

    private static String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value;
    }

}
