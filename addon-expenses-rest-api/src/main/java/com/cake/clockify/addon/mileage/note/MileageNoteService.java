package com.cake.clockify.addon.mileage.note;

import com.cake.clockify.addon.mileage.calculation.MileageCalculation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class MileageNoteService {
    public static final String MARKER_PREFIX = "[MileageAddon:converted:v1";
    public static final String CONVERTED_SIGNATURE = "Created/converted by Mileage for Clockify.";
    public static final String DEFAULT_NOTE_TEMPLATE =
            "Mileage reimbursement: {{miles}} {{unit}} x {{rate}} = {{calculatedAmount}}{{categoryCharge}}. Created/converted by Mileage for Clockify.";
    public static final String DEFAULT_UNIT = "mile";
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;

    public String marker(UUID conversionId) {
        return MARKER_PREFIX + " id=" + conversionId + "]";
    }

    /**
     * Inserts the "(Clockify category charge: X)" parenthetical into an already-written converted note,
     * used by the deferred add-on-create reconcile sweeper. Returns the note UNCHANGED when:
     *   - categoryCharge is null, or equals roundedAmount (no divergence to explain), or
     *   - the note already contains the parenthetical (idempotent), or
     *   - the note does not end with the standard signature line (custom template without the signature;
     *     we cannot safely find an insertion point, so we leave it alone).
     * The parenthetical is inserted directly before ". Created/converted by Mileage for Clockify.", matching
     * the native-conversion format ("... = 8.99 (Clockify category charge: 9.05). Created/converted by ...").
     */
    public String insertCategoryCharge(String note, BigDecimal categoryCharge, BigDecimal roundedAmount) {
        if (note == null || categoryCharge == null) {
            return note;
        }
        if (roundedAmount != null && categoryCharge.compareTo(roundedAmount) == 0) {
            return note;
        }
        if (note.contains("(Clockify category charge:")) {
            return note;
        }
        String anchor = ". " + CONVERTED_SIGNATURE;
        int idx = note.lastIndexOf(anchor);
        if (idx < 0) {
            return note;
        }
        String token = " (Clockify category charge: "
                + categoryCharge.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")";
        return note.substring(0, idx) + token + note.substring(idx);
    }

    public boolean hasMileageMarker(String notes) {
        return notes != null && notes.contains(MARKER_PREFIX);
    }

    public String buildConvertedNote(
            String originalNote,
            MileageCalculation calculation,
            String unit,
            UUID conversionId,
            String template,
            BigDecimal categoryCharge) {
        if (isAlreadyConverted(originalNote)) {
            return originalNote;
        }
        String generated = render(template == null || template.isBlank() ? DEFAULT_NOTE_TEMPLATE : template,
                calculation,
                unitLabel(calculation, unit),
                marker(conversionId),
                categoryChargeToken(calculation, categoryCharge));
        generated = ensureLoopMarker(generated, conversionId);
        if (originalNote == null || originalNote.isBlank()) {
            return generated;
        }
        return originalNote.strip() + "\n\n" + generated;
    }

    private String ensureLoopMarker(String note, UUID conversionId) {
        if (note.contains(MARKER_PREFIX) || note.contains(CONVERTED_SIGNATURE)) {
            return note;
        }
        return note + " " + marker(conversionId);
    }

    private boolean isAlreadyConverted(String notes) {
        return notes != null && (notes.contains(MARKER_PREFIX) || notes.contains(CONVERTED_SIGNATURE));
    }

    private static String categoryChargeToken(MileageCalculation calculation, BigDecimal categoryCharge) {
        if (categoryCharge == null || categoryCharge.compareTo(calculation.roundedAmount()) == 0) {
            return "";
        }
        return " (Clockify category charge: " + categoryCharge.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")";
    }

    private static String render(String template, MileageCalculation calculation, String unit, String marker, String categoryCharge) {
        return template
                .replace("{{miles}}", calculation.milesText())
                .replace("{{unit}}", unit)
                .replace("{{rate}}", calculation.rateText())
                .replace("{{calculatedAmount}}", calculation.calculatedAmountText())
                .replace("{{roundedAmount}}", calculation.roundedAmountText())
                .replace("{{amount}}", calculation.roundedAmountText())
                .replace("{{categoryCharge}}", categoryCharge)
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
