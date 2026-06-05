package com.cake.clockify.addon.mileage.report;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseListResult;
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
 * <p>Scope: an admin with no {@code userId} sees ALL users; an admin with {@code userId} sees that user;
 * a non-admin always sees their own (a foreign {@code userId} is ignored). Served under {@code /iframe/**}
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
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        String requesterId = claims.userId();
        if (requesterId == null || requesterId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User claim is required");
        }
        // admin + no userId => all users (null); admin + userId => that user; non-admin => forced own.
        String targetUserId = authorizationService.isAdmin(claims)
                ? (hasText(userId) ? userId.trim() : null)
                : requesterId;
        boolean includeUser = (targetUserId == null);
        LocalDate fromDate = parseRequired("from", from);
        LocalDate toDate = parseRequired("to", to);
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        String workspaceId = claims.workspaceId();

        // Only CONVERTED rows represent money actually written to Clockify; index by expenseId for override.
        Map<String, MileageConversion> conversions = loadConversions(workspaceId, targetUserId, fromDate, toDate);
        Map<String, String> userNames = userNames(workspaceId);
        String label = includeUser ? "All users" : userLabel(userNames, targetUserId);

        try {
            ClockifyExpenseListResult scan = gateway.listExpensesForReport(workspaceId, targetUserId, fromDate, toDate);
            List<ReportRow> merged = ReportMerger.merge(scan.items(), conversions, userNames);
            boolean truncated = scan.truncated() || merged.size() > MAX_REPORT_ROWS;
            List<ReportRow> shown = cap(merged);
            return ResponseEntity.ok(
                    MileageReportRenderer.render(label, fromDate, toDate, shown, includeUser, truncated, false));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return degraded(label, fromDate, toDate, conversions, userNames, includeUser);
        } catch (IOException | RuntimeException e) {
            return degraded(label, fromDate, toDate, conversions, userNames, includeUser);
        }
    }

    private Map<String, MileageConversion> loadConversions(
            String workspaceId, String targetUserId, LocalDate from, LocalDate to) {
        PageRequest pageRequest = PageRequest.of(0, MAX_REPORT_ROWS, REPORT_SORT);
        Page<MileageConversion> page = targetUserId == null
                ? conversionRepository.findAllByWorkspaceIdAndStatusAndExpenseDateBetween(
                        workspaceId, MileageConversionStatus.CONVERTED, from, to, pageRequest)
                : conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusAndExpenseDateBetween(
                        workspaceId, targetUserId, MileageConversionStatus.CONVERTED, from, to, pageRequest);
        Map<String, MileageConversion> byExpenseId = new LinkedHashMap<>();
        for (MileageConversion conversion : page.getContent()) {
            if (conversion.getExpenseId() != null) {
                byExpenseId.put(conversion.getExpenseId(), conversion); // sorted by updatedAt asc => latest wins
            }
        }
        return byExpenseId;
    }

    private ResponseEntity<String> degraded(
            String label, LocalDate from, LocalDate to,
            Map<String, MileageConversion> conversions, Map<String, String> userNames, boolean includeUser) {
        List<ReportRow> rows = ReportMerger.mileageOnly(conversions.values(), userNames);
        boolean truncated = rows.size() > MAX_REPORT_ROWS;
        return ResponseEntity.ok(
                MileageReportRenderer.render(label, from, to, cap(rows), includeUser, truncated, true));
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
