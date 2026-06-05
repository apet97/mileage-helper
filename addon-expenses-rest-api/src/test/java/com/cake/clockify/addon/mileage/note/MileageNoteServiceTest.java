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
        String note = service.buildConvertedNote("", calculation, "mi", conversionId, null, null);

        assertThat(note).isEqualTo("Mileage reimbursement: 37.4 miles x 0.655 = 24.497. Created/converted by Mileage for Clockify.");
        assertThat(note).doesNotContain("Expense amount");
        assertThat(note).doesNotContain("[MileageAddon");
    }

    @Test
    void supportsCalculatedAndRoundedTemplateTokensWhileKeepingLegacyAmountRounded() {
        String note = service.buildConvertedNote("", calculation, "mi", conversionId,
                "{{calculatedAmount}} / {{roundedAmount}} / {{amount}} / {{marker}}", null);

        assertThat(note).contains("24.497 / 24.50 / 24.50 / " + service.marker(conversionId));
    }

    @Test
    void appendsHiddenMarkerWhenCustomTemplateOmitsSignatureAndMarker() {
        String note = service.buildConvertedNote("", calculation, "mi", conversionId,
                "Trip: {{miles}} {{unit}} @ {{rate}}", null);

        assertThat(note).startsWith("Trip: 37.4 miles @ 0.655");
        assertThat(note).contains(service.marker(conversionId));
        assertThat(note).endsWith(service.marker(conversionId));
    }

    @Test
    void doesNotAppendMarkerWhenDefaultTemplateAlreadyCarriesSignature() {
        String note = service.buildConvertedNote("", calculation, "mi", conversionId, null, null);

        assertThat(note).doesNotContain("[MileageAddon");
    }

    @Test
    void usesSingularMileForOneMileAndExactCalculatedAmount() {
        MileageCalculation oneMile = new MileageCalculation(
                new BigDecimal("1"),
                new BigDecimal("0.725"),
                new BigDecimal("0.725"),
                new BigDecimal("0.73"),
                RoundingMode.HALF_UP);

        String note = service.buildConvertedNote("", oneMile, "mile", conversionId, null, null);

        assertThat(note).isEqualTo("Mileage reimbursement: 1 mile x 0.725 = 0.725. Created/converted by Mileage for Clockify.");
    }

    @Test
    void appendsClockifyCategoryChargeWhenItDiffersFromTheCalculatedAmount() {
        MileageCalculation subCent = new MileageCalculation(
                new BigDecimal("12.4"),
                new BigDecimal("7.25123"),
                new BigDecimal("89.915252"),
                new BigDecimal("89.92"),
                RoundingMode.HALF_UP);

        String note = service.buildConvertedNote("", subCent, "mile", conversionId, null, new BigDecimal("89.90"));

        assertThat(note).isEqualTo(
                "Mileage reimbursement: 12.4 miles x 7.25123 = 89.915252 (Clockify category charge: 89.90). Created/converted by Mileage for Clockify.");
    }

    @Test
    void omitsClockifyCategoryChargeWhenItMatchesTheRoundedAmount() {
        String note = service.buildConvertedNote("", calculation, "mi", conversionId, null, new BigDecimal("24.50"));

        assertThat(note).isEqualTo("Mileage reimbursement: 37.4 miles x 0.655 = 24.497. Created/converted by Mileage for Clockify.");
        assertThat(note).doesNotContain("category charge");
    }

    @Test
    void omitsClockifyCategoryChargeWhenChargeIsUnknown() {
        String note = service.buildConvertedNote("", calculation, "mi", conversionId, null, null);

        assertThat(note).doesNotContain("category charge");
    }

    @Test
    void preservesOriginalNotePrependedAboveTheGeneratedNote() {
        String note = service.buildConvertedNote("Client site visit", calculation, "mi", conversionId, null, null);

        assertThat(note).isEqualTo(
                "Client site visit\n\nMileage reimbursement: 37.4 miles x 0.655 = 24.497. Created/converted by Mileage for Clockify.");
    }

    @Test
    void rendersCanonicalNoteOnlyWhenThereIsNoOriginalNote() {
        String note = service.buildConvertedNote("   ", calculation, "mi", conversionId, null, null);

        assertThat(note).isEqualTo("Mileage reimbursement: 37.4 miles x 0.655 = 24.497. Created/converted by Mileage for Clockify.");
    }

    @Test
    void doesNotReStackWhenOriginalNoteIsAlreadyConverted() {
        String alreadyConverted = "Client site visit\n\nMileage reimbursement: 37.4 miles x 0.655 = 24.497. Created/converted by Mileage for Clockify.";

        String note = service.buildConvertedNote(alreadyConverted, calculation, "mi", conversionId, null, null);

        assertThat(note).isEqualTo(alreadyConverted);
    }

    @Test
    void doesNotDuplicateMarker() {
        String original = "Already done " + service.marker(conversionId);

        assertThat(service.buildConvertedNote(original, calculation, "mi", conversionId, null, null)).isEqualTo(original);
    }

    @Test
    void detectsExistingMarker() {
        assertThat(service.hasMileageMarker("text [MileageAddon:converted:v1 id=abc]")).isTrue();
        assertThat(service.hasMileageMarker("ordinary note")).isFalse();
    }
}
