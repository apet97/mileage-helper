package com.cake.clockify.addon.mileage.security;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Service
public class MileageAuthorizationService {
    private static final Set<String> ADMIN_ROLES = Set.of("OWNER", "ADMIN");

    public void requireAdmin(NormalizedClaims claims) {
        String role = claims == null ? null : claims.workspaceRole();
        if (role == null || !ADMIN_ROLES.contains(role.toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required");
        }
    }
}
