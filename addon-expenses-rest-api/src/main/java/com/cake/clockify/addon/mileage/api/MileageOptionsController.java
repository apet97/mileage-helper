package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.model.MileageProjectOptionsResponse;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class MileageOptionsController {
    private static final Logger log = LoggerFactory.getLogger(MileageOptionsController.class);
    private static final String PROJECTS_UNAVAILABLE =
            "Clockify projects are temporarily unavailable. Try again in a moment.";

    private final ClockifyExpenseGateway gateway;

    public MileageOptionsController(ClockifyExpenseGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/api/mileage/options/projects")
    public ResponseEntity<MileageProjectOptionsResponse> projects(HttpServletRequest request) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        try {
            return ResponseEntity.ok(MileageProjectOptionsResponse.from(gateway.listProjects(claims.workspaceId())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable(claims.workspaceId(), e);
        } catch (IOException | RuntimeException e) {
            // A Clockify timeout/transport failure is an IOException; degrade to an empty list + warning
            // (a populated dropdown is a convenience, not a reason to 500 the whole panel).
            return unavailable(claims.workspaceId(), e);
        }
    }

    private static ResponseEntity<MileageProjectOptionsResponse> unavailable(String workspaceId, Exception e) {
        log.warn("Clockify project lookup failed for workspace {}: {}", workspaceId, e.toString());
        return ResponseEntity.ok(MileageProjectOptionsResponse.unavailable(PROJECTS_UNAVAILABLE));
    }
}
