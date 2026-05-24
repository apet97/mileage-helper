package com.cake.clockify.addon.core.auth.filter;

import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import com.cake.clockify.addon.core.auth.ClaimsNormalizer;
import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Verifies the {@code auth_token} query parameter on every {@code /iframe/*} request, and the
 * {@code Authorization: Bearer <token>} header on every {@code /api/*} request.
 * On success, normalizes claims and sets {@link RequestAttributes#NORMALIZED_CLAIMS} on the
 * request so controllers can easily access verified workspace and user context.
 *
 * <p>Authentication is cryptographically verified against the Clockify public key.
 * Failures return 401 Unauthorized.
 */
public class ClockifyIframeAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClockifyIframeAuthFilter.class);
    private static final String PARAM_AUTH_TOKEN = "auth_token";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "bearer ";

    private final ClockifySignatureParser parser;

    public ClockifyIframeAuthFilter(ClockifySignatureParser parser) {
        this.parser = parser;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/iframe/") && !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        String token = tokenFromRequest(request);

        if (token == null || token.isBlank()) {
            log.debug("iframe-api.auth.missing-token path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Map<String, Object> claims;
        try {
            claims = parser.parseClaims(token);
        } catch (Exception e) {
            log.debug("iframe.auth.parse-failed path={} reason={}", request.getRequestURI(), e.getClass().getSimpleName());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (claims.get("exp") == null) {
            log.debug("iframe.auth.missing-exp path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        NormalizedClaims normalized;
        try {
            normalized = ClaimsNormalizer.normalize(claims);
        } catch (IllegalArgumentException e) {
            log.debug("iframe.auth.normalization-failed path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (normalized.workspaceId() == null || normalized.addonId() == null) {
            log.debug("iframe.auth.missing-identity path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        request.setAttribute(RequestAttributes.NORMALIZED_CLAIMS, normalized);
        chain.doFilter(request, response);
    }

    private static String tokenFromRequest(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/")) {
            return bearerToken(request);
        }
        String token = request.getParameter(PARAM_AUTH_TOKEN);
        if (token == null || token.isBlank()) {
            token = bearerToken(request);
        }
        return token;
    }

    private static String bearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HEADER_AUTHORIZATION);
        if (authHeader != null && authHeader.toLowerCase().startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
