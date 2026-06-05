package com.cake.clockify.addon.mileage.report;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseListItem;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseListResult;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Server-rendered, print-friendly expense report. Lists ALL Clockify expenses in the range; expenses
 * the add-on converted (a CONVERTED {@code mileage_conversion} matched by {@code expenseId}) render the
 * add-on's reconciled mileage values, everything else renders native Clockify values.
 *
 * <p>Scope: {@code scope=mine} (or any non-admin) = the requester's own. An admin with {@code scope=team}
 * (or no scope) and no {@code userId} sees ALL users; an admin with {@code userId} sees that user. A
 * non-admin always sees their own (a foreign {@code userId} is ignored). Served under {@code /iframe/**}
 * so it authenticates via the {@code auth_token} query parameter and can open in a new browser tab.
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
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        String requesterId = claims.userId();
        if (requesterId == null || requesterId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User claim is required");
        }
        boolean admin = authorizationService.isAdmin(claims);
        boolean mineScope = "mine".equalsIgnoreCase(scope);
        // 'mine' (or any non-admin) => the requester's own; admin 'team'/default => userId or all users (null).
        String targetUserId = (!admin || mineScope)
                ? requesterId
                : (hasText(userId) ? userId.trim() : null);
        boolean includeUser = (targetUserId == null);
        LocalDate fromDate = parseRequired("from", from);
        LocalDate toDate = parseRequired("to", to);
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        String workspaceId = claims.workspaceId();
        Map<String, String> userNames = userNames(workspaceId);
        String label = includeUser ? "All users" : userLabel(userNames, targetUserId);

        try {
            ClockifyExpenseListResult scan = gateway.listExpensesForReport(workspaceId, targetUserId, fromDate, toDate);
            // Load CONVERTED conversions for exactly the scanned expense ids so every displayed expense
            // finds its override (no cap/sort mismatch between the conversion query and the merged display).
            Map<String, MileageConversion> conversions = conversionsByExpenseId(workspaceId, scan.items());
            List<ReportRow> merged = ReportMerger.merge(scan.items(), conversions, userNames);
            boolean rowCapHit = merged.size() > MAX_REPORT_ROWS;
            return ResponseEntity.ok(MileageReportRenderer.render(
                    label, fromDate, toDate, cap(merged), includeUser, scan.truncated(), rowCapHit, false));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return degraded(workspaceId, label, fromDate, toDate, targetUserId, userNames, includeUser);
        } catch (IOException | RuntimeException e) {
            return degraded(workspaceId, label, fromDate, toDate, targetUserId, userNames, includeUser);
        }
    }

    private Map<String, MileageConversion> conversionsByExpenseId(String workspaceId, List<ClockifyExpenseListItem> expenses) {
        List<String> expenseIds = expenses.stream()
                .map(ClockifyExpenseListItem::expenseId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (expenseIds.isEmpty()) {
            return Map.of();
        }
        Map<String, MileageConversion> byExpenseId = new LinkedHashMap<>();
        for (MileageConversion conversion : conversionRepository.findByWorkspaceIdAndStatusAndExpenseIdIn(
                workspaceId, MileageConversionStatus.CONVERTED, expenseIds)) {
            if (conversion.getExpenseId() != null) {
                byExpenseId.put(conversion.getExpenseId(), conversion);
            }
        }
        return byExpenseId;
    }

    private ResponseEntity<String> degraded(
            String workspaceId, String label, LocalDate from, LocalDate to,
            String targetUserId, Map<String, String> userNames, boolean includeUser) {
        PageRequest pageRequest = PageRequest.of(0, MAX_REPORT_ROWS, REPORT_SORT);
        Page<MileageConversion> page = targetUserId == null
                ? conversionRepository.findAllByWorkspaceIdAndStatusAndExpenseDateBetween(
                        workspaceId, MileageConversionStatus.CONVERTED, from, to, pageRequest)
                : conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusAndExpenseDateBetween(
                        workspaceId, targetUserId, MileageConversionStatus.CONVERTED, from, to, pageRequest);
        List<ReportRow> rows = ReportMerger.mileageOnly(page.getContent(), userNames, projectNames(workspaceId));
        boolean rowCapHit = page.getTotalElements() > MAX_REPORT_ROWS;
        return ResponseEntity.ok(MileageReportRenderer.render(
                label, from, to, cap(rows), includeUser, false, rowCapHit, true));
    }

    private static List<ReportRow> cap(List<ReportRow> rows) {
        return rows.size() > MAX_REPORT_ROWS ? rows.subList(0, MAX_REPORT_ROWS) : rows;
    }

    private Map<String, String> userNames(String workspaceId) {
        try {
            return gateway.listUsers(workspaceId).stream()
                    .filter(user -> user.id() != null && !user.id().isBlank() && user.name() != null)
                    .collect(Collectors.toMap(ClockifyUserOption::id, ClockifyUserOption::name, (left, right) -> left));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    private Map<String, String> projectNames(String workspaceId) {
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

    private static String userLabel(Map<String, String> userNames, String userId) {
        String name = userNames.get(userId);
        return name == null || name.isBlank() ? userId : name;
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
