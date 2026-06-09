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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class MileageConversionCsvExporter {
    public static final String HEADER_EXPORT_TRUNCATED = "X-Mileage-Export-Truncated";
    static final String CSV_HEADER = "expense_id,source,source_label,status,user_id,user_name,project_id,project_name,miles,rate,calculated_amount,expense_amount,rounding_mode,expense_date,updated_at,converted_at,note_marker";
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

    public CsvRows collect(Function<org.springframework.data.domain.PageRequest, Page<MileageConversion>> fetchPage) {
        List<MileageConversion> rows = new ArrayList<>();
        int page = 0;
        while (rows.size() < csvMaxRows) {
            Page<MileageConversion> batch = fetchPage.apply(queryService.exportPageRequest(page, csvPageSize));
            List<MileageConversion> content = batch.getContent();
            int remaining = csvMaxRows - rows.size();
            if (content.size() > remaining) {
                rows.addAll(content.subList(0, remaining));
                return new CsvRows(rows, true);
            }
            rows.addAll(content);
            if (!batch.hasNext()) {
                return new CsvRows(rows, false);
            }
            page++;
        }
        return new CsvRows(rows, true);
    }

    public ResponseEntity<String> response(
            String filename,
            CsvRows conversions,
            Map<String, String> userNamesById,
            Map<String, String> projectNamesById) {
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HEADER_EXPORT_TRUNCATED, Boolean.toString(conversions.truncated()))
                .body(csv(conversions.rows(), userNamesById, projectNamesById));
    }

    String csv(
            Collection<MileageConversion> conversions,
            Map<String, String> userNamesById,
            Map<String, String> projectNamesById) {
        StringBuilder builder = new StringBuilder(CSV_HEADER).append('\n');
        for (MileageConversion conversion : conversions) {
            appendCsvRow(builder,
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
                    decimalText(conversion.getCalculatedAmount()),
                    roundedText(conversion.getRoundedAmount()),
                    conversion.getRoundingMode(),
                    conversion.getExpenseDate(),
                    conversion.getUpdatedAt(),
                    conversion.getConvertedAt(),
                    conversion.getNoteMarker());
        }
        return builder.toString();
    }

    private static void appendCsvRow(StringBuilder builder, Object... values) {
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

    public record CsvRows(List<MileageConversion> rows, boolean truncated) {
    }
}
