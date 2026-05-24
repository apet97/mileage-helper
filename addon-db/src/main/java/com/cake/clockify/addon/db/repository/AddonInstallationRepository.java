package com.cake.clockify.addon.db.repository;

import com.cake.clockify.addon.db.entity.AddonInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AddonInstallationRepository extends JpaRepository<AddonInstallation, String> {

    Optional<AddonInstallation> findByWorkspaceIdAndAddonKey(String workspaceId, String addonKey);

    boolean existsByWorkspaceIdAndAddonKey(String workspaceId, String addonKey);
}
