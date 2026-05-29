package com.cake.clockify.addon.mileage.metrics;

import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.audit.MileageSkipReason;
import com.cake.clockify.addon.mileage.conversion.ConversionResult;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MileageConversionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MileageConversionMetrics metrics = new MileageConversionMetrics(registry);

    @Test
    void recordIncrementsCounterForTerminalOutcome() {
        ConversionResult converted = new ConversionResult(
                UUID.randomUUID(), "exp-1", MileageConversionStatus.CONVERTED, null, "ok");

        metrics.record(converted);
        metrics.record(converted);
        metrics.record(new ConversionResult(UUID.randomUUID(), "exp-2",
                MileageConversionStatus.SKIPPED, MileageSkipReason.EVENT_DISABLED, "disabled"));
        metrics.record(new ConversionResult(UUID.randomUUID(), "exp-3",
                MileageConversionStatus.FAILED, null, "boom"));

        assertThat(counterValue(MileageConversionStatus.CONVERTED)).isEqualTo(2.0);
        assertThat(counterValue(MileageConversionStatus.SKIPPED)).isEqualTo(1.0);
        assertThat(counterValue(MileageConversionStatus.FAILED)).isEqualTo(1.0);
        assertThat(counterValue(MileageConversionStatus.DELETED)).isEqualTo(0.0);
    }

    @Test
    void nullOrStatuslessResultIsIgnored() {
        metrics.record(null);
        metrics.record(new ConversionResult(null, null, null, null, "no status"));

        registry.getMeters().forEach(meter -> {
            if (MileageConversionMetrics.COUNTER_NAME.equals(meter.getId().getName())) {
                assertThat(meter.measure().iterator().next().getValue()).isZero();
            }
        });
    }

    @Test
    void onlyOutcomeTagIsAttachedToCounters() {
        List<Meter> conversionMeters = registry.getMeters().stream()
                .filter(meter -> MileageConversionMetrics.COUNTER_NAME.equals(meter.getId().getName()))
                .toList();

        assertThat(conversionMeters).isNotEmpty();
        Set<String> tagKeys = conversionMeters.stream()
                .flatMap(meter -> meter.getId().getTagsAsIterable().iterator().hasNext()
                        ? meter.getId().getTags().stream()
                        : java.util.stream.Stream.empty())
                .map(tag -> tag.getKey())
                .collect(Collectors.toSet());

        assertThat(tagKeys).containsOnly(MileageConversionMetrics.TAG_OUTCOME);
        assertThat(tagKeys).noneMatch(key ->
                key.toLowerCase().contains("userid")
                        || key.toLowerCase().contains("workspace")
                        || key.toLowerCase().contains("token")
                        || key.toLowerCase().contains("expense"));
    }

    @Test
    void everyMileageConversionStatusGetsACounter() {
        Set<String> registeredOutcomes = registry.getMeters().stream()
                .filter(meter -> MileageConversionMetrics.COUNTER_NAME.equals(meter.getId().getName()))
                .map(meter -> meter.getId().getTag(MileageConversionMetrics.TAG_OUTCOME))
                .collect(Collectors.toSet());

        for (MileageConversionStatus status : MileageConversionStatus.values()) {
            assertThat(registeredOutcomes).contains(status.name());
        }
    }

    private double counterValue(MileageConversionStatus status) {
        return registry.counter(MileageConversionMetrics.COUNTER_NAME,
                MileageConversionMetrics.TAG_OUTCOME, status.name()).count();
    }
}
