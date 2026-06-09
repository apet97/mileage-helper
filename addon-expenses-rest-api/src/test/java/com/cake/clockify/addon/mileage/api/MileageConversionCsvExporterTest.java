package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.mileage.api.MileageConversionCsvExporter.CsvRows;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MileageConversionCsvExporterTest {
    private final MileageConversionCsvExporter exporter = new MileageConversionCsvExporter(
            new MileageConversionQueryService(mock(MileageConversionRepository.class)));

    @Test
    void csvIncludesHeaderEscapesCellsAndNeutralizesSpreadsheetFormulas() {
        MileageConversion conversion = conversion();
        conversion.setExpenseId("=HYPERLINK(\"https://evil.example\",\"x\")");
        conversion.setProjectId("project, \"north\"");
        conversion.setNoteMarker("[MileageAddon:converted:v1 id=quoted \"marker\"]");

        String csv = exporter.csv(
                List.of(conversion),
                Map.of("user-1", "Ada Lovelace"),
                Map.of("project, \"north\"", "North, \"Route\""));

        assertThat(csv).startsWith(MileageConversionCsvExporter.CSV_HEADER + "\n");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"https://evil.example\"\",\"\"x\"\")\"");
        assertThat(csv).contains("\"project, \"\"north\"\"\",\"North, \"\"Route\"\"\"");
        assertThat(csv).contains("\"[MileageAddon:converted:v1 id=quoted \"\"marker\"\"]\"");
    }

    @Test
    void responseExposesTruncationHeader() {
        var response = exporter.response("mileage.csv", new CsvRows(List.of(conversion()), true), Map.of(), Map.of());

        assertThat(response.getHeaders().getFirst(MileageConversionCsvExporter.HEADER_EXPORT_TRUNCATED)).isEqualTo("true");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("mileage.csv");
    }

    @Test
    void collectCapsRowsAndMarksTruncated() {
        MileageConversionCsvExporter cappedExporter = new MileageConversionCsvExporter(
                new MileageConversionQueryService(mock(MileageConversionRepository.class)), 5, 1);

        CsvRows rows = cappedExporter.collect(pageRequest -> new PageImpl<>(
                List.of(conversion(), conversion()),
                pageRequest,
                2));

        assertThat(rows.rows()).hasSize(1);
        assertThat(rows.truncated()).isTrue();
    }

    private static MileageConversion conversion() {
        MileageConversion conversion = new MileageConversion();
        conversion.setExpenseId("exp-1");
        conversion.setSource(MileageConversionSource.WEBHOOK_CREATED);
        conversion.setStatus(MileageConversionStatus.CONVERTED);
        conversion.setUserId("user-1");
        conversion.setProjectId("project-1");
        conversion.setMiles(new BigDecimal("37.4000"));
        conversion.setRate(new BigDecimal("0.6550"));
        conversion.setCalculatedAmount(new BigDecimal("24.4970"));
        conversion.setRoundedAmount(new BigDecimal("24.50"));
        conversion.setRoundingMode("HALF_UP");
        conversion.setExpenseDate(LocalDate.parse("2026-05-24"));
        conversion.setUpdatedAt(Instant.parse("2026-05-24T10:01:00Z"));
        conversion.setConvertedAt(Instant.parse("2026-05-24T10:02:00Z"));
        conversion.setNoteMarker("[MileageAddon:converted:v1 id=1]");
        return conversion;
    }
}
