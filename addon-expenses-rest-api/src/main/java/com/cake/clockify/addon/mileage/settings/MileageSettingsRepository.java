package com.cake.clockify.addon.mileage.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MileageSettingsRepository extends JpaRepository<MileageWorkspaceSettings, String> {
}
