package com.cake.clockify.addon.mileage.settings;

import com.cake.clockify.addon.mileage.api.model.MileageSettingsRequest;
import com.cake.clockify.addon.mileage.api.model.MileageSettingsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class MileageSettingsService {
    public static final String DEFAULT_UNIT = "mile";
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;

    private final MileageSettingsRepository repository;

    public MileageSettingsService(MileageSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public MileageSettingsResponse getEffectiveSettings(String workspaceId) {
        MileageWorkspaceSettings settings = getNormalizedSettings(workspaceId);
        MileageSettingsValidation addon = validate(settings, false);
        MileageSettingsValidation nativeValidation = validate(settings, true);
        List<String> diagnostics = new ArrayList<>(nativeValidation.diagnostics());
        String mileageCategoryId = mileageCategoryId(settings);
        return new MileageSettingsResponse(
                settings.isEnabled(),
                decimalText(settings.getRate()),
                settings.getUnit(),
                settings.getInputCategoryId(),
                settings.getOutputCategoryId(),
                settings.getRoundingMode(),
                settings.isConvertOnCreate(),
                settings.isConvertOnUpdate(),
                settings.isPreserveOriginalNotes(),
                settings.isDryRunMode(),
                settings.isAllowUserRateOverride(),
                settings.getNoteTemplate(),
                addon.complete(),
                nativeValidation.complete(),
                diagnostics,
                mileageCategoryId,
                null,
                DEFAULT_UNIT,
                DEFAULT_ROUNDING_MODE.name());
    }

    @Transactional
    public MileageWorkspaceSettings saveSettings(String workspaceId, MileageSettingsRequest request, String updatedByUserId) {
        MileageWorkspaceSettings settings = repository.findById(workspaceId).orElseGet(() -> defaults(workspaceId));
        if (request != null) {
            if (request.enabled() != null) settings.setEnabled(request.enabled());
            if (request.rate() != null) settings.setRate(parsePositiveDecimal("rate", request.rate()));
            String categoryId = firstNonBlank(request.mileageCategoryId(), request.inputCategoryId(), request.outputCategoryId());
            if (categoryId != null) {
                settings.setInputCategoryId(categoryId);
                settings.setOutputCategoryId(categoryId);
            }
            if (request.convertOnCreate() != null) settings.setConvertOnCreate(request.convertOnCreate());
            if (request.convertOnUpdate() != null) settings.setConvertOnUpdate(request.convertOnUpdate());
            if (request.dryRunMode() != null) settings.setDryRunMode(request.dryRunMode());
            if (request.allowUserRateOverride() != null) settings.setAllowUserRateOverride(request.allowUserRateOverride());
            if (request.noteTemplate() != null) settings.setNoteTemplate(blankToNull(request.noteTemplate()));
        }
        normalize(settings);
        settings.setUpdatedByUserId(updatedByUserId);
        return repository.saveAndFlush(settings);
    }

    @Transactional
    public MileageWorkspaceSettings saveMileageCategory(String workspaceId, String categoryId, String updatedByUserId) {
        MileageWorkspaceSettings settings = repository.findById(workspaceId).orElseGet(() -> defaults(workspaceId));
        String cleanedCategoryId = blankToNull(categoryId);
        settings.setInputCategoryId(cleanedCategoryId);
        settings.setOutputCategoryId(cleanedCategoryId);
        normalize(settings);
        settings.setUpdatedByUserId(updatedByUserId);
        return repository.saveAndFlush(settings);
    }

    @Transactional
    public MileageWorkspaceSettings saveMileageCategoryWithRate(
            String workspaceId,
            String categoryId,
            BigDecimal rate,
            String updatedByUserId) {
        MileageWorkspaceSettings settings = repository.findById(workspaceId).orElseGet(() -> defaults(workspaceId));
        String cleanedCategoryId = blankToNull(categoryId);
        settings.setInputCategoryId(cleanedCategoryId);
        settings.setOutputCategoryId(cleanedCategoryId);
        if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
            settings.setRate(rate);
        }
        normalize(settings);
        settings.setUpdatedByUserId(updatedByUserId);
        return repository.saveAndFlush(settings);
    }

    public MileageSettingsValidation validateForAddonCreate(String workspaceId) {
        return validate(normalizedCopy(workspaceId), false);
    }

    public MileageSettingsValidation validateForNativeConversion(String workspaceId) {
        return validate(normalizedCopy(workspaceId), true);
    }

    private static MileageWorkspaceSettings defaults(String workspaceId) {
        MileageWorkspaceSettings settings = new MileageWorkspaceSettings();
        settings.setWorkspaceId(workspaceId);
        settings.setEnabled(true);
        settings.setUnit(DEFAULT_UNIT);
        settings.setRoundingMode(DEFAULT_ROUNDING_MODE.name());
        settings.setConvertOnCreate(true);
        settings.setConvertOnUpdate(true);
        settings.setPreserveOriginalNotes(false);
        settings.setDryRunMode(false);
        settings.setAllowUserRateOverride(false);
        return settings;
    }

    private static MileageSettingsValidation validate(MileageWorkspaceSettings settings, boolean nativeConversion) {
        List<String> diagnostics = new ArrayList<>();
        if (!settings.isEnabled()) {
            diagnostics.add("Mileage is disabled");
        }
        if (settings.getRate() == null) {
            diagnostics.add("rate is required");
        }
        if (blankToNull(settings.getOutputCategoryId()) == null) {
            diagnostics.add("outputCategoryId is required");
        }
        if (nativeConversion && blankToNull(settings.getInputCategoryId()) == null) {
            diagnostics.add("inputCategoryId is required");
        }
        RoundingMode roundingMode = parseRoundingMode(settings.getRoundingMode());
        return new MileageSettingsValidation(
                settings.getWorkspaceId(),
                settings.isEnabled(),
                diagnostics.isEmpty(),
                settings.getRate(),
                settings.getUnit(),
                settings.getInputCategoryId(),
                settings.getOutputCategoryId(),
                roundingMode,
                settings.isConvertOnCreate(),
                settings.isConvertOnUpdate(),
                settings.isPreserveOriginalNotes(),
                settings.isDryRunMode(),
                settings.isAllowUserRateOverride(),
                settings.getNoteTemplate(),
                List.copyOf(diagnostics));
    }

    private static RoundingMode parseRoundingMode(String raw) {
        String value = raw == null || raw.isBlank() ? DEFAULT_ROUNDING_MODE.name() : raw.trim();
        try {
            return RoundingMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return DEFAULT_ROUNDING_MODE;
        }
    }

    private static BigDecimal parsePositiveDecimal(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(field + " must be greater than zero");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a decimal number", e);
        }
    }

    private static String decimalText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private MileageWorkspaceSettings getNormalizedSettings(String workspaceId) {
        return repository.findById(workspaceId)
                .map(settings -> {
                    boolean changed = normalize(settings);
                    return changed ? repository.saveAndFlush(settings) : settings;
                })
                .orElseGet(() -> defaults(workspaceId));
    }

    private MileageWorkspaceSettings normalizedCopy(String workspaceId) {
        MileageWorkspaceSettings settings = repository.findById(workspaceId).orElseGet(() -> defaults(workspaceId));
        normalize(settings);
        return settings;
    }

    private static boolean normalize(MileageWorkspaceSettings settings) {
        boolean changed = false;
        String mileageCategoryId = mileageCategoryId(settings);
        if (!DEFAULT_UNIT.equals(settings.getUnit())) {
            settings.setUnit(DEFAULT_UNIT);
            changed = true;
        }
        if (!DEFAULT_ROUNDING_MODE.name().equals(settings.getRoundingMode())) {
            settings.setRoundingMode(DEFAULT_ROUNDING_MODE.name());
            changed = true;
        }
        if (settings.isPreserveOriginalNotes()) {
            settings.setPreserveOriginalNotes(false);
            changed = true;
        }
        if (mileageCategoryId != null) {
            if (!mileageCategoryId.equals(settings.getInputCategoryId())) {
                settings.setInputCategoryId(mileageCategoryId);
                changed = true;
            }
            if (!mileageCategoryId.equals(settings.getOutputCategoryId())) {
                settings.setOutputCategoryId(mileageCategoryId);
                changed = true;
            }
        }
        return changed;
    }

    private static String mileageCategoryId(MileageWorkspaceSettings settings) {
        return firstNonBlank(settings.getInputCategoryId(), settings.getOutputCategoryId());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = blankToNull(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }
}
