package com.cake.clockify.addon.mileage.note;

import com.cake.clockify.addon.mileage.calculation.MileageCalculation;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.util.UUID;

@Service
public class MileageNoteService {
    public static final String MARKER_PREFIX = "[MileageAddon:converted:v1";
    public static final String DEFAULT_NOTE_TEMPLATE =
            "Mileage reimbursement: {{miles}} {{unit}} x {{rate}} = {{amount}}. Created/converted by Mileage for Clockify. {{marker}}";
    public static final String DEFAULT_UNIT = "mi";
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
            boolean preserveOriginalNotes,
            String template) {
        if (hasMileageMarker(originalNote)) {
            return originalNote;
        }
        String generated = render(template == null || template.isBlank() ? DEFAULT_NOTE_TEMPLATE : template,
                calculation,
                unit == null || unit.isBlank() ? DEFAULT_UNIT : unit,
                marker(conversionId));
        String original = originalNote == null ? "" : originalNote.strip();
        if (preserveOriginalNotes && !original.isBlank()) {
            return original + "\n\n" + generated;
        }
        return generated;
    }

    private static String render(String template, MileageCalculation calculation, String unit, String marker) {
        return template
                .replace("{{miles}}", calculation.milesText())
                .replace("{{unit}}", unit)
                .replace("{{rate}}", calculation.rateText())
                .replace("{{amount}}", calculation.roundedAmountText())
                .replace("{{marker}}", marker);
    }
}
