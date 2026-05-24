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
 * Verifies the {@code X-Addon-Lifecycle-Token} JWT on every {@code POST /lifecycle/*} request.
 * On success, normalizes claims and sets {@link RequestAttributes#NORMALIZED_CLAIMS} on the
 * request so lifecycle controllers can read workspaceId / addonId / backendUrl from a single
 * pre-verified source.
 *
 * <p>The SDK's {@link ClockifySignatureParser} enforces RS256 signature, {@code iss=clockify},
 * {@code type=addon}, {@code sub=<manifest-key>}, and JJWT rejects an expired {@code exp}.
 * We additionally require {@code workspaceId} and {@code addonId} to be present after
 * alias normalization. Failures return 401 with no body.
 */
public class ClockifyLifecycleAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClockifyLifecycleAuthFilter.class);
    private static final String HEADER = "X-Addon-Lifecycle-Token";

    private final ClockifySignatureParser parser;

    public ClockifyLifecycleAuthFilter(ClockifySignatureParser parser) {
        this.parser = parser;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/lifecycle/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token == null || token.isBlank()) {
            log.debug("lifecycle.auth.missing-token path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Map<String, Object> claims;
        try {
            claims = parser.parseClaims(token);
        } catch (Exception e) {
            log.debug("lifecycle.auth.parse-failed path={} reason={}", request.getRequestURI(), e.getClass().getSimpleName());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (claims.get("exp") == null) {
            log.debug("lifecycle.auth.missing-exp path={}", request.getRequestURI());
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
            log.debug("lifecycle.auth.missing-identity path={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        request.setAttribute(RequestAttributes.NORMALIZED_CLAIMS, normalized);
        chain.doFilter(request, response);
    }
}
