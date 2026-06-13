package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.MileageConversionCsvExporter.CsvStream;
import com.cake.clockify.addon.mileage.api.model.MileageConversionDetailResponse;
import com.cake.clockify.addon.mileage.api.model.MileageConversionListResponse;
import com.cake.clockify.addon.mileage.api.model.MileageConversionRetryResponse;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.conversion.MileageConversionService;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@RestController
public class MileageConversionController {
    private final MileageConversionRepository conversionRepository;
    private final MileageConversionService conversionService;
    private final MileageAuthorizationService authorizationService;
    private final MileageDateRangeResolver dateRangeResolver;
    private final MileageConversionQueryService queryService;
    private final MileageConversionCsvExporter csvExporter;
    private final ClockifyOptionNameResolver nameResolver;

    public MileageConversionController(
            MileageConversionRepository conversionRepository,
            MileageConversionService conversionService,
            MileageAuthorizationService authorizationService,
            MileageDateRangeResolver dateRangeResolver,
            MileageConversionQueryService queryService,
            MileageConversionCsvExporter csvExporter,
            ClockifyOptionNameResolver nameResolver) {
        this.conversionRepository = conversionRepository;
        this.conversionService = conversionService;
        this.authorizationService = authorizationService;
        this.dateRangeResolver = dateRangeResolver;
        this.queryService = queryService;
        this.csvExporter = csvExporter;
        this.nameResolver = nameResolver;
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
        MileageDateRange range = dateRangeResolver.optionalOrDefault(claims, from, to);
        Page<MileageConversion> conversions = queryService.conversions(
                claims.workspaceId(), userParam(userId), status, range, queryService.pageRequest(page, pageSize));
        return ResponseEntity.ok(MileageConversionListResponse.from(
                conversions,
                nameResolver.userNamesById(claims.workspaceId(), conversions.getContent())));
    }

    @GetMapping("/api/mileage/mine")
    public ResponseEntity<MileageConversionListResponse> mine(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = userClaims(request);
        MileageDateRange range = dateRangeResolver.optionalOrDefault(claims, from, to);
        Page<MileageConversion> conversions = queryService.mine(
                claims.workspaceId(), claims.userId(), range, queryService.pageRequest(page, pageSize));
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
        MileageDateRange range = dateRangeResolver.optionalOrDefault(claims, from, to);
        Page<MileageConversion> conversions = queryService.team(
                claims.workspaceId(), userParam(userId), range, queryService.pageRequest(page, pageSize));
        return ResponseEntity.ok(MileageConversionListResponse.from(
                conversions,
                nameResolver.userNamesById(claims.workspaceId(), conversions.getContent())));
    }

    @GetMapping(value = "/api/mileage/mine.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> mineCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = userClaims(request);
        MileageDateRange range = dateRangeResolver.optionalOrDefault(claims, from, to);
        return csvResponse(
                "mileage-mine.csv",
                pageRequest -> queryService.mine(claims.workspaceId(), claims.userId(), range, pageRequest),
                rows -> Map.of(),
                cachedProjectNames(claims.workspaceId()));
    }

    @GetMapping(value = "/api/mileage/team.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> teamCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = adminClaims(request);
        MileageDateRange range = dateRangeResolver.optionalOrDefault(claims, from, to);
        String filterUser = userParam(userId);
        return csvResponse(
                "mileage-team.csv",
                pageRequest -> queryService.team(claims.workspaceId(), filterUser, range, pageRequest),
                cachedUserNames(claims.workspaceId()),
                cachedProjectNames(claims.workspaceId()));
    }

    @GetMapping(value = "/api/mileage/conversions.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> conversionsCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = adminClaims(request);
        MileageDateRange range = dateRangeResolver.optionalOrDefault(claims, from, to);
        String filterUser = userParam(userId);
        return csvResponse(
                "mileage-conversions.csv",
                pageRequest -> queryService.conversions(claims.workspaceId(), filterUser, null, range, pageRequest),
                cachedUserNames(claims.workspaceId()),
                cachedProjectNames(claims.workspaceId()));
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
                nameResolver.userNameOrNull(
                        conversion.getUserId(),
                        nameResolver.userNamesById(claims.workspaceId(), java.util.List.of(conversion)))));
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

    private static String userParam(String userId) {
        return userId != null && !userId.isBlank() ? userId.trim() : null;
    }

    private ResponseEntity<StreamingResponseBody> csvResponse(
            String filename,
            Function<PageRequest, Page<MileageConversion>> fetchPage,
            Function<Collection<MileageConversion>, Map<String, String>> userNamesByPage,
            Function<Collection<MileageConversion>, Map<String, String>> projectNamesByPage) {
        CsvStream stream = csvExporter.stream(fetchPage);
        return csvExporter.response(filename, stream, userNamesByPage, projectNamesByPage);
    }

    private Function<Collection<MileageConversion>, Map<String, String>> cachedUserNames(String workspaceId) {
        AtomicReference<Map<String, String>> cache = new AtomicReference<>();
        return rows -> cachedNames(cache, rows, MileageConversion::getUserId, () -> nameResolver.allUserNamesById(workspaceId));
    }

    private Function<Collection<MileageConversion>, Map<String, String>> cachedProjectNames(String workspaceId) {
        AtomicReference<Map<String, String>> cache = new AtomicReference<>();
        return rows -> cachedNames(cache, rows, MileageConversion::getProjectId, () -> nameResolver.allProjectNamesById(workspaceId));
    }

    private static Map<String, String> cachedNames(
            AtomicReference<Map<String, String>> cache,
            Collection<MileageConversion> rows,
            Function<MileageConversion, String> idExtractor,
            java.util.function.Supplier<Map<String, String>> resolver) {
        if (rows.stream().map(idExtractor).noneMatch(MileageConversionController::hasText)) {
            return Map.of();
        }
        Map<String, String> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        Map<String, String> resolved = resolver.get();
        cache.compareAndSet(null, resolved);
        return cache.get();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
