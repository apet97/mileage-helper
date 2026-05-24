package com.cake.clockify.addon.core.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestAttributes {

    public static final String NORMALIZED_CLAIMS = "clockify.addon.normalized-claims";

    private RequestAttributes() {
    }

    public static NormalizedClaims claims(HttpServletRequest request) {
        Object value = request.getAttribute(NORMALIZED_CLAIMS);
        return value instanceof NormalizedClaims c ? c : null;
    }

    public static NormalizedClaims requireClaims(HttpServletRequest request) {
        NormalizedClaims claims = claims(request);
        if (claims == null) {
            throw new IllegalStateException("NormalizedClaims missing — Clockify auth filter not applied to this request");
        }
        return claims;
    }
}
