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
    void rendersExactCleanNoteWithoutVisibleMarkerOrExpenseAmountLine() {
        String note = service.buildConvertedNote("", calculation, "mi", conversionId, null);

        assertThat(note).isEqualTo("Mileage reimbursement: 37.4 miles x 0.655 = 24.497. Created/converted by Mileage for Clockify.");
        assertThat(note).doesNotContain("Expense amount");
        assertThat(note).doesNotContain("[MileageAddon");
    }

    @Test
    void supportsCalculatedAndRoundedTemplateTokensWhileKeepingLegacyAmountRounded() {
        String note = service.buildConvertedNote("", calculation, "mi", conversionId,
                "{{calculatedAmount}} / {{roundedAmount}} / {{amount}} / {{marker}}");

        assertThat(note).contains("24.497 / 24.50 / 24.50 / " + service.marker(conversionId));
    }

    @Test
    void usesSingularMileForOneMileAndExactCalculatedAmount() {
        MileageCalculation oneMile = new MileageCalculation(
                new BigDecimal("1"),
                new BigDecimal("0.725"),
                new BigDecimal("0.725"),
                new BigDecimal("0.73"),
                RoundingMode.HALF_UP);

        String note = service.buildConvertedNote("Native note that should be replaced", oneMile, "mile", conversionId, null);

        assertThat(note).isEqualTo("Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.");
    }

    @Test
    void replacesOriginalNoteWithGeneratedMileageNote() {
        String note = service.buildConvertedNote("Client site visit", calculation, "mi", conversionId, null);

        assertThat(note).isEqualTo("Mileage reimbursement: 37.4 miles x 0.655 = 24.497. Created/converted by Mileage for Clockify.");
    }

    @Test
    void generatedNoteDoesNotStartWithOriginalNote() {
        String note = service.buildConvertedNote("Client site visit", calculation, "mi", conversionId, null);

        assertThat(note).doesNotStartWith("Client site visit");
        assertThat(note).startsWith("Mileage reimbursement");
    }

    @Test
    void doesNotDuplicateMarker() {
        String original = "Already done " + service.marker(conversionId);

        assertThat(service.buildConvertedNote(original, calculation, "mi", conversionId, null)).isEqualTo(original);
    }

    @Test
    void detectsExistingMarker() {
        assertThat(service.hasMileageMarker("text [MileageAddon:converted:v1 id=abc]")).isTrue();
        assertThat(service.hasMileageMarker("ordinary note")).isFalse();
    }
}
