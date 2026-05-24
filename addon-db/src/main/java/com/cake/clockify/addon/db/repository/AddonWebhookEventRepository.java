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

    @Modifying
    @Query("UPDATE AddonWebhookEvent e SET e.status = 'PROCESSING', e.updatedAt = :now WHERE e.id = :id AND e.status = 'RECEIVED'")
    int tryStartProcessing(@Param("id") UUID id, @Param("now") Instant now);
}
