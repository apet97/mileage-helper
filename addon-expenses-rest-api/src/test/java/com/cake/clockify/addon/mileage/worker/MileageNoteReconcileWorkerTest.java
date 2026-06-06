package com.cake.clockify.addon.mileage.worker;

import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseSnapshot;
import com.cake.clockify.addon.mileage.clockify.UpdateFlatExpenseCommand;
import com.cake.clockify.addon.mileage.note.MileageNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MileageNoteReconcileWorkerTest {
    private MileageConversionRepository repository;
    private ClockifyExpenseGateway gateway;
    private MileageNoteReconcileWorker worker;

    @BeforeEach
    void setUp() {
        repository = mock(MileageConversionRepository.class);
        gateway = mock(ClockifyExpenseGateway.class);
        worker = new MileageNoteReconcileWorker(
                repository, gateway, new MileageNoteService(),
                Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC));
        when(repository.save(any(MileageConversion.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void annotatesNoteWhenChargeDivergesThenStamps() throws Exception {
        MileageConversion c = conversion();
        when(repository.findTop50BySourceAndStatusAndNoteChargeReconciledAtIsNullAndConvertedAtBetweenOrderByConvertedAtAsc(
                eq(MileageConversionSource.ADDON_FORM), eq(MileageConversionStatus.CONVERTED), any(), any()))
                .thenReturn(List.of(c));
        // total 905 cents = 9.05, diverges from recorded 8.99 -> annotate
        when(gateway.getExpense("ws-1", "exp-1")).thenReturn(snapshot(
                "Mileage reimbursement: 12.4 miles x 0.725 = 8.99. Created/converted by Mileage for Clockify.",
                new BigDecimal("905")));

        worker.reconcilePendingNotes();

        ArgumentCaptor<UpdateFlatExpenseCommand> cmd = ArgumentCaptor.forClass(UpdateFlatExpenseCommand.class);
        verify(gateway).updateFlatExpense(eq("ws-1"), eq("exp-1"), cmd.capture());
        assertThat(cmd.getValue().notes()).contains("(Clockify category charge: 9.05)");
        assertThat(cmd.getValue().amount()).isEqualByComparingTo(new BigDecimal("12.4"));
        assertThat(cmd.getValue().amountIsQuantity()).isTrue();
        assertThat(c.getNoteChargeReconciledAt()).isNotNull();
    }

    @Test
    void stampsWithoutUpdateWhenChargeMatches() throws Exception {
        MileageConversion c = conversion();
        when(repository.findTop50BySourceAndStatusAndNoteChargeReconciledAtIsNullAndConvertedAtBetweenOrderByConvertedAtAsc(
                eq(MileageConversionSource.ADDON_FORM), eq(MileageConversionStatus.CONVERTED), any(), any()))
                .thenReturn(List.of(c));
        // total 899 cents = 8.99, equals recorded 8.99 -> no annotation needed
        when(gateway.getExpense("ws-1", "exp-1")).thenReturn(snapshot(
                "Mileage reimbursement: 12.4 miles x 0.725 = 8.99. Created/converted by Mileage for Clockify.",
                new BigDecimal("899")));

        worker.reconcilePendingNotes();

        verify(gateway, never()).updateFlatExpense(any(), any(), any());
        assertThat(c.getNoteChargeReconciledAt()).isNotNull();
    }

    @Test
    void doesNotStampWhenClockifyLookupFails() throws Exception {
        MileageConversion c = conversion();
        when(repository.findTop50BySourceAndStatusAndNoteChargeReconciledAtIsNullAndConvertedAtBetweenOrderByConvertedAtAsc(
                eq(MileageConversionSource.ADDON_FORM), eq(MileageConversionStatus.CONVERTED), any(), any()))
                .thenReturn(List.of(c));
        when(gateway.getExpense("ws-1", "exp-1")).thenThrow(new java.io.IOException("clockify down"));

        worker.reconcilePendingNotes();

        verify(gateway, never()).updateFlatExpense(any(), any(), any());
        assertThat(c.getNoteChargeReconciledAt()).isNull(); // retried next cycle
    }

    private static MileageConversion conversion() {
        MileageConversion c = new MileageConversion();
        c.setId(UUID.randomUUID());
        c.setWorkspaceId("ws-1");
        c.setExpenseId("exp-1");
        c.setSource(MileageConversionSource.ADDON_FORM);
        c.setStatus(MileageConversionStatus.CONVERTED);
        c.setUserId("user-1");
        c.setSourceCategoryId("cat-mileage");
        c.setTargetCategoryId("cat-mileage");
        c.setProjectId(null);
        c.setTaskId(null);
        c.setExpenseDate(LocalDate.parse("2026-06-06"));
        c.setMiles(new BigDecimal("12.4"));
        c.setRoundedAmount(new BigDecimal("8.99"));
        c.setRoundingMode("HALF_UP");
        return c;
    }

    private static ClockifyExpenseSnapshot snapshot(String notes, BigDecimal totalCents) {
        return new ClockifyExpenseSnapshot("exp-1", "ws-1", "user-1", "2026-06-06", null, null,
                "cat-mileage", notes, new BigDecimal("12.4"), Boolean.TRUE, "", totalCents, Boolean.FALSE);
    }
}
