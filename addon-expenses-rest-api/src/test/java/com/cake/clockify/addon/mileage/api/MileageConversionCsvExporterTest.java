package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        assertThat(csv).contains(",0.655,POLICY,00000000-0000-0000-0000-000000000706,2026 mileage rate,24.497,");
        assertThat(csv).contains("24.497,24.50,,HALF_UP");
        assertThat(csv).contains("HQ,Client site,Install support,1200,1225,Storm detour");
        assertThat(csv).contains("\"[MileageAddon:converted:v1 id=quoted \"\"marker\"\"]\"");
    }

    @Test
    void writesCsvRowsToAppendableWithoutChangingFormat() throws Exception {
        StringBuilder builder = new StringBuilder();

        exporter.writeCsv(
                builder,
                List.of(conversion()),
                Map.of("user-1", "Ada Lovelace"),
                Map.of("project-1", "Northern Route"));

        assertThat(builder.toString()).startsWith(MileageConversionCsvExporter.CSV_HEADER + "\n");
        assertThat(builder.toString()).contains("Ada Lovelace");
        assertThat(builder.toString()).contains("Northern Route");
    }

    @Test
    void responseExposesTruncationHeader() {
        MileageConversionCsvExporter cappedExporter = new MileageConversionCsvExporter(
                new MileageConversionQueryService(mock(MileageConversionRepository.class)), 5, 1);
        var stream = cappedExporter.stream(pageRequest -> new PageImpl<>(
                List.of(conversion(), conversion()),
                pageRequest,
                2));

        var response = cappedExporter.response("mileage.csv", stream, rows -> Map.of(), rows -> Map.of());

        assertThat(response.getHeaders().getFirst(MileageConversionCsvExporter.HEADER_EXPORT_TRUNCATED)).isEqualTo("true");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("mileage.csv");
    }

    @Test
    void streamMarksTruncatedFromFirstPageTotal() {
        MileageConversionCsvExporter cappedExporter = new MileageConversionCsvExporter(
                new MileageConversionQueryService(mock(MileageConversionRepository.class)), 5, 1);

        var stream = cappedExporter.stream(pageRequest -> new PageImpl<>(
                List.of(conversion(), conversion()),
                PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize()),
                2));

        assertThat(stream.truncated()).isTrue();
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
        conversion.setRateSource("POLICY");
        conversion.setRatePolicyId(UUID.fromString("00000000-0000-0000-0000-000000000706"));
        conversion.setRatePolicyName("2026 mileage rate");
        conversion.setCalculatedAmount(new BigDecimal("24.4970"));
        conversion.setRoundedAmount(new BigDecimal("24.50"));
        conversion.setRoundingMode("HALF_UP");
        conversion.setExpenseDate(LocalDate.parse("2026-05-24"));
        conversion.setUpdatedAt(Instant.parse("2026-05-24T10:01:00Z"));
        conversion.setConvertedAt(Instant.parse("2026-05-24T10:02:00Z"));
        conversion.setTripOrigin("HQ");
        conversion.setTripDestination("Client site");
        conversion.setTripPurpose("Install support");
        conversion.setOdometerStart(new BigDecimal("1200"));
        conversion.setOdometerEnd(new BigDecimal("1225"));
        conversion.setPolicyExceptionReason("Storm detour");
        conversion.setNoteMarker("[MileageAddon:converted:v1 id=1]");
        return conversion;
    }
}
