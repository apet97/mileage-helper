package com.cake.clockify.addon.mileage.conversion;

import com.cake.clockify.addon.mileage.audit.MileageSkipReason;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseSnapshot;
import com.cake.clockify.addon.mileage.note.MileageNoteService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;
import com.cake.clockify.addon.mileage.workflow.MileageWorkflowPreflight;
import com.cake.clockify.addon.mileage.workflow.MileageWorkflowPreflightService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class MileageEligibilityService {
    private final MileageWorkflowPreflightService preflightService;

    public MileageEligibilityService(MileageWorkflowPreflightService preflightService) {
        this.preflightService = preflightService;
    }

    public EligibilityDecision evaluate(
            MileageSettingsValidation settings,
            ClockifyExpenseSnapshot expense,
            boolean successfulConversionExists) {
        MileageWorkflowPreflight preflight = preflightService.evaluate(expense);
        if (!settings.enabled()) {
            return EligibilityDecision.skipped(MileageSkipReason.ADDON_DISABLED, "Mileage is disabled");
        }
        if (!settings.complete()) {
            return EligibilityDecision.skipped(MileageSkipReason.SETTINGS_INCOMPLETE, "Mileage settings are incomplete");
        }
        if (hasText(expense.workspaceId()) && !Objects.equals(expense.workspaceId(), settings.workspaceId())) {
            return EligibilityDecision.skipped(MileageSkipReason.WORKSPACE_MISMATCH, "Fetched expense belongs to another workspace");
        }
        if (!Objects.equals(settings.inputCategoryId(), settings.outputCategoryId())
                && Objects.equals(expense.categoryId(), settings.outputCategoryId())) {
            return EligibilityDecision.skipped(MileageSkipReason.ALREADY_OUTPUT_CATEGORY, "Expense is already in the output category");
        }
        if (expense.notes() != null && expense.notes().contains(MileageNoteService.MARKER_PREFIX)) {
            return EligibilityDecision.skipped(MileageSkipReason.ALREADY_MARKED, "Expense already has a mileage marker");
        }
        if (successfulConversionExists) {
            return EligibilityDecision.skipped(MileageSkipReason.ALREADY_CONVERTED, "Expense was already converted");
        }
        if (!Objects.equals(expense.categoryId(), settings.inputCategoryId())) {
            return EligibilityDecision.skipped(MileageSkipReason.NOT_INPUT_CATEGORY, "Expense is not in the mileage input category");
        }
        if (expense.quantity() == null) {
            return EligibilityDecision.skipped(MileageSkipReason.MISSING_QUANTITY, "Expense quantity is missing");
        }
        if (expense.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            return EligibilityDecision.skipped(MileageSkipReason.INVALID_QUANTITY, "Expense quantity must be greater than zero");
        }
        if (preflight.blocked()) {
            return EligibilityDecision.skipped(MileageSkipReason.FINALIZED_OR_LOCKED,
                    String.join(", ", preflight.blockers()));
        }
        if (settings.dryRunMode()) {
            return EligibilityDecision.dryRunDecision();
        }
        return EligibilityDecision.eligibleDecision(preflight.warnings());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
