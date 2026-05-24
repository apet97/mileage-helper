package com.cake.clockify.addon.db.service;

import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.db.entity.AddonWorkspaceSetting;
import com.cake.clockify.addon.db.repository.AddonWorkspaceSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;

/**
 * Service to manage add-on settings for workspaces.
 */
@Service
public class AddonSettingsService {

    private final AddonWorkspaceSettingRepository repository;
    private final ClockifyAddonProperties properties;
    private final TokenCodec tokenCodec;

    public AddonSettingsService(
            AddonWorkspaceSettingRepository repository,
            ClockifyAddonProperties properties,
            TokenCodec tokenCodec) {
        this.repository = repository;
        this.properties = properties;
        this.tokenCodec = tokenCodec;
    }

    /**
     * Gets a setting value JSON by key using the configured add-on key.
     */
    public Optional<String> getSetting(String workspaceId, String settingKey) {
        return getSetting(properties.key(), workspaceId, settingKey);
    }

    /**
     * Gets a setting value as a boolean using the configured add-on key.
     */
    public boolean getSettingBoolean(String workspaceId, String settingKey, boolean defaultValue) {
        return getSetting(workspaceId, settingKey)
                .map(val -> {
                    String clean = val.trim();
                    if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() > 1) {
                        clean = clean.substring(1, clean.length() - 1);
                    }
                    return Boolean.parseBoolean(clean);
                })
                .orElse(defaultValue);
    }

    /**
     * Gets a setting value as an integer using the configured add-on key.
     */
    public Optional<Integer> getSettingInteger(String workspaceId, String settingKey) {
        return getSetting(workspaceId, settingKey)
                .map(val -> {
                    String clean = val.trim();
                    if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() > 1) {
                        clean = clean.substring(1, clean.length() - 1);
                    }
                    try {
                        return Integer.parseInt(clean);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                });
    }

    /**
     * Gets a setting value as a clean unquoted string using the configured add-on key.
     */
    public Optional<String> getSettingString(String workspaceId, String settingKey) {
        return getSetting(workspaceId, settingKey)
                .map(val -> {
                    String clean = val.trim();
                    if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() > 1) {
                        clean = clean.substring(1, clean.length() - 1);
                    }
                    return clean;
                });
    }

    /**
     * Gets a setting value as a clean unquoted string using the configured add-on key, or defaultValue if not present.
     */
    public String getSettingString(String workspaceId, String settingKey, String defaultValue) {
        return getSettingString(workspaceId, settingKey).orElse(defaultValue);
    }

    /**
     * Gets a setting value JSON by key.
     */
    public Optional<String> getSetting(String addonKey, String workspaceId, String settingKey) {
        return repository.findByAddonKeyAndWorkspaceIdAndSettingKey(addonKey, workspaceId, settingKey)
                .map(this::readValue);
    }

    /**
     * Idempotently saves or updates a setting key-value pair using the configured add-on key.
     */
    @Transactional
    public void putSetting(String workspaceId, String settingKey, String valueJson, String userId) {
        putSetting(properties.key(), workspaceId, settingKey, valueJson, null, userId);
    }

    /**
     * Idempotently saves or updates a setting key-value pair.
     */
    @Transactional
    public void putSetting(
            String addonKey,
            String workspaceId,
            String settingKey,
            String valueJson,
            String valueType,
            String userId) {
        
        AddonWorkspaceSetting setting = repository.findByAddonKeyAndWorkspaceIdAndSettingKey(addonKey, workspaceId, settingKey)
                .orElseGet(AddonWorkspaceSetting::new);
        
        setting.setAddonKey(addonKey);
        setting.setWorkspaceId(workspaceId);
        setting.setSettingKey(settingKey);
        writeValue(setting, valueJson);
        setting.setValueType(valueType);
        setting.setUpdatedByUserId(userId);
        
        repository.save(setting);
    }

    /**
     * Bulk upserts a map of settings using the configured add-on key.
     */
    @Transactional
    public void putSettings(String workspaceId, Map<String, String> settings, String userId) {
        putSettings(properties.key(), workspaceId, settings, userId);
    }

    /**
     * Bulk upserts a map of settings.
     */
    @Transactional
    public void putSettings(
            String addonKey,
            String workspaceId,
            Map<String, String> settings,
            String userId) {
        
        if (settings == null) {
            return;
        }
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            putSetting(addonKey, workspaceId, entry.getKey(), entry.getValue(), null, userId);
        }
    }

    /**
     * Retrieves all settings for a workspace as a key-value map using the configured add-on key.
     */
    public Map<String, String> getSettingsForWorkspace(String workspaceId) {
        return getSettingsForWorkspace(properties.key(), workspaceId);
    }

    /**
     * Retrieves all settings for a workspace as a key-value map.
     */
    public Map<String, String> getSettingsForWorkspace(String addonKey, String workspaceId) {
        List<AddonWorkspaceSetting> settings = repository.findAllByAddonKeyAndWorkspaceId(addonKey, workspaceId);
        return settings.stream()
                .collect(Collectors.toMap(
                        AddonWorkspaceSetting::getSettingKey,
                        setting -> readValue(setting),
                        (v1, v2) -> v1 // handle duplicates if they occur, though unique constraint prevents this
                ));
    }

    private void writeValue(AddonWorkspaceSetting setting, String valueJson) {
        if (valueJson == null) {
            setting.setValueJson(null);
            setting.setValueKeyId(null);
            return;
        }
        TokenCodec.Encrypted encrypted = tokenCodec.encrypt(valueJson);
        setting.setValueJson(Base64.getEncoder().encodeToString(encrypted.cipher()));
        setting.setValueKeyId(encrypted.keyId());
    }

    private String readValue(AddonWorkspaceSetting setting) {
        String value = setting.getValueJson();
        if (value == null) {
            return "";
        }
        if (setting.getValueKeyId() == null || setting.getValueKeyId().isBlank()) {
            return value;
        }
        byte[] cipher = Base64.getDecoder().decode(value);
        return tokenCodec.decrypt(setting.getValueKeyId(), cipher);
    }
}
