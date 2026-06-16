package com.cake.clockify.addon.mileage.packet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MileageReimbursementPacket(
        String userLabel,
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        List<MileageReimbursementPacketRow> rows,
        BigDecimal totalMiles,
        BigDecimal totalCalculatedAmount,
        BigDecimal totalRoundedAmount,
        int rowCount,
        int exceptionCount,
        List<String> ratePolicyNames,
        boolean truncated) {
}
