package com.cake.clockify.addon.mileage.conversion;

import com.cake.clockify.addon.mileage.audit.MileageSkipReason;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseSnapshot;
import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MileageEligibilityServiceTest {
    private final MileageEligibilityService service = new MileageEligibilityService();

    @Test
    void disabledSettingsAreSkipped() {
        assertThat(service.evaluate(settings(false, true, false), expense("cat-input", "37.4", false, ""), false).skipReason())
                .isEqualTo(MileageSkipReason.ADDON_DISABLED);
    }

    @Test
    void missingOutputCategoryIsSkipped() {
        MileageSettingsValidation settings = new MileageSettingsValidation("ws-1", true, false, rate(), "mi",
                "cat-input", null, RoundingMode.HALF_UP, true, true, true, false, false, null, List.of("outputCategoryId is required"));
        assertThat(service.evaluate(settings, expense("cat-input", "37.4", false, ""), false).skipReason())
                .isEqualTo(MileageSkipReason.SETTINGS_INCOMPLETE);
    }

    @Test
    void nonInputCategoryIsSkipped() {
        assertThat(service.evaluate(settings(true, true, false), expense("other", "37.4", false, ""), false).skipReason())
                .isEqualTo(MileageSkipReason.NOT_INPUT_CATEGORY);
    }

    @Test
    void outputCategoryIsSkippedAsAlreadyOutputCategory() {
        assertThat(service.evaluate(settings(true, true, false), expense("cat-output", "37.4", false, ""), false).skipReason())
                .isEqualTo(MileageSkipReason.ALREADY_OUTPUT_CATEGORY);
    }

    @Test
    void markerIsSkippedAsAlreadyMarked() {
        assertThat(service.evaluate(settings(true, true, false), expense("cat-input", "37.4", false, "[MileageAddon:converted:v1 id=x]"), false).skipReason())
                .isEqualTo(MileageSkipReason.ALREADY_MARKED);
    }

    @Test
    void existingConvertedRowIsSkipped() {
        assertThat(service.evaluate(settings(true, true, false), expense("cat-input", "37.4", false, ""), true).skipReason())
                .isEqualTo(MileageSkipReason.ALREADY_CONVERTED);
    }

    @Test
    void missingQuantityIsSkipped() {
        assertThat(service.evaluate(settings(true, true, false), expense("cat-input", null, false, ""), false).skipReason())
                .isEqualTo(MileageSkipReason.MISSING_QUANTITY);
    }

    @Test
    void negativeQuantityIsSkipped() {
        assertThat(service.evaluate(settings(true, true, false), expense("cat-input", "-1", false, ""), false).skipReason())
                .isEqualTo(MileageSkipReason.INVALID_QUANTITY);
    }

    @Test
    void lockedExpenseIsSkipped() {
        assertThat(service.evaluate(settings(true, true, false), expense("cat-input", "37.4", true, ""), false).skipReason())
                .isEqualTo(MileageSkipReason.FINALIZED_OR_LOCKED);
    }

    @Test
    void dryRunReturnsDryRunDecision() {
        EligibilityDecision decision = service.evaluate(settings(true, true, true), expense("cat-input", "37.4", false, ""), false);
        assertThat(decision.dryRun()).isTrue();
        assertThat(decision.skipReason()).isEqualTo(MileageSkipReason.DRY_RUN);
    }

    @Test
    void eligibleInputExpenseReturnsEligible() {
        EligibilityDecision decision = service.evaluate(settings(true, true, false), expense("cat-input", "37.4", false, ""), false);
        assertThat(decision.eligible()).isTrue();
        assertThat(decision.skipReason()).isNull();
    }

    private static MileageSettingsValidation settings(boolean enabled, boolean complete, boolean dryRun) {
        return new MileageSettingsValidation("ws-1", enabled, complete, rate(), "mi",
                "cat-input", "cat-output", RoundingMode.HALF_UP, true, true, true, dryRun, false, null, List.of());
    }

    private static ClockifyExpenseSnapshot expense(String categoryId, String quantity, boolean locked, String notes) {
        return new ClockifyExpenseSnapshot("exp-1", "ws-1", "user-1", "2026-05-24T12:00:00Z",
                "project-1", "task-1", categoryId, notes,
                quantity == null ? null : new BigDecimal(quantity), true, "file-1", new BigDecimal("2450"), locked);
    }

    private static BigDecimal rate() {
        return new BigDecimal("0.655");
    }
}
