package com.cake.clockify.addon.mileage.worker;

import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseSnapshot;
import com.cake.clockify.addon.mileage.clockify.UpdateFlatExpenseCommand;
import com.cake.clockify.addon.mileage.note.MileageNoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Deferred, off-request-thread reconcile of the Clockify "(Clockify category charge: X)" note parenthetical
 * for ADD-ON-CREATED mileage expenses.
 *
 * <p>WHY THIS EXISTS / WHY IT IS NOT DONE SYNCHRONOUSLY: stamping the charge requires reading the
 * Clockify-computed expense {@code total} and writing the note back. Doing that synchronously inside the
 * create request (a second updateFlatExpense to the just-created expense) races Clockify's own
 * EXPENSE_CREATED webhook and proved unreliable in production (the write hung; the create stalled ~20s).
 * This sweeper runs on the scheduler thread, ONLY for rows whose conversion settled at least
 * {@link #SETTLE} ago, so the webhook race is over and Clockify has computed the total — exactly the
 * conditions under which the native-conversion update path is reliable.
 *
 * <p>Idempotent and safe to re-run: it skips rows already carrying the parenthetical, stamps
 * {@code note_charge_reconciled_at} after handling a row so it is not reconsidered, and only ever issues an
 * update when the live note actually changes.
 */
public class MileageNoteReconcileWorker {

    private static final Logger log = LoggerFactory.getLogger(MileageNoteReconcileWorker.class);

    /** Skip rows newer than this: give Clockify time to settle and the EXPENSE_CREATED webhook race to pass. */
    private static final Duration SETTLE = Duration.ofSeconds(30);
    /** Stop trying after this: if not reconciled within the window, give up (cosmetic; do not retry forever). */
    private static final Duration MAX_AGE = Duration.ofMinutes(15);

    private final MileageConversionRepository conversionRepository;
    private final ClockifyExpenseGateway gateway;
    private final MileageNoteService noteService;
    private final Clock clock;

    public MileageNoteReconcileWorker(
            MileageConversionRepository conversionRepository,
            ClockifyExpenseGateway gateway,
            MileageNoteService noteService,
            Clock clock) {
        this.conversionRepository = conversionRepository;
        this.gateway = gateway;
        this.noteService = noteService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${mileage.worker.note-reconcile-poll-delay-ms:60000}")
    public void reconcilePendingNotes() {
        Instant now = clock.instant();
        List<MileageConversion> candidates =
                conversionRepository.findTop50BySourceAndStatusAndNoteChargeReconciledAtIsNullAndConvertedAtBetweenOrderByConvertedAtAsc(
                        MileageConversionSource.ADDON_FORM,
                        MileageConversionStatus.CONVERTED,
                        now.minus(MAX_AGE),
                        now.minus(SETTLE));
        for (MileageConversion conversion : candidates) {
            try {
                reconcileOne(conversion, now);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Transient (Clockify outage, lock): leave note_charge_reconciled_at NULL so the next cycle
                // retries until the row ages past MAX_AGE. Never log tokens or upstream bodies.
                log.warn("Note-charge reconcile failed for expense {} (will retry): {}",
                        conversion.getExpenseId(), e.toString());
            }
        }
    }

    private void reconcileOne(MileageConversion conversion, Instant now) throws Exception {
        // Only the single Mileage UNIT category is supported (the current product). Skip anything else.
        if (!Objects.equals(conversion.getSourceCategoryId(), conversion.getTargetCategoryId())) {
            stampDone(conversion, now);
            return;
        }
        ClockifyExpenseSnapshot snapshot = gateway.getExpense(conversion.getWorkspaceId(), conversion.getExpenseId());
        if (snapshot == null || snapshot.total() == null || snapshot.notes() == null) {
            // total not yet available — leave unstamped, retry next cycle.
            return;
        }
        BigDecimal categoryCharge = snapshot.total().movePointLeft(2);
        String newNote = noteService.insertCategoryCharge(snapshot.notes(), categoryCharge, conversion.getRoundedAmount());
        if (!newNote.equals(snapshot.notes())) {
            gateway.updateFlatExpense(conversion.getWorkspaceId(), conversion.getExpenseId(),
                    new UpdateFlatExpenseCommand(
                            conversion.getTargetCategoryId(),
                            conversion.getUserId(),
                            conversion.getExpenseDate() == null ? snapshot.date() : conversion.getExpenseDate().toString(),
                            conversion.getProjectId(),
                            conversion.getTaskId(),
                            snapshot.billable(),
                            conversion.getMiles(),
                            newNote,
                            roundingMode(conversion),
                            true));
            log.info("Stamped Clockify category charge on add-on expense {}", conversion.getExpenseId());
        }
        stampDone(conversion, now);
    }

    private void stampDone(MileageConversion conversion, Instant now) {
        conversion.setNoteChargeReconciledAt(now);
        conversionRepository.save(conversion);
    }

    private static RoundingMode roundingMode(MileageConversion conversion) {
        String mode = conversion.getRoundingMode();
        if (mode == null || mode.isBlank()) {
            return RoundingMode.HALF_UP;
        }
        try {
            return RoundingMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return RoundingMode.HALF_UP;
        }
    }
}
