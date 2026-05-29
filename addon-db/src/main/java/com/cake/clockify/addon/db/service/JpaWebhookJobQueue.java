package com.cake.clockify.addon.db.service;

import com.cake.clockify.addon.core.webhook.WebhookJobQueue;
import com.cake.clockify.addon.db.entity.AddonWebhookJob;
import com.cake.clockify.addon.db.repository.AddonWebhookJobRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed {@link WebhookJobQueue}. Writes a PENDING row that a worker will later
 * claim with {@code SELECT … FOR UPDATE SKIP LOCKED}.
 */
public class JpaWebhookJobQueue implements WebhookJobQueue {

    private final AddonWebhookJobRepository repository;

    public JpaWebhookJobQueue(AddonWebhookJobRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void enqueue(EnqueueRequest request) {
        AddonWebhookJob job = new AddonWebhookJob();
        job.setAddonKey(request.addonKey());
        job.setWorkspaceId(request.workspaceId());
        job.setEventType(request.eventType());
        job.setEventId(request.eventId());
        job.setDedupeKey(request.dedupeKey());
        job.setPayload(request.payload());
        job.setClaimsJson(request.claimsJson());
        job.setStatus(AddonWebhookJob.STATUS_PENDING);
        repository.save(job);
    }
}
