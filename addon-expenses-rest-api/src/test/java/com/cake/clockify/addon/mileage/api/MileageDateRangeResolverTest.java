package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MileageDateRangeResolverTest {
    private final MileageDateRangeResolver resolver = new MileageDateRangeResolver(
            Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void missingOptionalRangeDefaultsToCurrentSundayThroughSaturdayWeek() {
        MileageDateRange range = resolver.optionalOrDefault(claims("America/New_York"), null, null);

        assertThat(range.from()).hasToString("2026-05-24");
        assertThat(range.to()).hasToString("2026-05-30");
    }

    @Test
    void requiredRangeParsesBothDates() {
        MileageDateRange range = resolver.required("2026-05-01", "2026-05-31");

        assertThat(range.from()).hasToString("2026-05-01");
        assertThat(range.to()).hasToString("2026-05-31");
    }

    @Test
    void invalidDateReturnsSpecificBadRequestMessage() {
        assertThatThrownBy(() -> resolver.optionalOrDefault(claims("UTC"), "bad", "2026-05-31"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo("from and to must use YYYY-MM-DD"));
    }

    @Test
    void reversedRangeReturnsBadRequest() {
        assertThatThrownBy(() -> resolver.required("2026-05-31", "2026-05-01"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo("from must be on or before to"));
    }

    private static NormalizedClaims claims(String timezone) {
        return new NormalizedClaims("ws-admin", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", "MEMBER", "en", "DEFAULT", timezone, Instant.now());
    }
}
