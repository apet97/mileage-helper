package com.cake.clockify.addon.mileage.note;

import com.cake.clockify.addon.mileage.calculation.MileageCalculation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MileageNoteServiceTest {
    private final MileageNoteService service = new MileageNoteService();
    private final UUID conversionId = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private final MileageCalculation calculation = new MileageCalculation(
            new BigDecimal("37.4"),
            new BigDecimal("0.655"),
            new BigDecimal("24.4970"),
            new BigDecimal("24.50"),
            RoundingMode.HALF_UP);

    @Test
    void buildsMarkerFromConversionId() {
        assertThat(service.marker(conversionId))
                .isEqualTo("[MileageAddon:converted:v1 id=00000000-0000-0000-0000-000000000123]");
    }

    @Test
    void appendsHumanFormulaAndMarkerToBlankNote() {
        String note = service.buildConvertedNote("", calculation, "mi", conversionId, true, null);

        assertThat(note).contains("Mileage reimbursement: 37.4 mi x 0.655 = 24.4970.");
        assertThat(note).contains("Expense amount: 24.50.");
        assertThat(note).contains(service.marker(conversionId));
    }

    @Test
    void supportsCalculatedAndRoundedTemplateTokensWhileKeepingLegacyAmountRounded() {
        String note = service.buildConvertedNote("", calculation, "mi", conversionId, true,
                "{{calculatedAmount}} / {{roundedAmount}} / {{amount}} / {{marker}}");

        assertThat(note).contains("24.4970 / 24.50 / 24.50 / " + service.marker(conversionId));
    }

    @Test
    void preservesOriginalNoteWhenConfigured() {
        String note = service.buildConvertedNote("Client site visit", calculation, "mi", conversionId, true, null);

        assertThat(note).startsWith("Client site visit\n\nMileage reimbursement");
    }

    @Test
    void replacesOriginalNoteWhenPreserveFalse() {
        String note = service.buildConvertedNote("Client site visit", calculation, "mi", conversionId, false, null);

        assertThat(note).doesNotStartWith("Client site visit");
        assertThat(note).startsWith("Mileage reimbursement");
    }

    @Test
    void doesNotDuplicateMarker() {
        String original = "Already done " + service.marker(conversionId);

        assertThat(service.buildConvertedNote(original, calculation, "mi", conversionId, true, null)).isEqualTo(original);
    }

    @Test
    void detectsExistingMarker() {
        assertThat(service.hasMileageMarker("text [MileageAddon:converted:v1 id=abc]")).isTrue();
        assertThat(service.hasMileageMarker("ordinary note")).isFalse();
    }
}
