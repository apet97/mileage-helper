package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.db.entity.AddonWebhookJob;
import com.cake.clockify.addon.db.repository.AddonWebhookJobRepository;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.mileage.api.model.MileageCategoryOptionsResponse;
import com.cake.clockify.addon.mileage.api.model.MileageChecklistItemResponse;
import com.cake.clockify.addon.mileage.api.model.MileageDiagnosticsResponse;
import com.cake.clockify.addon.mileage.api.model.MileageOperationalHealthResponse;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
public class MileageSettingsController {
    private static final Logger log = LoggerFactory.getLogger(MileageSettingsController.class);
    private static final String CATEGORY_LOOKUP_UNAVAILABLE =
            "Clockify did not allow reading expense categories. Try Use or Repair Mileage Category, or verify expense permissions for this workspace.";
    private static final String USERS_UNAVAILABLE =
            "Clockify users are temporarily unavailable. Try again in a moment.";
    private static final String CATEGORY_SYNC_CLOCKIFY_WARNING =
            "Settings saved, but Clockify category price could not be synced. Try Use or Repair Mileage Category again when Clockify is available.";
    private static final String CATEGORY_SYNC_INTERNAL_WARNING =
            "Settings saved, but category price sync hit an internal error. Check server logs before relying on Clockify category charges.";

    private final MileageSettingsService settingsService;
    private final ClockifyExpenseGateway gateway;
    private final MileageAuthorizationService authorizationService;
    private final AddonInstallationService installationService;
    private final AddonWebhookJobRepository jobRepository;

    public MileageSettingsController(
            MileageSettingsService settingsService,
            ClockifyExpenseGateway gateway,
            MileageAuthorizationService authorizationService,
            AddonInstallationService installationService,
            AddonWebhookJobRepository jobRepository) {
        this.settingsService = settingsService;
        this.gateway = gateway;
        this.authorizationService = authorizationService;
        this.installationService = installationService;
        this.jobRepository = jobRepository;
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
        List<String> warnings = syncMileageCategoryPrice(claims.workspaceId());
        return ResponseEntity.ok(enrichSettings(
                claims.workspaceId(),
                settingsService.getEffectiveSettings(claims.workspaceId())).withWarnings(warnings));
    }

    private List<String> syncMileageCategoryPrice(String workspaceId) {
        MileageSettingsResponse settings = settingsService.getEffectiveSettings(workspaceId);
        if (!hasText(settings.rate())
                || settings.mileageCategoryId() == null || settings.mileageCategoryId().isBlank()) {
            return List.of();
        }
        try {
            gateway.createOrRepairMileageCategory(workspaceId, new BigDecimal(settings.rate()));
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Mileage category price sync interrupted for workspace {}", workspaceId);
            return List.of(CATEGORY_SYNC_CLOCKIFY_WARNING);
        } catch (IOException | ClockifyTransportException | ClockifyApiException e) {
            // Expected Clockify outage/permission failure — best-effort sync, never fail the committed save.
            log.warn("Mileage category price sync skipped for workspace {} after Clockify failure: {}",
                    workspaceId, e.toString());
            return List.of(CATEGORY_SYNC_CLOCKIFY_WARNING);
        } catch (RuntimeException e) {
            // Unexpected: a real bug, not a Clockify hiccup. Log loudly (with stack) but still do not fail the
            // already-committed save — and do not let it masquerade as a routine outage in the logs.
            log.error("Mileage category price sync hit an unexpected error for workspace {}", workspaceId, e);
            return List.of(CATEGORY_SYNC_INTERNAL_WARNING);
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
        MileageOperationalHealthResponse health = operationalHealth();
        if (health.pendingJobs() > 0
                && health.oldestPendingAgeSeconds() != null
                && health.oldestPendingAgeSeconds() > 300) {
            warnings.add("webhook queue has pending jobs older than 5 minutes; check worker liveness");
        }
        if (health.failedJobs() > 0) {
            warnings.add("webhook queue has failed jobs; inspect logs before publishing");
        }
        return ResponseEntity.ok(new MileageDiagnosticsResponse(
                installationAvailable,
                settings.completeForAddonCreate(),
                settings.completeForNativeConversion(),
                List.copyOf(warnings),
                checklist(installationAvailable, settings),
                health));
    }

    private MileageOperationalHealthResponse operationalHealth() {
        long pending = jobRepository.countByStatus(AddonWebhookJob.STATUS_PENDING);
        long claimed = jobRepository.countByStatus(AddonWebhookJob.STATUS_CLAIMED);
        long failed = jobRepository.countByStatus(AddonWebhookJob.STATUS_FAILED);
        Long oldestPendingAgeSeconds = jobRepository.findFirstByStatusOrderByCreatedAtAsc(AddonWebhookJob.STATUS_PENDING)
                .map(job -> Duration.between(job.getCreatedAt(), Instant.now()).getSeconds())
                .orElse(null);
        Instant lastCompleted = jobRepository.findFirstByStatusOrderByCompletedAtDesc(AddonWebhookJob.STATUS_COMPLETED)
                .map(AddonWebhookJob::getCompletedAt)
                .orElse(null);
        return new MileageOperationalHealthResponse(pending, claimed, failed, oldestPendingAgeSeconds, lastCompleted);
    }

    private static List<MileageChecklistItemResponse> checklist(
            boolean installationAvailable,
            MileageSettingsResponse settings) {
        boolean rateSaved = hasText(settings.rate());
        boolean categoryConfigured = hasText(settings.mileageCategoryId());
        return List.of(
                new MileageChecklistItemResponse(
                        "installation",
                        "Add-on installed",
                        installationAvailable,
                        installationAvailable ? "" : "Reinstall the add-on from the current manifest"),
                new MileageChecklistItemResponse(
                        "rate",
                        "Mileage rate saved",
                        rateSaved,
                        rateSaved ? "" : "Set a positive rate in Settings"),
                new MileageChecklistItemResponse(
                        "category",
                        "Mileage category configured",
                        categoryConfigured,
                        categoryConfigured ? "" : "Use or Repair Mileage Category"),
                new MileageChecklistItemResponse(
                        "nativeConversion",
                        "Native conversion ready",
                        settings.completeForNativeConversion(),
                        settings.completeForNativeConversion() ? "" : "Resolve the diagnostics warnings above"));
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
