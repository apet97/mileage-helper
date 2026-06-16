package com.cake.clockify.addon.mileage.packet;

import com.cake.clockify.addon.mileage.api.ClockifyOptionNameResolver;
import com.cake.clockify.addon.mileage.api.MileageDateRange;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.audit.MileageSkipReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MileageReimbursementPacketServiceTest {
    private static final MileageDateRange RANGE = new MileageDateRange(
            LocalDate.parse("2026-05-01"),
            LocalDate.parse("2026-05-31"));

    private MileageConversionRepository repository;
    private ClockifyOptionNameResolver nameResolver;
    private MileageReimbursementPacketService service;

    @BeforeEach
    void setUp() {
        repository = mock(MileageConversionRepository.class);
        nameResolver = mock(ClockifyOptionNameResolver.class);
        service = new MileageReimbursementPacketService(
                repository,
                nameResolver,
                Clock.fixed(Instant.parse("2026-06-16T12:00:00Z"), ZoneOffset.UTC));
        when(nameResolver.userNamesById(eq("ws-1"), any())).thenReturn(Map.of("user-1", "Ada Lovelace"));
        when(nameResolver.projectNamesById(eq("ws-1"), any())).thenReturn(Map.of("project-1", "North Route"));
    }

    @Test
    void buildsPacketTotalsFromConvertedRowsAndCountsExceptions() {
        when(repository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-1"), eq(MileageConversionStatus.DELETED), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        conversion("exp-1", "user-1", MileageConversionStatus.CONVERTED, "3.5", "25.38585", "25.39"),
                        skipped("exp-2", "user-1"))));

        MileageReimbursementPacket packet = service.packet("ws-1", null, RANGE, null, null, false, false);

        assertThat(packet.totalMiles()).isEqualByComparingTo("3.5");
        assertThat(packet.totalCalculatedAmount()).isEqualByComparingTo("25.38585");
        assertThat(packet.totalRoundedAmount()).isEqualByComparingTo("25.39");
        assertThat(packet.rowCount()).isEqualTo(2);
        assertThat(packet.exceptionCount()).isEqualTo(1);
        assertThat(packet.ratePolicyNames()).containsExactly("2026 rate");
        assertThat(packet.rows().get(0).projectName()).isEqualTo("North Route");
    }

    @Test
    void userFilterUsesWorkspaceAndUserRepositoryPath() {
        when(repository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-1"), eq("user-1"), eq(MileageConversionStatus.DELETED), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("exp-1", "user-1", MileageConversionStatus.CONVERTED, "1", "7.25", "7.25"))));

        MileageReimbursementPacket packet = service.packet("ws-1", "user-1", RANGE, null, null, false, false);

        assertThat(packet.userLabel()).isEqualTo("Ada Lovelace");
        verify(repository).findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                eq("ws-1"), eq("user-1"), eq(MileageConversionStatus.DELETED), any(), any(), any(Pageable.class));
    }

    @Test
    void excludesDeletedByDefaultAndIncludesDeletedWhenRequested() {
        when(repository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-1"), eq(MileageConversionStatus.DELETED), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deleted("exp-deleted"))));
        when(repository.findAllByWorkspaceIdAndExpenseDateBetween(eq("ws-1"), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deleted("exp-deleted"))));

        MileageReimbursementPacket defaultPacket = service.packet("ws-1", null, RANGE, null, null, false, false);
        MileageReimbursementPacket auditPacket = service.packet("ws-1", null, RANGE, null, null, true, false);

        assertThat(defaultPacket.rows()).isEmpty();
        assertThat(auditPacket.rows()).extracting(MileageReimbursementPacketRow::expenseId).containsExactly("exp-deleted");
    }

    @Test
    void filtersProjectStatusAndExceptionsOnly() {
        when(repository.findAllByWorkspaceIdAndStatusAndExpenseDateBetween(
                eq("ws-1"), eq(MileageConversionStatus.SKIPPED), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        skipped("exp-keep", "project-1"),
                        skipped("exp-drop", "project-2"))));

        MileageReimbursementPacket packet = service.packet(
                "ws-1", null, RANGE, "project-1", MileageConversionStatus.SKIPPED, false, true);

        assertThat(packet.rows()).extracting(MileageReimbursementPacketRow::expenseId).containsExactly("exp-keep");
        assertThat(packet.exceptionCount()).isEqualTo(1);
    }

    @Test
    void csvIncludesRequiredColumnsAndLeavesUnknownsBlank() {
        when(repository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                eq("ws-1"), eq(MileageConversionStatus.DELETED), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversion("exp-1", "user-1", MileageConversionStatus.CONVERTED, "1", "7.25", "7.25"))));

        String csv = service.csv(service.packet("ws-1", null, RANGE, null, null, false, false));

        assertThat(csv).startsWith("expense_date,user_id,user_name,project_id,project_name,expense_id,miles,rate,");
        assertThat(csv).contains("exp-1,1,0.725,POLICY");
        assertThat(csv).contains(",CONVERTED,,,");
        assertThat(csv).contains("HQ,Client site,Install support,1200,1225,Storm detour");
    }

    private static MileageConversion skipped(String expenseId) {
        return skipped(expenseId, "project-1");
    }

    private static MileageConversion skipped(String expenseId, String projectId) {
        MileageConversion conversion = conversion(expenseId, "user-1", MileageConversionStatus.SKIPPED, "0", "0", "0.00");
        conversion.setProjectId(projectId);
        conversion.setSkipReason(MileageSkipReason.FINALIZED_OR_LOCKED);
        return conversion;
    }

    private static MileageConversion deleted(String expenseId) {
        MileageConversion conversion = conversion(expenseId, "user-1", MileageConversionStatus.DELETED, "1", "7.25", "7.25");
        conversion.setDeletedAt(Instant.parse("2026-05-25T12:00:00Z"));
        return conversion;
    }

    private static MileageConversion conversion(
            String expenseId,
            String userId,
            MileageConversionStatus status,
            String miles,
            String calculated,
            String rounded) {
        MileageConversion conversion = new MileageConversion();
        conversion.setId(UUID.randomUUID());
        conversion.setWorkspaceId("ws-1");
        conversion.setExpenseId(expenseId);
        conversion.setSource(MileageConversionSource.ADDON_FORM);
        conversion.setStatus(status);
        conversion.setUserId(userId);
        conversion.setProjectId("project-1");
        conversion.setExpenseDate(LocalDate.parse("2026-05-24"));
        conversion.setMiles(new BigDecimal(miles));
        conversion.setRate(new BigDecimal("0.725"));
        conversion.setRateSource("POLICY");
        conversion.setRatePolicyId(UUID.fromString("00000000-0000-0000-0000-000000000706"));
        conversion.setRatePolicyName("2026 rate");
        conversion.setCalculatedAmount(new BigDecimal(calculated));
        conversion.setRoundedAmount(new BigDecimal(rounded));
        conversion.setRoundingMode("HALF_UP");
        conversion.setTripOrigin("HQ");
        conversion.setTripDestination("Client site");
        conversion.setTripPurpose("Install support");
        conversion.setOdometerStart(new BigDecimal("1200"));
        conversion.setOdometerEnd(new BigDecimal("1225"));
        conversion.setPolicyExceptionReason("Storm detour");
        return conversion;
    }
}
