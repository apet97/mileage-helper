package com.cake.clockify.addon.mileage.lifecycle;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.lifecycle.AddonLifecycleHandler;
import com.cake.clockify.addon.mileage.api.model.MileageSettingsRequest;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.settings.MileageSettingsRepository;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class MileageLifecycleHandler implements AddonLifecycleHandler {
    private static final Logger log = LoggerFactory.getLogger(MileageLifecycleHandler.class);

    private final MileageSettingsRepository settingsRepository;
    private final MileageConversionRepository conversionRepository;
    private final MileageSettingsService settingsService;

    public MileageLifecycleHandler(
            MileageSettingsRepository settingsRepository,
            MileageConversionRepository conversionRepository,
            MileageSettingsService settingsService) {
        this.settingsRepository = settingsRepository;
        this.conversionRepository = conversionRepository;
        this.settingsService = settingsService;
    }

    @Override
    public void onInstalled(NormalizedClaims claims, Map<String, Object> payload) {
        log.info("mileage-for-clockify.installed workspace={}", claims.workspaceId());
    }

    @Override
    @Transactional
    public void onDeleted(NormalizedClaims claims) {
        log.info("mileage-for-clockify.deleted workspace={}", claims.workspaceId());
        settingsRepository.deleteById(claims.workspaceId());
        conversionRepository.deleteByWorkspaceId(claims.workspaceId());
    }

    @Override
    @Transactional
    public void onSettingsUpdated(NormalizedClaims claims, List<Map<String, Object>> updates) {
        MileageSettingsRequest request = requestFrom(updates);
        if (request != null) {
            settingsService.saveSettings(claims.workspaceId(), request, claims.userId());
        }
    }

    @Override
    @Transactional
    public void onStatusChanged(NormalizedClaims claims, String status) {
        Boolean enabled = enabledForStatus(status);
        if (enabled == null) {
            return;
        }
        settingsRepository.findById(claims.workspaceId()).ifPresent(settings -> {
            settings.setEnabled(enabled);
            settingsRepository.saveAndFlush(settings);
        });
    }

    private static MileageSettingsRequest requestFrom(List<Map<String, Object>> updates) {
        if (updates == null || updates.isEmpty()) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map<String, Object> update : updates) {
            Object id = update.get("id");
            Object value = update.get("value");
            String field = canonicalField(id == null ? null : id.toString());
            if (field != null && value != null) {
                values.put(field, value.toString());
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        return new MileageSettingsRequest(
                bool(values.get("enabled")),
                values.get("rate"),
                values.get("unit"),
                values.get("inputCategoryId"),
                values.get("outputCategoryId"),
                values.get("mileageCategoryId"),
                values.get("roundingMode"),
                bool(values.get("convertOnCreate")),
                bool(values.get("convertOnUpdate")),
                bool(values.get("preserveOriginalNotes")),
                bool(values.get("dryRunMode")),
                bool(values.get("allowUserRateOverride")),
                values.get("noteTemplate"));
    }

    private static String canonicalField(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        return switch (compact) {
            case "enabled" -> "enabled";
            case "rate", "mileagerate" -> "rate";
            case "unit", "mileageunit" -> "unit";
            case "inputcategoryid", "mileageinputcategoryid" -> "inputCategoryId";
            case "outputcategoryid", "mileageoutputcategoryid" -> "outputCategoryId";
            case "mileagecategoryid" -> "mileageCategoryId";
            case "roundingmode", "mileageroundingmode" -> "roundingMode";
            case "convertoncreate" -> "convertOnCreate";
            case "convertonupdate" -> "convertOnUpdate";
            case "preserveoriginalnotes" -> "preserveOriginalNotes";
            case "dryrunmode" -> "dryRunMode";
            case "allowuserrateoverride" -> "allowUserRateOverride";
            case "notetemplate" -> "noteTemplate";
            default -> null;
        };
    }

    private static Boolean bool(String value) {
        return value == null ? null : Boolean.valueOf(value);
    }

    private static Boolean enabledForStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE", "ENABLED" -> Boolean.TRUE;
            case "INACTIVE", "SUSPENDED", "DISABLED", "PAUSED" -> Boolean.FALSE;
            default -> null;
        };
    }
}
