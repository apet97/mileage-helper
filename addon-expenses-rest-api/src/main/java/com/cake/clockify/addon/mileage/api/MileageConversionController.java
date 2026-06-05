package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.model.MileageConversionDetailResponse;
import com.cake.clockify.addon.mileage.api.model.MileageConversionListResponse;
import com.cake.clockify.addon.mileage.api.model.MileageConversionRetryResponse;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyProjectOption;
import com.cake.clockify.addon.mileage.clockify.ClockifyUserOption;
import com.cake.clockify.addon.mileage.conversion.MileageConversionService;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
public class MileageConversionController {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int CSV_PAGE_SIZE = 1_000;
    private static final int CSV_MAX_ROWS = 100_000;
    private static final String HEADER_EXPORT_TRUNCATED = "X-Mileage-Export-Truncated";
    private static final Sort VISIBLE_LIST_SORT = Sort.by(
            Sort.Order.desc("expenseDate"),
            Sort.Order.desc("updatedAt"));
    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final String CSV_HEADER = "expense_id,source,source_label,status,user_id,user_name,project_id,project_name,miles,rate,calculated_amount,expense_amount,rounding_mode,expense_date,updated_at,converted_at,note_marker";

    private final MileageConversionRepository conversionRepository;
    private final MileageConversionService conversionService;
    private final MileageAuthorizationService authorizationService;
    private final ClockifyExpenseGateway gateway;
    private final Clock clock;

    public MileageConversionController(
            MileageConversionRepository conversionRepository,
            MileageConversionService conversionService,
            MileageAuthorizationService authorizationService,
            ClockifyExpenseGateway gateway,
            Clock clock) {
        this.conversionRepository = conversionRepository;
        this.conversionService = conversionService;
        this.authorizationService = authorizationService;
        this.gateway = gateway;
        this.clock = clock;
    }

    @GetMapping("/api/mileage/conversions")
    public ResponseEntity<MileageConversionListResponse> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) MileageConversionStatus status,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = adminClaims(request);
        MileageDateRange range = dateRange(claims, from, to);
        Page<MileageConversion> conversions = conversionsPage(
                claims.workspaceId(), userParam(userId), status, range, pageRequest(page, pageSize));
        return ResponseEntity.ok(MileageConversionListResponse.from(
                conversions,
                userNamesById(claims.workspaceId(), conversions.getContent())));
    }

    @GetMapping("/api/mileage/mine")
    public ResponseEntity<MileageConversionListResponse> mine(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = userClaims(request);
        MileageDateRange range = dateRange(claims, from, to);
        Page<MileageConversion> conversions = conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                claims.workspaceId(),
                claims.userId(),
                MileageConversionStatus.DELETED,
                range.from(),
                range.to(),
                pageRequest(page, pageSize));
        return ResponseEntity.ok(MileageConversionListResponse.from(conversions));
    }

    @GetMapping("/api/mileage/team")
    public ResponseEntity<MileageConversionListResponse> team(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = adminClaims(request);
        MileageDateRange range = dateRange(claims, from, to);
        Page<MileageConversion> conversions = teamPage(
                claims.workspaceId(), userParam(userId), range, pageRequest(page, pageSize));
        return ResponseEntity.ok(MileageConversionListResponse.from(
                conversions,
                userNamesById(claims.workspaceId(), conversions.getContent())));
    }

    @GetMapping(value = "/api/mileage/mine.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> mineCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = userClaims(request);
        MileageDateRange range = dateRange(claims, from, to);
        CsvRows conversions = csvRows(pageRequest -> conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                claims.workspaceId(),
                claims.userId(),
                MileageConversionStatus.DELETED,
                range.from(),
                range.to(),
                pageRequest));
        return csvResponse("mileage-mine.csv", conversions, Map.of(), projectNamesById(claims.workspaceId(), conversions.rows()));
    }

    @GetMapping(value = "/api/mileage/team.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> teamCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = adminClaims(request);
        MileageDateRange range = dateRange(claims, from, to);
        String filterUser = userParam(userId);
        CsvRows conversions = csvRows(pageRequest -> teamPage(claims.workspaceId(), filterUser, range, pageRequest));
        List<MileageConversion> teamRows = conversions.rows();
        return csvResponse(
                "mileage-team.csv",
                conversions,
                userNamesById(claims.workspaceId(), teamRows),
                projectNamesById(claims.workspaceId(), teamRows));
    }

    @GetMapping(value = "/api/mileage/conversions.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> conversionsCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = adminClaims(request);
        MileageDateRange range = dateRange(claims, from, to);
        String filterUser = userParam(userId);
        CsvRows conversions = csvRows(pageRequest -> conversionsPage(claims.workspaceId(), filterUser, null, range, pageRequest));
        List<MileageConversion> conversionRows = conversions.rows();
        return csvResponse(
                "mileage-conversions.csv",
                conversions,
                userNamesById(claims.workspaceId(), conversionRows),
                projectNamesById(claims.workspaceId(), conversionRows));
    }

    @GetMapping("/api/mileage/conversions/{id}")
    public ResponseEntity<MileageConversionDetailResponse> detail(
            HttpServletRequest request,
            @PathVariable UUID id) {
        NormalizedClaims claims = adminClaims(request);
        MileageConversion conversion = conversionRepository.findByIdAndWorkspaceId(id, claims.workspaceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mileage conversion was not found"));
        return ResponseEntity.ok(MileageConversionDetailResponse.from(
                conversion,
                userNameOrNull(conversion.getUserId(), userNamesById(claims.workspaceId(), java.util.List.of(conversion)))));
    }

    @PostMapping("/api/mileage/conversions/{id}/retry")
    public ResponseEntity<MileageConversionRetryResponse> retry(
            HttpServletRequest request,
            @PathVariable UUID id) {
        NormalizedClaims claims = adminClaims(request);
        return ResponseEntity.ok(MileageConversionRetryResponse.from(conversionService.retry(claims, id)));
    }

    private NormalizedClaims adminClaims(HttpServletRequest request) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        authorizationService.requireAdmin(claims);
        return claims;
    }

    private NormalizedClaims userClaims(HttpServletRequest request) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        if (claims.userId() == null || claims.userId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User claim is required");
        }
        return claims;
    }

    private static PageRequest pageRequest(int page, int pageSize) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE), VISIBLE_LIST_SORT);
    }

    private static String userParam(String userId) {
        return hasText(userId) ? userId.trim() : null;
    }

    private Page<MileageConversion> teamPage(String workspaceId, String userId, MileageDateRange range, PageRequest pageRequest) {
        if (userId != null) {
            return conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                    workspaceId, userId, MileageConversionStatus.DELETED, range.from(), range.to(), pageRequest);
        }
        return conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                workspaceId, MileageConversionStatus.DELETED, range.from(), range.to(), pageRequest);
    }

    private Page<MileageConversion> conversionsPage(String workspaceId, String userId, MileageConversionStatus status, MileageDateRange range, PageRequest pageRequest) {
        if (userId != null && status != null) {
            return conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusAndExpenseDateBetween(
                    workspaceId, userId, status, range.from(), range.to(), pageRequest);
        }
        if (userId != null) {
            return conversionRepository.findAllByWorkspaceIdAndUserIdAndExpenseDateBetween(
                    workspaceId, userId, range.from(), range.to(), pageRequest);
        }
        if (status != null) {
            return conversionRepository.findAllByWorkspaceIdAndStatusAndExpenseDateBetween(
                    workspaceId, status, range.from(), range.to(), pageRequest);
        }
        return conversionRepository.findAllByWorkspaceIdAndExpenseDateBetween(
                workspaceId, range.from(), range.to(), pageRequest);
    }

    private MileageDateRange dateRange(NormalizedClaims claims, String from, String to) {
        boolean hasFrom = hasText(from);
        boolean hasTo = hasText(to);
        if (!hasFrom && !hasTo) {
            return defaultDateRange(claims);
        }
        if (hasFrom != hasTo) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Both from and to dates are required when filtering mileage rows");
        }
        LocalDate parsedFrom = parseDate(from);
        LocalDate parsedTo = parseDate(to);
        if (parsedFrom.isAfter(parsedTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        return new MileageDateRange(parsedFrom, parsedTo);
    }

    private MileageDateRange defaultDateRange(NormalizedClaims claims) {
        LocalDate today = LocalDate.now(clock.withZone(zoneId(claims.userTimeZone())));
        int daysSinceSunday = today.getDayOfWeek().getValue() % 7;
        LocalDate from = today.minusDays(daysSinceSunday);
        return new MileageDateRange(from, from.plusDays(6));
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to must use YYYY-MM-DD");
        }
    }

    private static ZoneId zoneId(String value) {
        if (!hasText(value)) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(value.trim());
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private static ResponseEntity<String> csvResponse(
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

    private static String csv(
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

    private static CsvRows csvRows(Function<PageRequest, Page<MileageConversion>> fetchPage) {
        List<MileageConversion> rows = new ArrayList<>();
        int page = 0;
        while (rows.size() < CSV_MAX_ROWS) {
            Page<MileageConversion> batch = fetchPage.apply(PageRequest.of(page, CSV_PAGE_SIZE, VISIBLE_LIST_SORT));
            List<MileageConversion> content = batch.getContent();
            int remaining = CSV_MAX_ROWS - rows.size();
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, String> userNamesById(String workspaceId, Collection<MileageConversion> conversions) {
        if (conversions.stream().map(MileageConversion::getUserId).filter(Objects::nonNull).findAny().isEmpty()) {
            return Map.of();
        }
        try {
            return gateway.listUsers(workspaceId).stream()
                    .filter(user -> user.id() != null && !user.id().isBlank() && user.name() != null)
                    .collect(Collectors.toMap(
                            ClockifyUserOption::id,
                            ClockifyUserOption::name,
                            (left, right) -> left));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    private static String userName(String userId, Map<String, String> userNamesById) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        String name = userNamesById.get(userId);
        return name == null || name.isBlank() ? userId : name;
    }

    private static String userNameOrNull(String userId, Map<String, String> userNamesById) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userNamesById.get(userId);
    }

    private Map<String, String> projectNamesById(String workspaceId, Collection<MileageConversion> conversions) {
        if (conversions.stream().map(MileageConversion::getProjectId).filter(Objects::nonNull).findAny().isEmpty()) {
            return Map.of();
        }
        try {
            return gateway.listProjects(workspaceId).stream()
                    .filter(project -> project.id() != null && !project.id().isBlank() && project.name() != null)
                    .collect(Collectors.toMap(
                            ClockifyProjectOption::id,
                            ClockifyProjectOption::name,
                            (left, right) -> left));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
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

    private record MileageDateRange(LocalDate from, LocalDate to) {
    }

    private record CsvRows(List<MileageConversion> rows, boolean truncated) {
    }
}
