package com.cake.clockify.addon.core;

import com.cake.clockify.addon.core.auth.ClaimsNormalizer;
import com.cake.clockify.addon.core.auth.NormalizedClaims;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClaimsNormalizerTest {

    @Test
    void normalizesCanonicalClaims() {
        Map<String, Object> claims = Map.of(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "backendUrl", "https://api.clockify.me/api",
                "reportsUrl", "https://reports.api.clockify.me",
                "user", "u1",
                "workspaceRole", "ADMIN",
                "exp", 9999999999L
        );
        NormalizedClaims nc = ClaimsNormalizer.normalize(claims);
        assertEquals("ws1", nc.workspaceId());
        assertEquals("ad1", nc.addonId());
        assertEquals("https://api.clockify.me/api", nc.backendUrl());
        assertEquals("ADMIN", nc.workspaceRole());
    }

    @Test
    void normalizesLegacyAliases() {
        Map<String, Object> claims = Map.of(
                "activeWs", "ws-legacy",
                "apiUrl", "https://api.clockify.me/api/v1",
                "addonId", "ad1",
                "exp", 9999999999L
        );
        NormalizedClaims nc = ClaimsNormalizer.normalize(claims);
        assertEquals("ws-legacy", nc.workspaceId());
        // /api/v1 → /api
        assertEquals("https://api.clockify.me/api", nc.backendUrl());
    }

    @Test
    void stripsTrailingSlashFromBackendUrl() {
        Map<String, Object> claims = Map.of(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "backendUrl", "https://api.clockify.me/api/",
                "exp", 9999999999L
        );
        NormalizedClaims nc = ClaimsNormalizer.normalize(claims);
        assertEquals("https://api.clockify.me/api", nc.backendUrl());
    }

    @Test
    void addsApiPathWhenMissing() {
        Map<String, Object> claims = Map.of(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "backendUrl", "https://api.clockify.me",
                "exp", 9999999999L
        );
        NormalizedClaims nc = ClaimsNormalizer.normalize(claims);
        assertEquals("https://api.clockify.me/api", nc.backendUrl());
    }

    @Test
    void rejectsNonClockifyHttpsBackendUrl() {
        Map<String, Object> claims = Map.of(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "backendUrl", "https://evil.example.com/api",
                "exp", 9999999999L
        );
        NormalizedClaims nc = ClaimsNormalizer.normalize(claims);
        assertNull(nc.backendUrl());
    }

    @Test
    void rejectsHttpBackendUrlOutsideLocalhost() {
        Map<String, Object> claims = Map.of(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "backendUrl", "http://api.clockify.me/api",
                "exp", 9999999999L
        );
        NormalizedClaims nc = ClaimsNormalizer.normalize(claims);
        assertNull(nc.backendUrl());
    }

    @Test
    void permitsLocalhostHttpBackendUrlForTestsAndLocalDevelopment() {
        Map<String, Object> claims = Map.of(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "backendUrl", "http://localhost:8080",
                "exp", 9999999999L
        );
        NormalizedClaims nc = ClaimsNormalizer.normalize(claims);
        assertEquals("http://localhost:8080/api", nc.backendUrl());
    }

    @Test
    void rejectsNullClaims() {
        assertThrows(IllegalArgumentException.class, () -> ClaimsNormalizer.normalize(null));
    }

    @Test
    void enrichFromInstalledPayloadFillsMissingBackendUrl() {
        NormalizedClaims existing = new NormalizedClaims("ws1", "addon1", null, null, null, null, null, null, null, null, null, null);
        Map<String, Object> payload = Map.of("apiUrl", "https://api.clockify.me/api");
        NormalizedClaims enriched = ClaimsNormalizer.enrichFromInstalledPayload(existing, payload);
        assertEquals("https://api.clockify.me/api", enriched.backendUrl());
    }

    @Test
    void enrichFromInstalledPayloadFillsMissingUserId() {
        NormalizedClaims existing = new NormalizedClaims("ws1", "ad1", "https://api.clockify.me/api", null, null, null, null, null, null, null, null, null);
        Map<String, Object> payload = Map.of("asUser", "user123");
        NormalizedClaims enriched = ClaimsNormalizer.enrichFromInstalledPayload(existing, payload);
        assertEquals("user123", enriched.userId());
    }
}
