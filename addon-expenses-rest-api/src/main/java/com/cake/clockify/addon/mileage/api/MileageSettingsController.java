package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.mileage.api.model.MileageCategoryOptionsResponse;
import com.cake.clockify.addon.mileage.api.model.MileageDiagnosticsResponse;
import com.cake.clockify.addon.mileage.api.model.MileageSettingsRequest;
import com.cake.clockify.addon.mileage.api.model.MileageSettingsResponse;
import com.cake.clockify.addon.mileage.api.model.MileageUserOptionsResponse;
import com.cake.clockify.addon.mileage.clockify.ClockifyCategoryOption;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import com.cake.clockify.client.ClockifyApiException;
import com.cake.clockify.client.ClockifyTransportException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(MileageSettingsController.class);
    private static final String CATEGORY_LOOKUP_UNAVAILABLE =
            "Clockify did not allow reading expense categories. Try Use or Repair Mileage Category, or verify expense permissions for this workspace.";
    private static final String USERS_UNAVAILABLE =
            "Clockify users are temporarily unavailable. Try again in a moment.";

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
        // Keep the Clockify Mileage category's unit price in step with the saved rate so add-on-created and
        // native-converted expenses are charged the intended amount (a unit category forces total = miles ×
        // price). Best-effort: a Clockify outage must not fail the save.
        syncMileageCategoryPrice(claims.workspaceId());
        return ResponseEntity.ok(enrichSettings(claims.workspaceId(), settingsService.getEffectiveSettings(claims.workspaceId())));
    }

    private void syncMileageCategoryPrice(String workspaceId) {
        MileageSettingsResponse settings = settingsService.getEffectiveSettings(workspaceId);
        if (!hasText(settings.rate())
                || settings.mileageCategoryId() == null || settings.mileageCategoryId().isBlank()) {
            return;
        }
        try {
            gateway.createOrRepairMileageCategory(workspaceId, new BigDecimal(settings.rate()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Mileage category price sync interrupted for workspace {}", workspaceId);
        } catch (IOException | ClockifyTransportException | ClockifyApiException e) {
            // Expected Clockify outage/permission failure — best-effort sync, never fail the committed save.
            log.warn("Mileage category price sync skipped for workspace {} after Clockify failure: {}",
                    workspaceId, e.toString());
        } catch (RuntimeException e) {
            // Unexpected: a real bug, not a Clockify hiccup. Log loudly (with stack) but still do not fail the
            // already-committed save — and do not let it masquerade as a routine outage in the logs.
            log.error("Mileage category price sync hit an unexpected error for workspace {}", workspaceId, e);
        }
    }

    @PostMapping("/api/mileage/settings/mileage-category")
    public ResponseEntity<MileageSettingsResponse> createOrRepairMileageCategory(HttpServletRequest request)
            throws IOException, InterruptedException {
        NormalizedClaims claims = adminClaims(request);
        MileageSettingsResponse settings = settingsService.getEffectiveSettings(claims.workspaceId());
        ClockifyCategoryOption category;
        if (hasText(settings.rate())) {
            category = gateway.createOrRepairMileageCategory(
                    claims.workspaceId(),
                    new BigDecimal(settings.rate()));
            settingsService.saveMileageCategory(claims.workspaceId(), category.id(), claims.userId());
        } else {
            category = gateway.findMileageCategory(claims.workspaceId())
                    .filter(MileageSettingsController::isUsableMileageUnitCategory)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Mileage rate is required before creating a category"));
            settingsService.saveMileageCategoryWithRate(
                    claims.workspaceId(),
                    category.id(),
                    rateFromPriceInCents(category.unitPrice()),
                    claims.userId());
        }
        return ResponseEntity.ok(enrichSettings(claims.workspaceId(), settingsService.getEffectiveSettings(claims.workspaceId()))
                .withMileageCategoryName(category.name()));
    }

    @GetMapping("/api/mileage/options/categories")
    public ResponseEntity<MileageCategoryOptionsResponse> categories(HttpServletRequest request)
            throws IOException, InterruptedException {
        NormalizedClaims claims = adminClaims(request);
        try {
            return ResponseEntity.ok(MileageCategoryOptionsResponse.from(gateway.listCategories(claims.workspaceId())));
        } catch (ClockifyApiException e) {
            if (!isAuthzFailure(e)) {
                throw e;
            }
            log.warn("Clockify category lookup denied for workspace {} with status {}",
                    claims.workspaceId(), e.statusCode());
            return ResponseEntity.ok(MileageCategoryOptionsResponse.unavailable(CATEGORY_LOOKUP_UNAVAILABLE));
        }
    }

    @GetMapping("/api/mileage/options/users")
    public ResponseEntity<MileageUserOptionsResponse> users(HttpServletRequest request) {
        NormalizedClaims claims = adminClaims(request);
        try {
            return ResponseEntity.ok(MileageUserOptionsResponse.from(gateway.listUsers(claims.workspaceId())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return usersUnavailable(claims.workspaceId(), e);
        } catch (IOException | ClockifyTransportException | ClockifyApiException e) {
            // A Clockify cold-start timeout surfaces as a ClockifyTransportException (a RuntimeException
            // wrapping HttpTimeoutException); permission/transport errors are ClockifyApiException/IOException.
            // Degrade to empty list + warning so the Team/Conversions user filter stays usable rather than
            // 500-ing. Any OTHER RuntimeException is a real bug and propagates (500) rather than being mislabeled
            // a transient outage.
            return usersUnavailable(claims.workspaceId(), e);
        }
    }

    private static ResponseEntity<MileageUserOptionsResponse> usersUnavailable(String workspaceId, Exception e) {
        log.warn("Clockify user lookup failed for workspace {}: {}", workspaceId, e.toString());
        return ResponseEntity.ok(MileageUserOptionsResponse.unavailable(USERS_UNAVAILABLE));
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

    private static boolean isAuthzFailure(ClockifyApiException e) {
        return e.statusCode() == 401 || e.statusCode() == 403;
    }

    private static boolean isUsableMileageUnitCategory(ClockifyCategoryOption category) {
        return category.id() != null
                && "UNIT".equalsIgnoreCase(category.type())
                && MileageSettingsService.DEFAULT_UNIT.equalsIgnoreCase(category.unit())
                && category.unitPrice() != null
                && category.unitPrice().compareTo(BigDecimal.ZERO) > 0;
    }

    private static BigDecimal rateFromPriceInCents(BigDecimal unitPrice) {
        return unitPrice.movePointLeft(2).stripTrailingZeros();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
