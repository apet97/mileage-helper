package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.mileage.api.model.MileageConversionDetailResponse;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

@Component
public class MileageConversionCsvExporter {
    public static final String HEADER_EXPORT_TRUNCATED = "X-Mileage-Export-Truncated";
    static final String CSV_HEADER = "expense_id,source,source_label,status,user_id,user_name,project_id,project_name,miles,rate,rate_source,rate_policy_id,rate_policy_name,calculated_amount,expense_amount,currency,rounding_mode,expense_date,updated_at,converted_at,trip_origin,trip_destination,trip_purpose,odometer_start,odometer_end,policy_exception_reason,note_marker";
    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final int DEFAULT_CSV_PAGE_SIZE = 1_000;
    private static final int DEFAULT_CSV_MAX_ROWS = 100_000;

    private final MileageConversionQueryService queryService;
    private final int csvPageSize;
    private final int csvMaxRows;

    @Autowired
    public MileageConversionCsvExporter(MileageConversionQueryService queryService) {
        this(queryService, DEFAULT_CSV_PAGE_SIZE, DEFAULT_CSV_MAX_ROWS);
    }

    MileageConversionCsvExporter(MileageConversionQueryService queryService, int csvPageSize, int csvMaxRows) {
        this.queryService = queryService;
        this.csvPageSize = csvPageSize;
        this.csvMaxRows = csvMaxRows;
    }

    public CsvStream stream(Function<org.springframework.data.domain.PageRequest, Page<MileageConversion>> fetchPage) {
        Page<MileageConversion> firstPage = fetchPage.apply(queryService.exportPageRequest(0, csvPageSize));
        boolean truncated = firstPage.getTotalElements() > csvMaxRows || firstPage.getContent().size() > csvMaxRows;
        return new CsvStream(firstPage, fetchPage, truncated);
    }

    public ResponseEntity<StreamingResponseBody> response(
            String filename,
            CsvStream conversions,
            Function<Collection<MileageConversion>, Map<String, String>> userNamesByPage,
            Function<Collection<MileageConversion>, Map<String, String>> projectNamesByPage) {
        StreamingResponseBody body = outputStream -> {
            OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            writeCsv(writer, conversions, userNamesByPage, projectNamesByPage);
            writer.flush();
        };
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HEADER_EXPORT_TRUNCATED, Boolean.toString(conversions.truncated()))
                .body(body);
    }

    String csv(
            Collection<MileageConversion> conversions,
            Map<String, String> userNamesById,
            Map<String, String> projectNamesById) {
        StringBuilder builder = new StringBuilder(CSV_HEADER).append('\n');
        try {
            appendCsvRows(builder, conversions, conversions.size(), userNamesById, projectNamesById);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return builder.toString();
    }

    void writeCsv(
            Appendable writer,
            Iterable<MileageConversion> conversions,
            Map<String, String> userNamesById,
            Map<String, String> projectNamesById) throws IOException {
        writer.append(CSV_HEADER).append('\n');
        appendCsvRows(writer, conversions, Integer.MAX_VALUE, userNamesById, projectNamesById);
    }

    private void writeCsv(
            Appendable writer,
            CsvStream conversions,
            Function<Collection<MileageConversion>, Map<String, String>> userNamesByPage,
            Function<Collection<MileageConversion>, Map<String, String>> projectNamesByPage) throws IOException {
        writer.append(CSV_HEADER).append('\n');
        Page<MileageConversion> batch = conversions.firstPage();
        int page = 0;
        int written = 0;
        while (true) {
            Collection<MileageConversion> rows = batch.getContent();
            int remaining = csvMaxRows - written;
            if (remaining <= 0) {
                return;
            }
            written += appendCsvRows(
                    writer,
                    rows,
                    remaining,
                    userNamesByPage.apply(rows),
                    projectNamesByPage.apply(rows));
            if (written >= csvMaxRows || !batch.hasNext()) {
                return;
            }
            page++;
            batch = conversions.fetchPage().apply(queryService.exportPageRequest(page, csvPageSize));
        }
    }

    private static int appendCsvRows(
            Appendable writer,
            Iterable<MileageConversion> conversions,
            int limit,
            Map<String, String> userNamesById,
            Map<String, String> projectNamesById) throws IOException {
        int written = 0;
        for (MileageConversion conversion : conversions) {
            if (written >= limit) {
                return written;
            }
            appendCsvRow(writer,
                    conversion.getExpenseId(),
                    conversion.getSource(),
                    MileageConversionDetailResponse.sourceLabel(conversion.getSource()),
                    conversion.getStatus(),
                    conversion.getUserId(),
                    userName(conversion.getUserId(), userNamesById),
                    conversion.getProjectId(),
                    projectName(conversion.getProjectId(), projectNamesById),
                    decimalText(conversion.getMiles()),
                    decimalText(conversion.getRate()),
                    conversion.getRateSource(),
                    conversion.getRatePolicyId(),
                    conversion.getRatePolicyName(),
                    decimalText(conversion.getCalculatedAmount()),
                    roundedText(conversion.getRoundedAmount()),
                    null,
                    conversion.getRoundingMode(),
                    conversion.getExpenseDate(),
                    conversion.getUpdatedAt(),
                    conversion.getConvertedAt(),
                    conversion.getTripOrigin(),
                    conversion.getTripDestination(),
                    conversion.getTripPurpose(),
                    decimalText(conversion.getOdometerStart()),
                    decimalText(conversion.getOdometerEnd()),
                    conversion.getPolicyExceptionReason(),
                    conversion.getNoteMarker());
            written++;
        }
        return written;
    }

    private static void appendCsvRow(Appendable builder, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escapeCsv(spreadsheetSafe(text(values[i]))));
        }
        builder.append('\n');
    }

    private static String spreadsheetSafe(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r' || first == '\n') {
            return "'" + value;
        }
        return value;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return String.valueOf(value);
    }

    private static String userName(String userId, Map<String, String> userNamesById) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        String name = userNamesById.get(userId);
        return name == null || name.isBlank() ? userId : name;
    }

    private static String projectName(String projectId, Map<String, String> projectNamesById) {
        if (projectId == null || projectId.isBlank()) {
            return "";
        }
        String name = projectNamesById.get(projectId);
        return name == null || name.isBlank() ? "" : name;
    }

    private static String decimalText(java.math.BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String roundedText(java.math.BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String escapeCsv(String value) {
        if (value.indexOf('"') >= 0 || value.indexOf(',') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    public record CsvStream(
            Page<MileageConversion> firstPage,
            Function<org.springframework.data.domain.PageRequest, Page<MileageConversion>> fetchPage,
            boolean truncated) {
    }
}
