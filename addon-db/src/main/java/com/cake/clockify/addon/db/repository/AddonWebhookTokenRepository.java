package com.cake.clockify.addon.db.repository;

import com.cake.clockify.addon.db.entity.AddonWebhookToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AddonWebhookTokenRepository extends JpaRepository<AddonWebhookToken, Long> {

    Optional<AddonWebhookToken> findByWorkspaceIdAndAddonKeyAndPath(
            String workspaceId, String addonKey, String path);

    Optional<AddonWebhookToken> findByWorkspaceIdAndAddonIdAndPath(
            String workspaceId, String addonId, String path);

    java.util.List<AddonWebhookToken> findAllByWorkspaceIdAndAddonKey(String workspaceId, String addonKey);

    void deleteAllByWorkspaceIdAndAddonKey(String workspaceId, String addonKey);
}
