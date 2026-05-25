package com.cake.clockify.addon.db.repository;

import com.cake.clockify.addon.db.entity.AddonWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AddonWebhookEventRepository extends JpaRepository<AddonWebhookEvent, UUID> {

    Optional<AddonWebhookEvent> findByAddonKeyAndWorkspaceIdAndDedupeKey(
            String addonKey, String workspaceId, String dedupeKey);

    @Query(value = """
            INSERT INTO {h-schema}addon_webhook_events (
                id,
                addon_key,
                workspace_id,
                event_type,
                dedupe_key,
                payload_hash,
                status,
                received_at,
                retry_count,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :addonKey,
                :workspaceId,
                :eventType,
                :dedupeKey,
                :payloadHash,
                'RECEIVED',
                :now,
                0,
                :now,
                :now
            )
            ON CONFLICT (addon_key, workspace_id, dedupe_key)
            DO UPDATE SET
                event_type = CASE
                    WHEN addon_webhook_events.status = 'FAILED' THEN EXCLUDED.event_type
                    ELSE addon_webhook_events.event_type
                END,
                payload_hash = CASE
                    WHEN addon_webhook_events.status = 'FAILED' THEN EXCLUDED.payload_hash
                    ELSE addon_webhook_events.payload_hash
                END,
                status = CASE
                    WHEN addon_webhook_events.status = 'FAILED' THEN 'RECEIVED'
                    ELSE addon_webhook_events.status
                END,
                updated_at = CASE
                    WHEN addon_webhook_events.status = 'FAILED' THEN EXCLUDED.updated_at
                    ELSE addon_webhook_events.updated_at
                END
            RETURNING id
            """, nativeQuery = true)
    UUID upsertReceived(
            @Param("id") UUID id,
            @Param("addonKey") String addonKey,
            @Param("workspaceId") String workspaceId,
            @Param("eventType") String eventType,
            @Param("dedupeKey") String dedupeKey,
            @Param("payloadHash") String payloadHash,
            @Param("now") Instant now);

    @Modifying
    @Query("UPDATE AddonWebhookEvent e SET e.status = 'PROCESSING', e.updatedAt = :now WHERE e.id = :id AND e.status = 'RECEIVED'")
    int tryStartProcessing(@Param("id") UUID id, @Param("now") Instant now);
}
