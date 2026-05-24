package com.cake.clockify.addon.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sets marketplace-required security headers on every response:
 * <ul>
 *   <li>CSP with {@code frame-ancestors} for Clockify iframe embedding.
 *   <li>{@code X-Content-Type-Options: nosniff}
 *   <li>{@code Referrer-Policy: no-referrer}
 *   <li>{@code Permissions-Policy} denying camera, microphone, geolocation.
 *   <li>HSTS (if enabled and request is over HTTPS).
 *   <li>{@code Cache-Control: no-store} for API paths.
 * </ul>
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final ClockifyAddonProperties props;

    public SecurityHeadersFilter(ClockifyAddonProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", buildCsp());
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (props.enableHsts() && (request.isSecure() || isForwardedHttps(request))) {
            response.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
        }
        if (isNoStorePath(request.getRequestURI())) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }

    private String buildCsp() {
        List<String> ancestors = new ArrayList<>();
        ancestors.add("https://app.clockify.me");
        ancestors.add("https://*.clockify.me");
        ancestors.addAll(props.extraFrameAncestorList());
        return "default-src 'self'; "
                + "script-src 'self'; "
                + "style-src 'self' https://resources.developer.clockify.me; "
                + "img-src 'self' data: https://resources.developer.clockify.me; "
                + "font-src 'self' https://resources.developer.clockify.me; "
                + "connect-src 'self' https://*.clockify.me; "
                + "frame-ancestors " + String.join(" ", ancestors);
    }

    private static boolean isForwardedHttps(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && proto.equalsIgnoreCase("https");
    }

    private static boolean isNoStorePath(String uri) {
        return uri.equals("/api")
                || uri.startsWith("/api/")
                || uri.equals("/iframe/api")
                || uri.startsWith("/iframe/api/")
                || uri.equals("/clockify")
                || uri.startsWith("/clockify/");
    }
}
