package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.model.MileageProjectOptionsResponse;
import com.cake.clockify.addon.mileage.api.model.MileageTaskOptionsResponse;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class MileageOptionsController {
    private final ClockifyExpenseGateway gateway;

    public MileageOptionsController(ClockifyExpenseGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/api/mileage/options/projects")
    public ResponseEntity<MileageProjectOptionsResponse> projects(HttpServletRequest request)
            throws IOException, InterruptedException {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        return ResponseEntity.ok(new MileageProjectOptionsResponse(gateway.listProjects(claims.workspaceId())));
    }

    @GetMapping("/api/mileage/options/tasks")
    public ResponseEntity<MileageTaskOptionsResponse> tasks(
            HttpServletRequest request,
            @RequestParam(required = false) String projectId) throws IOException, InterruptedException {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        return ResponseEntity.ok(new MileageTaskOptionsResponse(gateway.listTasks(claims.workspaceId(), projectId)));
    }
}
