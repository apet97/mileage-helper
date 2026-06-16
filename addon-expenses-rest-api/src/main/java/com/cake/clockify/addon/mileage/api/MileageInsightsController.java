package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.model.MileageInsightsResponse;
import com.cake.clockify.addon.mileage.insights.MileageInsightsService;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MileageInsightsController {
    private final MileageAuthorizationService authorizationService;
    private final MileageDateRangeResolver dateRangeResolver;
    private final MileageInsightsService insightsService;

    public MileageInsightsController(
            MileageAuthorizationService authorizationService,
            MileageDateRangeResolver dateRangeResolver,
            MileageInsightsService insightsService) {
        this.authorizationService = authorizationService;
        this.dateRangeResolver = dateRangeResolver;
        this.insightsService = insightsService;
    }

    @GetMapping("/api/mileage/insights")
    public ResponseEntity<MileageInsightsResponse> insights(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        authorizationService.requireAdmin(claims);
        MileageDateRange range = dateRangeResolver.optionalOrDefault(claims, from, to);
        return ResponseEntity.ok(insightsService.insights(claims.workspaceId(), range));
    }
}
