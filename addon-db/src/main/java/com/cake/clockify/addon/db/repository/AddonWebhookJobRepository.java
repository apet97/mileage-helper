package com.cake.clockify.addon.db.repository;

import com.cake.clockify.addon.db.entity.AddonWebhookJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AddonWebhookJobRepository extends JpaRepository<AddonWebhookJob, UUID> {

    /**
     * Postgres FOR UPDATE SKIP LOCKED — claims up to {@code limit} pending rows,
     * skipping any rows already locked by a concurrent worker. The {@code {h-schema}}
     * placeholder is rewritten by Hibernate to whatever {@code default_schema} the
     * persistence unit is configured with.
     */
    @Query(value = """
            SELECT * FROM {h-schema}addon_webhook_jobs
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<AddonWebhookJob> findPendingForUpdateSkipLocked(@Param("limit") int limit);

    /**
     * Re-queues jobs that were claimed but never completed (worker crash / pod kill).
     */
    @Modifying
    @Query(value = """
            UPDATE {h-schema}addon_webhook_jobs
               SET status     = 'PENDING',
                   claimed_at = NULL,
                   updated_at = NOW()
             WHERE status     = 'CLAIMED'
               AND claimed_at < :cutoff
            """, nativeQuery = true)
    int resetStuckJobs(@Param("cutoff") Instant cutoff);

    long countByStatus(String status);
}
