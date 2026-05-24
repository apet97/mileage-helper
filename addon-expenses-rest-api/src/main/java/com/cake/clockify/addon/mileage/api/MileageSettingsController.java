package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.mileage.api.model.MileageCategoryOptionsResponse;
import com.cake.clockify.addon.mileage.api.model.MileageDiagnosticsResponse;
import com.cake.clockify.addon.mileage.api.model.MileageSettingsRequest;
import com.cake.clockify.addon.mileage.api.model.MileageSettingsResponse;
import com.cake.clockify.addon.mileage.clockify.ClockifyCategoryOption;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
public class MileageSettingsController {
    private final MileageSettingsService settingsService;
    private final ClockifyExpenseGateway gateway;
    private final MileageAuthorizationService authorizationService;
    private final AddonInstallationService installationService;

    public MileageSettingsController(
            MileageSettingsService settingsService,
            ClockifyExpenseGateway gateway,
            MileageAuthorizationService authorizationService,
            AddonInstallationService installationService) {
        this.settingsService = settingsService;
        this.gateway = gateway;
        this.authorizationService = authorizationService;
        this.installationService = installationService;
    }

    @GetMapping("/api/mileage/settings")
    public ResponseEntity<MileageSettingsResponse> getSettings(HttpServletRequest request) {
        NormalizedClaims claims = adminClaims(request);
        return ResponseEntity.ok(enrichSettings(claims.workspaceId(), settingsService.getEffectiveSettings(claims.workspaceId())));
    }

    @PutMapping("/api/mileage/settings")
    public ResponseEntity<MileageSettingsResponse> saveSettings(
            HttpServletRequest request,
            @RequestBody MileageSettingsRequest body) {
        NormalizedClaims claims = adminClaims(request);
        settingsService.saveSettings(claims.workspaceId(), body, claims.userId());
        return ResponseEntity.ok(enrichSettings(claims.workspaceId(), settingsService.getEffectiveSettings(claims.workspaceId())));
    }

    @PostMapping("/api/mileage/settings/mileage-category")
    public ResponseEntity<MileageSettingsResponse> createOrRepairMileageCategory(HttpServletRequest request)
            throws IOException, InterruptedException {
        NormalizedClaims claims = adminClaims(request);
        MileageSettingsResponse settings = settingsService.getEffectiveSettings(claims.workspaceId());
        if (settings.rate() == null || settings.rate().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mileage rate is required before creating a category");
        }
        ClockifyCategoryOption category = gateway.createOrRepairMileageCategory(
                claims.workspaceId(),
                new BigDecimal(settings.rate()));
        settingsService.saveMileageCategory(claims.workspaceId(), category.id(), claims.userId());
        return ResponseEntity.ok(enrichSettings(claims.workspaceId(), settingsService.getEffectiveSettings(claims.workspaceId()))
                .withMileageCategoryName(category.name()));
    }

    @GetMapping("/api/mileage/options/categories")
    public ResponseEntity<MileageCategoryOptionsResponse> categories(HttpServletRequest request)
            throws IOException, InterruptedException {
        NormalizedClaims claims = adminClaims(request);
        return ResponseEntity.ok(MileageCategoryOptionsResponse.from(gateway.listCategories(claims.workspaceId())));
    }

    @GetMapping("/api/mileage/diagnostics")
    public ResponseEntity<MileageDiagnosticsResponse> diagnostics(HttpServletRequest request) {
        NormalizedClaims claims = adminClaims(request);
        MileageSettingsResponse settings = settingsService.getEffectiveSettings(claims.workspaceId());
        boolean installationAvailable = installationService.isInstalled(claims.workspaceId());
        List<String> warnings = new ArrayList<>(settings.diagnostics());
        if (!installationAvailable) {
            warnings.add(0, "installation record is missing; reinstall the add-on before publishing or testing native conversion");
        }
        return ResponseEntity.ok(new MileageDiagnosticsResponse(
                installationAvailable,
                settings.completeForAddonCreate(),
                settings.completeForNativeConversion(),
                List.copyOf(warnings)));
    }

    private NormalizedClaims adminClaims(HttpServletRequest request) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        authorizationService.requireAdmin(claims);
        return claims;
    }

    private MileageSettingsResponse enrichSettings(String workspaceId, MileageSettingsResponse settings) {
        if (settings.mileageCategoryId() == null || settings.mileageCategoryId().isBlank()) {
            return settings;
        }
        try {
            String name = gateway.listCategories(workspaceId).stream()
                    .filter(category -> settings.mileageCategoryId().equals(category.id()))
                    .map(ClockifyCategoryOption::name)
                    .findFirst()
                    .orElse(settings.mileageCategoryName());
            return settings.withMileageCategoryName(name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return settings;
        } catch (IOException | RuntimeException e) {
            return settings;
        }
    }
}
