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
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class MileageConversionController {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int CSV_PAGE_SIZE = 5_000;
    private static final Sort VISIBLE_LIST_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final String CSV_HEADER = "expense_id,source,source_label,status,user_id,user_name,project_id,miles,rate,calculated_amount,expense_amount,rounding_mode,updated_at,converted_at,note_marker";

    private final MileageConversionRepository conversionRepository;
    private final MileageConversionService conversionService;
    private final MileageAuthorizationService authorizationService;
    private final ClockifyExpenseGateway gateway;

    public MileageConversionController(
            MileageConversionRepository conversionRepository,
            MileageConversionService conversionService,
            MileageAuthorizationService authorizationService,
            ClockifyExpenseGateway gateway) {
        this.conversionRepository = conversionRepository;
        this.conversionService = conversionService;
        this.authorizationService = authorizationService;
        this.gateway = gateway;
    }

    @GetMapping("/api/mileage/conversions")
    public ResponseEntity<MileageConversionListResponse> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) MileageConversionStatus status) {
        NormalizedClaims claims = adminClaims(request);
        PageRequest pageRequest = pageRequest(page, pageSize);
        Page<MileageConversion> conversions = status == null
                ? conversionRepository.findAllByWorkspaceId(claims.workspaceId(), pageRequest)
                : conversionRepository.findAllByWorkspaceIdAndStatus(claims.workspaceId(), status, pageRequest);
        return ResponseEntity.ok(MileageConversionListResponse.from(
                conversions,
                userNamesById(claims.workspaceId(), conversions.getContent())));
    }

    @GetMapping("/api/mileage/mine")
    public ResponseEntity<MileageConversionListResponse> mine(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        NormalizedClaims claims = userClaims(request);
        Page<MileageConversion> conversions = conversionRepository.findAllByWorkspaceIdAndUserId(
                claims.workspaceId(),
                claims.userId(),
                pageRequest(page, pageSize));
        return ResponseEntity.ok(MileageConversionListResponse.from(conversions));
    }

    @GetMapping("/api/mileage/team")
    public ResponseEntity<MileageConversionListResponse> team(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        NormalizedClaims claims = adminClaims(request);
        Page<MileageConversion> conversions = conversionRepository.findAllByWorkspaceId(
                claims.workspaceId(),
                pageRequest(page, pageSize));
        return ResponseEntity.ok(MileageConversionListResponse.from(
                conversions,
                userNamesById(claims.workspaceId(), conversions.getContent())));
    }

    @GetMapping(value = "/api/mileage/mine.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> mineCsv(HttpServletRequest request) {
        NormalizedClaims claims = userClaims(request);
        Page<MileageConversion> conversions = conversionRepository.findAllByWorkspaceIdAndUserId(
                claims.workspaceId(),
                claims.userId(),
                PageRequest.of(0, CSV_PAGE_SIZE, VISIBLE_LIST_SORT));
        return csvResponse("mileage-mine.csv", conversions, Map.of());
    }

    @GetMapping(value = "/api/mileage/team.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> teamCsv(HttpServletRequest request) {
        NormalizedClaims claims = adminClaims(request);
        Page<MileageConversion> conversions = conversionRepository.findAllByWorkspaceId(
                claims.workspaceId(),
                PageRequest.of(0, CSV_PAGE_SIZE, VISIBLE_LIST_SORT));
        return csvResponse("mileage-team.csv", conversions, userNamesById(claims.workspaceId(), conversions.getContent()));
    }

    @GetMapping(value = "/api/mileage/conversions.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> conversionsCsv(HttpServletRequest request) {
        NormalizedClaims claims = adminClaims(request);
        Page<MileageConversion> conversions = conversionRepository.findAllByWorkspaceId(
                claims.workspaceId(),
                PageRequest.of(0, CSV_PAGE_SIZE, VISIBLE_LIST_SORT));
        return csvResponse("mileage-conversions.csv", conversions, userNamesById(claims.workspaceId(), conversions.getContent()));
    }

    @GetMapping("/api/mileage/conversions/{id}")
    public ResponseEntity<MileageConversionDetailResponse> detail(
            HttpServletRequest request,
            @PathVariable UUID id) {
        NormalizedClaims claims = adminClaims(request);
        MileageConversion conversion = conversionRepository.findById(id)
                .filter(row -> claims.workspaceId().equals(row.getWorkspaceId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mileage conversion was not found"));
        return ResponseEntity.ok(MileageConversionDetailResponse.from(
                conversion,
                userNamesById(claims.workspaceId(), java.util.List.of(conversion)).get(conversion.getUserId())));
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

    private static ResponseEntity<String> csvResponse(
            String filename,
            Page<MileageConversion> conversions,
            Map<String, String> userNamesById) {
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(csv(conversions, userNamesById));
    }

    private static String csv(Page<MileageConversion> conversions, Map<String, String> userNamesById) {
        StringBuilder builder = new StringBuilder(CSV_HEADER).append('\n');
        for (MileageConversion conversion : conversions.getContent()) {
            appendCsvRow(builder,
                    conversion.getExpenseId(),
                    conversion.getSource(),
                    MileageConversionDetailResponse.sourceLabel(conversion.getSource()),
                    conversion.getStatus(),
                    conversion.getUserId(),
                    userName(conversion.getUserId(), userNamesById),
                    conversion.getProjectId(),
                    decimalText(conversion.getMiles()),
                    decimalText(conversion.getRate()),
                    decimalText(conversion.getCalculatedAmount()),
                    roundedText(conversion.getRoundedAmount()),
                    conversion.getRoundingMode(),
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
            builder.append(escapeCsv(text(values[i])));
        }
        builder.append('\n');
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

    private Map<String, String> userNamesById(String workspaceId, Collection<MileageConversion> conversions) {
        if (conversions.stream().map(MileageConversion::getUserId).filter(Objects::nonNull).findAny().isEmpty()) {
            return Map.of();
        }
        try {
            return gateway.listUsers(workspaceId).stream()
                    .filter(user -> user.id() != null && !user.id().isBlank())
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
        String name = userNamesById.get(userId);
        return name == null || name.isBlank() ? userId : name;
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
}
