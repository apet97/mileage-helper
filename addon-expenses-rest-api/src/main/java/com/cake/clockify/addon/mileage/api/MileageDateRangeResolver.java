package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Component
public class MileageDateRangeResolver {
    private final Clock clock;

    public MileageDateRangeResolver(Clock clock) {
        this.clock = clock;
    }

    public MileageDateRange optionalOrDefault(NormalizedClaims claims, String from, String to) {
        boolean hasFrom = hasText(from);
        boolean hasTo = hasText(to);
        if (!hasFrom && !hasTo) {
            return defaultDateRange(claims);
        }
        if (hasFrom != hasTo) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Both from and to dates are required when filtering mileage rows");
        }
        LocalDate parsedFrom = parseDate(from, "from and to must use YYYY-MM-DD");
        LocalDate parsedTo = parseDate(to, "from and to must use YYYY-MM-DD");
        return validateOrder(parsedFrom, parsedTo);
    }

    public MileageDateRange required(String from, String to) {
        LocalDate parsedFrom = parseRequired("from", from);
        LocalDate parsedTo = parseRequired("to", to);
        return validateOrder(parsedFrom, parsedTo);
    }

    public LocalDate today(NormalizedClaims claims) {
        return LocalDate.now(clock.withZone(zoneId(claims.userTimeZone())));
    }

    private MileageDateRange defaultDateRange(NormalizedClaims claims) {
        LocalDate today = today(claims);
        int daysSinceSunday = today.getDayOfWeek().getValue() % 7;
        LocalDate from = today.minusDays(daysSinceSunday);
        return new MileageDateRange(from, from.plusDays(6));
    }

    private static MileageDateRange validateOrder(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        return new MileageDateRange(from, to);
    }

    private static LocalDate parseRequired(String field, String value) {
        if (!hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required (YYYY-MM-DD)");
        }
        return parseDate(value, field + " must use YYYY-MM-DD");
    }

    private static LocalDate parseDate(String value, String message) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private static ZoneId zoneId(String value) {
        if (!hasText(value)) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(value.trim());
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
