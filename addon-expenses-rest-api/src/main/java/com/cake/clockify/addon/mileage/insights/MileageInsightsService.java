package com.cake.clockify.addon.mileage.insights;

import com.cake.clockify.addon.mileage.api.ClockifyOptionNameResolver;
import com.cake.clockify.addon.mileage.api.MileageDateRange;
import com.cake.clockify.addon.mileage.api.model.MileageInsightsResponse;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MileageInsightsService {
    private static final int MAX_INSIGHT_ROWS = 10_000;
    private static final Sort INSIGHT_SORT = Sort.by(Sort.Order.asc("expenseDate"), Sort.Order.asc("updatedAt"));

    private final MileageConversionRepository conversionRepository;
    private final ClockifyOptionNameResolver nameResolver;

    public MileageInsightsService(
            MileageConversionRepository conversionRepository,
            ClockifyOptionNameResolver nameResolver) {
        this.conversionRepository = conversionRepository;
        this.nameResolver = nameResolver;
    }

    public MileageInsightsResponse insights(String workspaceId, MileageDateRange range) {
        Page<MileageConversion> page = conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                workspaceId,
                MileageConversionStatus.DELETED,
                range.from(),
                range.to(),
                PageRequest.of(0, MAX_INSIGHT_ROWS, INSIGHT_SORT));
        List<MileageConversion> rows = page.getContent();
        List<MileageConversion> converted = rows.stream()
                .filter(row -> row.getStatus() == MileageConversionStatus.CONVERTED)
                .toList();
        Map<String, String> projectNames = nameResolver.projectNamesById(workspaceId, converted);
        Map<String, String> userNames = nameResolver.userNamesById(workspaceId, converted);
        return new MileageInsightsResponse(
                decimal(sum(converted, MileageConversion::getMiles)),
                money(sum(converted, MileageConversion::getCalculatedAmount)),
                money(sum(converted, MileageConversion::getRoundedAmount)),
                rows.stream().filter(row -> row.getStatus() == MileageConversionStatus.FAILED).count(),
                converted.stream().filter(row -> !hasText(row.getTripPurpose())).count(),
                converted.stream().filter(row -> hasText(row.getPolicyExceptionReason())).count(),
                counts(rows, row -> row.getStatus() == null ? "" : row.getStatus().name()),
                counts(rows, row -> row.getSkipReason() == null ? "" : row.getSkipReason().name()),
                top(converted, MileageConversion::getProjectId, projectNames),
                top(converted, MileageConversion::getUserId, userNames));
    }

    private static List<MileageInsightsResponse.CountItem> counts(
            Collection<MileageConversion> rows,
            java.util.function.Function<MileageConversion, String> keyExtractor) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MileageConversion row : rows) {
            String key = keyExtractor.apply(row);
            if (!hasText(key)) {
                continue;
            }
            counts.merge(key, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByKey())
                .map(entry -> new MileageInsightsResponse.CountItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<MileageInsightsResponse.TopItem> top(
            Collection<MileageConversion> rows,
            java.util.function.Function<MileageConversion, String> idExtractor,
            Map<String, String> namesById) {
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (MileageConversion row : rows) {
            String id = idExtractor.apply(row);
            if (!hasText(id)) {
                continue;
            }
            aggregates.computeIfAbsent(id, ignored -> new Aggregate())
                    .add(row.getCalculatedAmount(), row.getMiles());
        }
        return aggregates.entrySet().stream()
                .sorted(Map.Entry.<String, Aggregate>comparingByValue(
                        Comparator.comparing(Aggregate::amount).reversed()))
                .limit(10)
                .map(entry -> new MileageInsightsResponse.TopItem(
                        entry.getKey(),
                        name(entry.getKey(), namesById),
                        money(entry.getValue().amount()),
                        decimal(entry.getValue().miles()),
                        entry.getValue().count()))
                .toList();
    }

    private static BigDecimal sum(
            Collection<MileageConversion> rows,
            java.util.function.Function<MileageConversion, BigDecimal> extractor) {
        return rows.stream()
                .map(extractor)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String name(String id, Map<String, String> namesById) {
        String name = namesById.get(id);
        return hasText(name) ? name : id;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class Aggregate {
        private BigDecimal amount = BigDecimal.ZERO;
        private BigDecimal miles = BigDecimal.ZERO;
        private long count;

        void add(BigDecimal amount, BigDecimal miles) {
            if (amount != null) {
                this.amount = this.amount.add(amount);
            }
            if (miles != null) {
                this.miles = this.miles.add(miles);
            }
            count++;
        }

        BigDecimal amount() {
            return amount;
        }

        BigDecimal miles() {
            return miles;
        }

        long count() {
            return count;
        }
    }
}
