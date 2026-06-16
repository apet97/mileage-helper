package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyListResponse;
import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyRequest;
import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyResponse;
import com.cake.clockify.addon.mileage.policy.MileageRatePolicyService;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class MileageRatePolicyController {
    private final MileageRatePolicyService policyService;
    private final MileageAuthorizationService authorizationService;

    public MileageRatePolicyController(
            MileageRatePolicyService policyService,
            MileageAuthorizationService authorizationService) {
        this.policyService = policyService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/api/mileage/rate-policies")
    public ResponseEntity<MileageRatePolicyListResponse> list(HttpServletRequest request) {
        NormalizedClaims claims = adminClaims(request);
        return ResponseEntity.ok(policyService.listPolicyResponse(claims.workspaceId()));
    }

    @PostMapping("/api/mileage/rate-policies")
    public ResponseEntity<MileageRatePolicyResponse> create(
            HttpServletRequest request,
            @RequestBody MileageRatePolicyRequest body) {
        NormalizedClaims claims = adminClaims(request);
        return ResponseEntity.ok(policyService.createPolicy(claims.workspaceId(), body, claims.userId()));
    }

    @PutMapping("/api/mileage/rate-policies/{id}")
    public ResponseEntity<MileageRatePolicyResponse> update(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestBody MileageRatePolicyRequest body) {
        NormalizedClaims claims = adminClaims(request);
        return ResponseEntity.ok(policyService.updatePolicy(claims.workspaceId(), id, body, claims.userId()));
    }

    @DeleteMapping("/api/mileage/rate-policies/{id}")
    public ResponseEntity<MileageRatePolicyResponse> deactivate(
            HttpServletRequest request,
            @PathVariable UUID id) {
        NormalizedClaims claims = adminClaims(request);
        return ResponseEntity.ok(policyService.deactivatePolicy(claims.workspaceId(), id, claims.userId()));
    }

    private NormalizedClaims adminClaims(HttpServletRequest request) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        authorizationService.requireAdmin(claims);
        return claims;
    }
}
