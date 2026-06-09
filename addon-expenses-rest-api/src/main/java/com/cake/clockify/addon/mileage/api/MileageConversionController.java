package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.MileageConversionCsvExporter.CsvRows;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    public ResponseEntity<String> mineCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = userClaims(request);
        MileageDateRange range = dateRangeResolver.optionalOrDefault(claims, from, to);
        CsvRows conversions = csvExporter.collect(pageRequest -> queryService.mine(
                claims.workspaceId(), claims.userId(), range, pageRequest));
        return csvExporter.response(
                "mileage-mine.csv",
                conversions,
                Map.of(),
                nameResolver.projectNamesById(claims.workspaceId(), conversions.rows()));
    }

    @GetMapping(value = "/api/mileage/team.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> teamCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = adminClaims(request);
        MileageDateRange range = dateRangeResolver.optionalOrDefault(claims, from, to);
        String filterUser = userParam(userId);
        CsvRows conversions = csvExporter.collect(pageRequest -> queryService.team(
                claims.workspaceId(), filterUser, range, pageRequest));
        List<MileageConversion> teamRows = conversions.rows();
        return csvExporter.response(
                "mileage-team.csv",
                conversions,
                nameResolver.userNamesById(claims.workspaceId(), teamRows),
                nameResolver.projectNamesById(claims.workspaceId(), teamRows));
    }

    @GetMapping(value = "/api/mileage/conversions.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> conversionsCsv(
            HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = adminClaims(request);
        MileageDateRange range = dateRangeResolver.optionalOrDefault(claims, from, to);
        String filterUser = userParam(userId);
        CsvRows conversions = csvExporter.collect(pageRequest -> queryService.conversions(
                claims.workspaceId(), filterUser, null, range, pageRequest));
        List<MileageConversion> conversionRows = conversions.rows();
        return csvExporter.response(
                "mileage-conversions.csv",
                conversions,
                nameResolver.userNamesById(claims.workspaceId(), conversionRows),
                nameResolver.projectNamesById(claims.workspaceId(), conversionRows));
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
}
