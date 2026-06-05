package com.cake.clockify.addon.mileage.report;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyProjectOption;
import com.cake.clockify.addon.mileage.clockify.ClockifyUserOption;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Server-rendered, print-friendly per-user mileage reimbursement report.
 * Served under {@code /iframe/**} so it authenticates via the {@code auth_token} query parameter and
 * can be opened in a new browser tab (top-level navigation cannot send a Bearer header).
 */
@RestController
public class MileageReportController {
    private static final int MAX_REPORT_ROWS = 1000;
    private static final Sort REPORT_SORT = Sort.by(Sort.Order.asc("expenseDate"), Sort.Order.asc("updatedAt"));

    private final MileageConversionRepository conversionRepository;
    private final MileageAuthorizationService authorizationService;
    private final ClockifyExpenseGateway gateway;

    public MileageReportController(
            MileageConversionRepository conversionRepository,
            MileageAuthorizationService authorizationService,
            ClockifyExpenseGateway gateway) {
        this.conversionRepository = conversionRepository;
        this.authorizationService = authorizationService;
        this.gateway = gateway;
    }

    @GetMapping(value = "/iframe/report", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> report(
            HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        String requesterId = claims.userId();
        if (requesterId == null || requesterId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User claim is required");
        }
        String targetUserId = authorizationService.isAdmin(claims) && hasText(userId)
                ? userId.trim()
                : requesterId;
        LocalDate fromDate = parseRequired("from", from);
        LocalDate toDate = parseRequired("to", to);
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        // Reimbursement document: only CONVERTED rows represent money actually written to Clockify.
        // Excluding DRY_RUN/FAILED/CONVERTING/SKIPPED keeps the printed Total honest (those rows
        // retain a calculatedAmount but were never reimbursed, and the report has no Status column).
        Page<MileageConversion> page = conversionRepository
                .findAllByWorkspaceIdAndUserIdAndStatusAndExpenseDateBetween(
                        claims.workspaceId(),
                        targetUserId,
                        MileageConversionStatus.CONVERTED,
                        fromDate,
                        toDate,
                        PageRequest.of(0, MAX_REPORT_ROWS, REPORT_SORT));
        List<MileageConversion> rows = page.getContent();
        boolean truncated = page.getTotalElements() > MAX_REPORT_ROWS;
        String html = MileageReportRenderer.render(
                userLabel(claims.workspaceId(), targetUserId),
                fromDate,
                toDate,
                rows,
                projectNames(claims.workspaceId(), rows),
                truncated);
        return ResponseEntity.ok(html);
    }

    private String userLabel(String workspaceId, String userId) {
        try {
            return gateway.listUsers(workspaceId).stream()
                    .filter(user -> userId.equals(user.id()))
                    .map(ClockifyUserOption::name)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse(userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return userId;
        } catch (IOException | RuntimeException e) {
            return userId;
        }
    }

    private Map<String, String> projectNames(String workspaceId, List<MileageConversion> rows) {
        if (rows.stream().map(MileageConversion::getProjectId).filter(Objects::nonNull).findAny().isEmpty()) {
            return Map.of();
        }
        try {
            return gateway.listProjects(workspaceId).stream()
                    .filter(project -> project.id() != null && !project.id().isBlank() && project.name() != null)
                    .collect(Collectors.toMap(ClockifyProjectOption::id, ClockifyProjectOption::name, (left, right) -> left));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    private static LocalDate parseRequired(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required (YYYY-MM-DD)");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must use YYYY-MM-DD");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
