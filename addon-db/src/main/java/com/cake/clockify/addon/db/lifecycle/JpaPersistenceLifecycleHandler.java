package com.cake.clockify.addon.db.lifecycle;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.core.lifecycle.AddonLifecycleHandler;
import com.cake.clockify.addon.db.entity.AddonWebhookToken;
import com.cake.clockify.addon.db.repository.AddonWebhookTokenRepository;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.db.service.AddonSettingsService;
import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Automatically persists the {@code INSTALLED} payload (including the auth token and webhook tokens)
 * and cleans up on {@code DELETED}. Runs before any custom {@link AddonLifecycleHandler} implementations.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JpaPersistenceLifecycleHandler implements AddonLifecycleHandler {

    private static final Logger log = LoggerFactory.getLogger(JpaPersistenceLifecycleHandler.class);
    private static final String WEBHOOK_PATH_PREFIX = "/webhook";

    private final AddonInstallationService installationService;
    private final AddonWebhookTokenRepository webhookTokenRepo;
    private final TokenCodec codec;
    private final ClockifyAddonProperties props;
    private final AddonSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final Map<String, String> manifestWebhookEventsByPath;

    public JpaPersistenceLifecycleHandler(
            AddonInstallationService installationService,
            AddonWebhookTokenRepository webhookTokenRepo,
            TokenCodec codec,
            ClockifyAddonProperties props,
            AddonSettingsService settingsService,
            ObjectMapper objectMapper) {
        this(installationService, webhookTokenRepo, codec, props, settingsService, objectMapper, null);
    }

    public JpaPersistenceLifecycleHandler(
            AddonInstallationService installationService,
            AddonWebhookTokenRepository webhookTokenRepo,
            TokenCodec codec,
            ClockifyAddonProperties props,
            AddonSettingsService settingsService,
            ObjectMapper objectMapper,
            ClockifyManifest manifest) {
        this.installationService = installationService;
        this.webhookTokenRepo = webhookTokenRepo;
        this.codec = codec;
        this.props = props;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.manifestWebhookEventsByPath = webhookEventsByPath(manifest);
    }

    @Override
    @Transactional
    public void onInstalled(NormalizedClaims claims, Map<String, Object> payload) {
        String workspaceId = claims.workspaceId();
        String authToken = stringValue(payload.get("authToken"));
        if (authToken == null) {
            log.warn("INSTALLED payload missing authToken for workspace {}", workspaceId);
            return;
        }

        installationService.markInstalled(
                workspaceId,
                props.key(),
                claims.addonId(),
                authToken,
                claims.backendUrl(),
                claims.reportsUrl(),
                claims.userId());

        // Persist per-webhook auth tokens from the INSTALLED payload.
        Object rawWebhooks = payload.get("webhooks");
        List<?> webhooks = rawWebhooks instanceof List<?> list ? list : List.of();
        java.util.Set<String> activePaths = new java.util.HashSet<>();
        for (Object item : webhooks) {
            if (!(item instanceof Map<?, ?> wh)) {
                continue;
            }
            String path = stringValue(wh.get("path"));
            String explicitEventType = firstString(wh, "webhookEvent", "eventType", "event");
            String webhookToken = stringValue(wh.get("authToken"));
            if (path == null || webhookToken == null) continue;

            String normalizedPath = normalizeWebhookPath(path);
            if (normalizedPath == null) continue;
            String eventType = manifestWebhookEventsByPath.get(normalizedPath);
            if (eventType == null && explicitEventType != null) {
                eventType = explicitEventType.toUpperCase(Locale.ROOT);
            }

            activePaths.add(normalizedPath);

            TokenCodec.Encrypted whEnc = codec.encrypt(webhookToken);
            AddonWebhookToken wat = webhookTokenRepo
                    .findByWorkspaceIdAndAddonKeyAndPath(workspaceId, props.key(), normalizedPath)
                    .orElseGet(AddonWebhookToken::new);
            wat.setWorkspaceId(workspaceId);
            wat.setAddonKey(props.key());
            wat.setAddonId(claims.addonId());
            wat.setPath(normalizedPath);
            wat.setEventType(eventType != null ? eventType : "");
            wat.setWebhookTokenEnc(Base64.getEncoder().encodeToString(whEnc.cipher()));
            wat.setTokenKeyId(whEnc.keyId());
            webhookTokenRepo.save(wat);
        }

        // Delete orphaned webhook tokens for this installation that are no longer present
        List<AddonWebhookToken> existingTokens = webhookTokenRepo.findAllByWorkspaceIdAndAddonKey(workspaceId, props.key());
        for (AddonWebhookToken token : existingTokens) {
            if (!activePaths.contains(token.getPath())) {
                webhookTokenRepo.delete(token);
            }
        }
        log.debug("Auto-persisted installation and webhook tokens for workspace {}", workspaceId);
    }

    @Override
    @Transactional
    public void onDeleted(NormalizedClaims claims) {
        String workspaceId = claims.workspaceId();
        installationService.markUninstalled(workspaceId);
        webhookTokenRepo.deleteAllByWorkspaceIdAndAddonKey(workspaceId, props.key());
        log.debug("Auto-deleted installation and webhook tokens for workspace {}", workspaceId);
    }

    @Override
    @Transactional
    public void onSettingsUpdated(NormalizedClaims claims, List<Map<String, Object>> updates) {
        String workspaceId = claims.workspaceId();
        if (updates == null || updates.isEmpty()) {
            return;
        }
        for (Map<String, Object> update : updates) {
            String settingKey = stringValue(update.get("id"));
            Object value = update.get("value");
            if (settingKey == null) continue;

            String valueJson;
            String valueType = value != null ? value.getClass().getSimpleName() : "null";
            try {
                valueJson = objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                valueJson = value != null ? value.toString() : "null";
            }

            settingsService.putSetting(
                    props.key(),
                    workspaceId,
                    settingKey,
                    valueJson,
                    valueType,
                    claims.userId()
            );
        }
        log.debug("Auto-persisted settings updates for workspace {}", workspaceId);
    }

    private static String stringValue(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static String firstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            String value = stringValue(map.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Map<String, String> webhookEventsByPath(ClockifyManifest manifest) {
        if (manifest == null || manifest.getWebhooks() == null) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        for (Object item : manifest.getWebhooks()) {
            if (item == null) {
                continue;
            }
            Map<String, Object> webhook = objectMapper.convertValue(item, new TypeReference<>() {});
            String path = stringValue(webhook.get("path"));
            String event = firstString(webhook, "event", "webhookEvent", "eventType");
            String normalizedPath = normalizeWebhookPath(path);
            if (normalizedPath != null && event != null) {
                out.put(normalizedPath, event.toUpperCase(Locale.ROOT));
            }
        }
        return Map.copyOf(out);
    }

    private static String normalizeWebhookPath(String rawPath) {
        String path = rawPath == null ? null : rawPath.trim();
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(path);
            if (uri.getScheme() != null && uri.getHost() != null) {
                path = uri.getRawPath();
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (path == null || path.isBlank()) {
            path = "/";
        }
        path = path.replaceAll("/+$", "");
        if (path.isEmpty()) {
            path = "/";
        }
        if (path.equals(WEBHOOK_PATH_PREFIX)) {
            return "/";
        }
        if (path.startsWith(WEBHOOK_PATH_PREFIX + "/")) {
            path = path.substring(WEBHOOK_PATH_PREFIX.length());
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
