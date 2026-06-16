package com.cake.clockify.addon.mileage.workflow;

import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseSnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MileageWorkflowPreflightService {
    public MileageWorkflowPreflight evaluate(ClockifyExpenseSnapshot expense) {
        boolean locked = Boolean.TRUE.equals(expense.locked());
        boolean finalized = Boolean.TRUE.equals(expense.finalized());
        boolean invoiced = Boolean.TRUE.equals(expense.invoiced());
        boolean billable = Boolean.TRUE.equals(expense.billable());
        String approvalStatus = normalize(expense.approvalStatus());
        boolean submitted = approvalStatus.equals("SUBMITTED")
                || approvalStatus.equals("SUBMITTED_FOR_APPROVAL")
                || approvalStatus.equals("PENDING");
        boolean approved = approvalStatus.equals("APPROVED");
        boolean rejected = approvalStatus.equals("REJECTED");

        List<String> blockers = new ArrayList<>();
        if (locked) {
            blockers.add("Expense is locked");
        }
        if (finalized) {
            blockers.add("Expense is finalized");
        }

        List<String> warnings = new ArrayList<>();
        if (submitted) {
            warnings.add("Expense is submitted for approval");
        } else if (approved) {
            warnings.add("Expense is approved");
        } else if (rejected) {
            warnings.add("Expense is rejected");
        } else if (!approvalStatus.isBlank()) {
            warnings.add("Workflow state is unknown");
        }
        if (invoiced) {
            warnings.add("Expense is invoiced");
        }

        return new MileageWorkflowPreflight(
                locked,
                finalized,
                submitted,
                approved,
                rejected,
                invoiced,
                billable,
                List.copyOf(warnings),
                List.copyOf(blockers));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
