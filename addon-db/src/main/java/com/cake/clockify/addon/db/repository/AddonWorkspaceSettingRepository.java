package com.cake.clockify.addon.db.repository;

import com.cake.clockify.addon.db.entity.AddonWorkspaceSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddonWorkspaceSettingRepository extends JpaRepository<AddonWorkspaceSetting, UUID> {

    Optional<AddonWorkspaceSetting> findByAddonKeyAndWorkspaceIdAndSettingKey(
            String addonKey, String workspaceId, String settingKey);

    List<AddonWorkspaceSetting> findAllByAddonKeyAndWorkspaceId(String addonKey, String workspaceId);

    void deleteByAddonKeyAndWorkspaceIdAndSettingKey(String addonKey, String workspaceId, String settingKey);
}
