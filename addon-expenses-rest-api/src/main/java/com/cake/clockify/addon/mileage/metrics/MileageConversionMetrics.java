package com.cake.clockify.addon.mileage.metrics;

import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.conversion.ConversionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Mileage conversion outcome counters.
 *
 * <p>Tags are restricted to the outcome enum name. Never tag with userId, workspaceId,
 * expenseId, token values, or any other PII — Prometheus tag cardinality explodes and
 * tagged identifiers leak into scrape endpoints.
 */
@Component
public class MileageConversionMetrics {

    public static final String COUNTER_NAME = "mileage.conversion.outcome";
    public static final String TAG_OUTCOME = "outcome";

    private final Map<MileageConversionStatus, Counter> outcomeCounters =
            new EnumMap<>(MileageConversionStatus.class);

    public MileageConversionMetrics(MeterRegistry registry) {
        for (MileageConversionStatus status : MileageConversionStatus.values()) {
            outcomeCounters.put(status, Counter.builder(COUNTER_NAME)
                    .tag(TAG_OUTCOME, status.name())
                    .description("Count of mileage conversion attempts by terminal outcome status")
                    .register(registry));
        }
    }

    public void record(ConversionResult result) {
        if (result == null || result.status() == null) {
            return;
        }
        Counter counter = outcomeCounters.get(result.status());
        if (counter != null) {
            counter.increment();
        }
    }
}
