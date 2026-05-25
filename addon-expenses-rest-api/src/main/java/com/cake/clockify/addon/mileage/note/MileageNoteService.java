package com.cake.clockify.addon.mileage.note;

import com.cake.clockify.addon.mileage.calculation.MileageCalculation;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.util.UUID;

@Service
public class MileageNoteService {
    public static final String MARKER_PREFIX = "[MileageAddon:converted:v1";
    public static final String DEFAULT_NOTE_TEMPLATE =
            "Mileage reimbursement: {{miles}} {{unit}} x {{rate}} = {{calculatedAmount}}. Created/converted by Mileage for Clockify.";
    public static final String DEFAULT_UNIT = "mile";
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;

    public String marker(UUID conversionId) {
        return MARKER_PREFIX + " id=" + conversionId + "]";
    }

    public boolean hasMileageMarker(String notes) {
        return notes != null && notes.contains(MARKER_PREFIX);
    }

    public String buildConvertedNote(
            String originalNote,
            MileageCalculation calculation,
            String unit,
            UUID conversionId,
            String template) {
        if (hasMileageMarker(originalNote)) {
            return originalNote;
        }
        String generated = render(template == null || template.isBlank() ? DEFAULT_NOTE_TEMPLATE : template,
                calculation,
                unitLabel(calculation, unit),
                marker(conversionId));
        return generated;
    }

    private static String render(String template, MileageCalculation calculation, String unit, String marker) {
        return template
                .replace("{{miles}}", calculation.milesText())
                .replace("{{unit}}", unit)
                .replace("{{rate}}", calculation.rateText())
                .replace("{{calculatedAmount}}", calculation.calculatedAmountText())
                .replace("{{roundedAmount}}", calculation.roundedAmountText())
                .replace("{{amount}}", calculation.roundedAmountText())
                .replace("{{marker}}", marker);
    }

    private static String unitLabel(MileageCalculation calculation, String unit) {
        String base = unit == null || unit.isBlank() ? DEFAULT_UNIT : unit.strip();
        if ("mi".equalsIgnoreCase(base)) {
            base = DEFAULT_UNIT;
        }
        if (!DEFAULT_UNIT.equals(base)) {
            return base;
        }
        return calculation.miles().compareTo(java.math.BigDecimal.ONE) == 0 ? DEFAULT_UNIT : DEFAULT_UNIT + "s";
    }
}
