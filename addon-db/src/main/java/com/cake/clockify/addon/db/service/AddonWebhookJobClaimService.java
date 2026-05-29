package com.cake.clockify.addon.db.service;

import com.cake.clockify.addon.db.entity.AddonWebhookJob;
import com.cake.clockify.addon.db.repository.AddonWebhookJobRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Worker-side operations on the {@code addon_webhook_jobs} queue. The transactional
 * boundaries here matter: the {@code SELECT … FOR UPDATE SKIP LOCKED} and the UPDATE
 * that flips status to CLAIMED must commit together so the lock is released only after
 * other workers can see the new status.
 *
 * <p>Processing the claimed job (calling the handler, talking to Clockify) happens
 * OUTSIDE this service so we never hold a database lock across a network call.
 */
public class AddonWebhookJobClaimService {

    private final AddonWebhookJobRepository repository;

    public AddonWebhookJobClaimService(AddonWebhookJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<AddonWebhookJob> claimNext() {
        List<AddonWebhookJob> rows = repository.findPendingForUpdateSkipLocked(1);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        AddonWebhookJob job = rows.get(0);
        job.setStatus(AddonWebhookJob.STATUS_CLAIMED);
        job.setClaimedAt(Instant.now());
        job.setAttempts(job.getAttempts() + 1);
        return Optional.of(repository.save(job));
    }

    @Transactional
    public void markCompleted(UUID jobId, String status, String lastError) {
        repository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setCompletedAt(Instant.now());
            job.setLastError(lastError);
            repository.save(job);
        });
    }

    @Transactional
    public int resetStuckJobs(Instant cutoff) {
        return repository.resetStuckJobs(cutoff);
    }

    public long countPending() {
        return repository.countByStatus(AddonWebhookJob.STATUS_PENDING);
    }
}
