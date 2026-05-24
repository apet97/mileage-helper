package com.cake.clockify.client.auth;

import java.time.Instant;
import java.util.Map;

/**
 * Represents the verified claims from a Clockify JWT token.
 */
public record ClockifyClaims(
        String issuer,
        String subject,       // The add-on manifest key
        String type,          // Always "addon"
        Instant expiration,   // Null for non-expiring tokens (like webhook tokens)
        String backendUrl,    // Environment backend base URL
        String reportsUrl,    // Environment reports base URL
        String ptoUrl,        // Environment PTO/time-off base URL
        String locationsUrl,  // Environment locations base URL
        String screenshotsUrl,// Environment screenshots base URL
        String workspaceId,   // Workspace context
        String addonId,       // Add-on installation ID
        String userId,        // User context (claim "user")
        String workspaceRole, // Workspace role context
        String language,      // User language (user token only)
        String theme,         // User theme (user token only)
        Map<String, Object> rawClaims // Underlying raw claims map
) {
    public static final String CLAIM_ISSUER = "iss";
    public static final String CLAIM_SUBJECT = "sub";
    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_EXPIRATION = "exp";
    public static final String CLAIM_BACKEND_URL = "backendUrl";
    public static final String CLAIM_REPORTS_URL = "reportsUrl";
    public static final String CLAIM_PTO_URL = "ptoUrl";
    public static final String CLAIM_LOCATIONS_URL = "locationsUrl";
    public static final String CLAIM_SCREENSHOTS_URL = "screenshotsUrl";
    public static final String CLAIM_WORKSPACE_ID = "workspaceId";
    public static final String CLAIM_ADDON_ID = "addonId";
    public static final String CLAIM_USER_ID = "user";
    public static final String CLAIM_WORKSPACE_ROLE = "workspaceRole";
    public static final String CLAIM_LANGUAGE = "language";
    public static final String CLAIM_THEME = "theme";
}
