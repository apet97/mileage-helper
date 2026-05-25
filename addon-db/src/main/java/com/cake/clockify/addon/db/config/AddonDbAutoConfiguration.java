package com.cake.clockify.addon.db.config;

import com.cake.clockify.addon.core.config.AddonCoreAutoConfiguration;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.core.webhook.WebhookTokenLookup;
import com.cake.clockify.addon.db.lifecycle.JpaPersistenceLifecycleHandler;
import com.cake.clockify.addon.db.repository.AddonInstallationRepository;
import com.cake.clockify.addon.db.repository.AddonWebhookEventRepository;
import com.cake.clockify.addon.db.repository.AddonWebhookTokenRepository;
import com.cake.clockify.addon.db.repository.AddonWorkspaceSettingRepository;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.db.service.AddonSettingsService;
import com.cake.clockify.addon.db.service.AddonWebhookEventService;
import com.cake.clockify.addon.db.service.ClockifyClientFactory;
import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.sql.DataSource;

@AutoConfiguration(
        after = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class},
        before = {AddonCoreAutoConfiguration.class}
)
@ConditionalOnBean(DataSource.class)
@EntityScan(basePackages = "com.cake.clockify.addon.db.entity")
@EnableJpaRepositories(basePackages = "com.cake.clockify.addon.db.repository")
public class AddonDbAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JpaPersistenceLifecycleHandler jpaPersistenceLifecycleHandler(
            AddonInstallationService installationService,
            AddonWebhookTokenRepository webhookTokenRepo,
            TokenCodec codec,
            ClockifyAddonProperties props,
            AddonSettingsService settingsService,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<ClockifyManifest> manifestProvider) {
        return new JpaPersistenceLifecycleHandler(
                installationService,
                webhookTokenRepo,
                codec,
                props,
                settingsService,
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                manifestProvider.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public AddonInstallationService addonInstallationService(
            AddonInstallationRepository repository,
            TokenCodec tokenCodec) {
        return new AddonInstallationService(repository, tokenCodec);
    }

    @Bean
    @ConditionalOnMissingBean
    public AddonSettingsService addonSettingsService(
            AddonWorkspaceSettingRepository repository,
            ClockifyAddonProperties properties,
            TokenCodec tokenCodec) {
        return new AddonSettingsService(repository, properties, tokenCodec);
    }

    @Bean
    @ConditionalOnMissingBean
    public AddonWebhookEventService addonWebhookEventService(
            AddonWebhookEventRepository repository) {
        return new AddonWebhookEventService(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookTokenLookup webhookTokenLookup(
            AddonWebhookTokenRepository repository) {
        return new JpaWebhookTokenLookup(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClockifyClientFactory clockifyClientFactory(
            AddonInstallationService installationService) {
        return new ClockifyClientFactory(installationService);
    }
}
