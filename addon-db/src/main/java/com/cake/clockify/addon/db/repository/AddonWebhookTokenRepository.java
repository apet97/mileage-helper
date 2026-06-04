package com.cake.clockify.addon.db.repository;

import com.cake.clockify.addon.db.entity.AddonWebhookToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AddonWebhookTokenRepository extends JpaRepository<AddonWebhookToken, Long> {

    Optional<AddonWebhookToken> findByWorkspaceIdAndAddonKeyAndPath(
            String workspaceId, String addonKey, String path);

    Optional<AddonWebhookToken> findByWorkspaceIdAndAddonIdAndPath(
            String workspaceId, String addonId, String path);

    java.util.List<AddonWebhookToken> findAllByWorkspaceIdAndAddonKey(String workspaceId, String addonKey);

    @Modifying
    @Query(value = """
            DELETE FROM {h-schema}addon_webhook_tokens
             WHERE workspace_id = :workspaceId
               AND addon_key = :addonKey
            """, nativeQuery = true)
    int deleteAllByWorkspaceIdAndAddonKeyBulk(
            @Param("workspaceId") String workspaceId,
            @Param("addonKey") String addonKey);
}
