package com.cake.clockify.addon.mileage.packet;

import com.cake.clockify.addon.mileage.api.ClockifyOptionNameResolver;
import com.cake.clockify.addon.mileage.api.MileageDateRange;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MileageReimbursementPacketService {
    private static final int MAX_PACKET_ROWS = 5_000;
    private static final Sort PACKET_SORT = Sort.by(Sort.Order.asc("expenseDate"), Sort.Order.asc("updatedAt"));
    private static final Set<MileageConversionStatus> DEFAULT_STATUSES = EnumSet.of(
            MileageConversionStatus.CONVERTED,
            MileageConversionStatus.FAILED,
            MileageConversionStatus.SKIPPED,
            MileageConversionStatus.DRY_RUN);

    private final MileageConversionRepository conversionRepository;
    private final ClockifyOptionNameResolver nameResolver;
    private final Clock clock;

    @Autowired
    public MileageReimbursementPacketService(
            MileageConversionRepository conversionRepository,
            ClockifyOptionNameResolver nameResolver) {
        this(conversionRepository, nameResolver, Clock.systemUTC());
    }

    MileageReimbursementPacketService(
            MileageConversionRepository conversionRepository,
            ClockifyOptionNameResolver nameResolver,
            Clock clock) {
        this.conversionRepository = conversionRepository;
        this.nameResolver = nameResolver;
        this.clock = clock;
    }

    public MileageReimbursementPacket packet(
            String workspaceId,
            String targetUserId,
            MileageDateRange range,
            String projectId,
            MileageConversionStatus status,
            boolean includeDeleted,
            boolean exceptionsOnly) {
        Page<MileageConversion> page = loadRows(workspaceId, targetUserId, range, status, includeDeleted);
        List<MileageConversion> filtered = page.getContent().stream()
                .filter(conversion -> statusAllowed(conversion, status, includeDeleted))
                .filter(conversion -> projectAllowed(conversion, projectId))
                .filter(conversion -> !exceptionsOnly || isException(conversion))
                .limit(MAX_PACKET_ROWS)
                .toList();
        Map<String, String> userNames = nameResolver.userNamesById(workspaceId, filtered);
        Map<String, String> projectNames = nameResolver.projectNamesById(workspaceId, filtered);
        List<MileageReimbursementPacketRow> rows = filtered.stream()
                .map(conversion -> row(conversion, userNames, projectNames))
                .toList();
        return new MileageReimbursementPacket(
                userLabel(targetUserId, userNames),
                range.from(),
                range.to(),
                Instant.now(clock),
                rows,
                sumConverted(filtered, MileageConversion::getMiles),
                sumConverted(filtered, MileageConversion::getCalculatedAmount),
                sumConverted(filtered, MileageConversion::getRoundedAmount),
                rows.size(),
                (int) filtered.stream().filter(MileageReimbursementPacketService::isException).count(),
                filtered.stream()
                        .map(MileageConversion::getRatePolicyName)
                        .filter(MileageReimbursementPacketService::hasText)
                        .distinct()
                        .sorted()
                        .toList(),
                page.getTotalElements() > MAX_PACKET_ROWS || page.getContent().size() > MAX_PACKET_ROWS);
    }

    public String csv(MileageReimbursementPacket packet) {
        StringBuilder builder = new StringBuilder()
                .append("expense_date,user_id,user_name,project_id,project_name,expense_id,miles,rate,")
                .append("rate_source,rate_policy_id,rate_policy_name,calculated_amount,rounded_amount,")
                .append("status,skip_reason,error_code,billable,currency,receipt_present,")
                .append("trip_origin,trip_destination,trip_purpose,odometer_start,odometer_end,policy_exception_reason\n");
        for (MileageReimbursementPacketRow row : packet.rows()) {
            appendCsvRow(builder,
                    row.expenseDate(),
                    row.userId(),
                    row.userName(),
                    row.projectId(),
                    row.projectName(),
                    row.expenseId(),
                    row.miles(),
                    row.rate(),
                    row.rateSource(),
                    row.ratePolicyId(),
                    row.ratePolicyName(),
                    row.calculatedAmount(),
                    row.roundedAmount(),
                    row.status(),
                    row.skipReason(),
                    row.errorCode(),
                    row.billable(),
                    row.currency(),
                    row.receiptPresent(),
                    row.tripOrigin(),
                    row.tripDestination(),
                    row.tripPurpose(),
                    row.odometerStart(),
                    row.odometerEnd(),
                    row.policyExceptionReason());
        }
        return builder.toString();
    }

    private Page<MileageConversion> loadRows(
            String workspaceId,
            String targetUserId,
            MileageDateRange range,
            MileageConversionStatus status,
            boolean includeDeleted) {
        PageRequest request = PageRequest.of(0, MAX_PACKET_ROWS + 1, PACKET_SORT);
        if (status != null && targetUserId != null) {
            return conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusAndExpenseDateBetween(
                    workspaceId, targetUserId, status, range.from(), range.to(), request);
        }
        if (status != null) {
            return conversionRepository.findAllByWorkspaceIdAndStatusAndExpenseDateBetween(
                    workspaceId, status, range.from(), range.to(), request);
        }
        if (targetUserId != null && includeDeleted) {
            return conversionRepository.findAllByWorkspaceIdAndUserIdAndExpenseDateBetween(
                    workspaceId, targetUserId, range.from(), range.to(), request);
        }
        if (targetUserId != null) {
            return conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                    workspaceId, targetUserId, MileageConversionStatus.DELETED, range.from(), range.to(), request);
        }
        if (includeDeleted) {
            return conversionRepository.findAllByWorkspaceIdAndExpenseDateBetween(
                    workspaceId, range.from(), range.to(), request);
        }
        return conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                workspaceId, MileageConversionStatus.DELETED, range.from(), range.to(), request);
    }

    private static MileageReimbursementPacketRow row(
            MileageConversion conversion,
            Map<String, String> userNames,
            Map<String, String> projectNames) {
        return new MileageReimbursementPacketRow(
                text(conversion.getExpenseDate()),
                conversion.getUserId(),
                resolvedName(conversion.getUserId(), userNames, true),
                conversion.getProjectId(),
                resolvedName(conversion.getProjectId(), projectNames, false),
                conversion.getExpenseId(),
                decimal(conversion.getMiles()),
                decimal(conversion.getRate()),
                conversion.getRateSource(),
                conversion.getRatePolicyId(),
                conversion.getRatePolicyName(),
                decimal(conversion.getCalculatedAmount()),
                rounded(conversion.getRoundedAmount()),
                null,
                null,
                text(conversion.getStatus()),
                exceptionReason(conversion),
                text(conversion.getSkipReason()),
                conversion.getErrorCode(),
                null,
                null,
                conversion.getTripOrigin(),
                conversion.getTripDestination(),
                conversion.getTripPurpose(),
                decimal(conversion.getOdometerStart()),
                decimal(conversion.getOdometerEnd()),
                conversion.getPolicyExceptionReason());
    }

    private static boolean statusAllowed(
            MileageConversion conversion,
            MileageConversionStatus requestedStatus,
            boolean includeDeleted) {
        MileageConversionStatus status = conversion.getStatus();
        if (requestedStatus != null) {
            return includeDeleted || status != MileageConversionStatus.DELETED;
        }
        if (status == MileageConversionStatus.DELETED) {
            return includeDeleted;
        }
        return DEFAULT_STATUSES.contains(status);
    }

    private static boolean projectAllowed(MileageConversion conversion, String projectId) {
        return !hasText(projectId) || projectId.trim().equals(conversion.getProjectId());
    }

    private static boolean isException(MileageConversion conversion) {
        return conversion.getStatus() == MileageConversionStatus.FAILED
                || conversion.getStatus() == MileageConversionStatus.SKIPPED;
    }

    private static BigDecimal sumConverted(
            List<MileageConversion> conversions,
            java.util.function.Function<MileageConversion, BigDecimal> extractor) {
        return conversions.stream()
                .filter(conversion -> conversion.getStatus() == MileageConversionStatus.CONVERTED)
                .map(extractor)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String exceptionReason(MileageConversion conversion) {
        if (conversion.getStatus() == MileageConversionStatus.SKIPPED && conversion.getSkipReason() != null) {
            return String.valueOf(conversion.getSkipReason());
        }
        if (conversion.getStatus() == MileageConversionStatus.FAILED && hasText(conversion.getErrorMessage())) {
            return conversion.getErrorMessage();
        }
        if (conversion.getStatus() == MileageConversionStatus.FAILED && hasText(conversion.getErrorCode())) {
            return conversion.getErrorCode();
        }
        return "";
    }

    private static String userLabel(String targetUserId, Map<String, String> userNames) {
        if (!hasText(targetUserId)) {
            return "All users";
        }
        String name = userNames.get(targetUserId);
        return hasText(name) ? name : targetUserId;
    }

    private static String resolvedName(String id, Map<String, String> names, boolean fallbackToId) {
        if (!hasText(id)) {
            return "";
        }
        String name = names.get(id);
        if (hasText(name)) {
            return name;
        }
        return fallbackToId ? id : "";
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String rounded(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private static String escapeCsv(String value) {
        if (value.indexOf('"') >= 0 || value.indexOf(',') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
