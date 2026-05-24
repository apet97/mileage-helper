package com.cake.clockify.addon.core.config;

import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import com.cake.clockify.addon.core.auth.PublicKeyParser;
import com.cake.clockify.addon.core.auth.filter.ClockifyIframeAuthFilter;
import com.cake.clockify.addon.core.auth.filter.ClockifyLifecycleAuthFilter;
import com.cake.clockify.addon.core.auth.filter.ClockifyWebhookAuthFilter;
import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.core.error.GlobalExceptionHandler;
import com.cake.clockify.addon.core.health.HealthController;
import com.cake.clockify.addon.core.lifecycle.AddonLifecycleHandler;
import com.cake.clockify.addon.core.lifecycle.LifecycleController;
import com.cake.clockify.addon.core.manifest.ManifestController;
import com.cake.clockify.addon.core.webhook.AddonWebhookHandler;
import com.cake.clockify.addon.core.webhook.WebhookController;
import com.cake.clockify.addon.core.webhook.WebhookEventService;
import com.cake.clockify.addon.core.webhook.WebhookTokenLookup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;

/**
 * Spring Boot auto-configuration for the Clockify add-on core.
 *
 * <p>Activated automatically for any Spring Boot application that has addon-core on the classpath.
 * Add-ons only need to provide:
 * <ul>
 *   <li>A {@link ClockifyManifest} bean (usually built in their own {@code @Configuration}).
 *   <li>One or more {@link AddonLifecycleHandler} components.
 *   <li>Properties under {@code addon.*} (key, name, baseUrl, description).
 * </ul>
 *
 * <p>All auth filters, manifest endpoint, lifecycle router, health endpoint, CORS, and security
 * headers are wired automatically from this configuration.
 */
@AutoConfiguration
@EnableConfigurationProperties(ClockifyAddonProperties.class)
public class AddonCoreAutoConfiguration {

    /** Clockify production RSA public key. Override by providing your own {@code RSAPublicKey} bean. */
    private static final String DEFAULT_CLOCKIFY_PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAubktufFNO/op+E5WBWL6
            /Y9QRZGSGGCsV00FmPRl5A0mSfQu3yq2Yaq47IlN0zgFy9IUG8/JJfwiehsmbrKa
            49t/xSkpG1u9w1GUyY0g4eKDUwofHKAt3IPw0St4qsWLK9mO+koUo56CGQOEpTui
            5bMfmefVBBfShXTaZOtXPB349FdzSuYlU/5o3L12zVWMutNhiJCKyGfsuu2uXa9+
            6uQnZBw1wO3/QEci7i4TbC+ZXqW1rCcbogSMORqHAP6qSAcTFRmrjFAEsOWiUUhZ
            rLDg2QJ8VTDghFnUhYklNTJlGgfo80qEWe1NLIwvZj0h3bWRfrqZHsD/Yjh0duk6
            yQIDAQAB
            -----END PUBLIC KEY-----
            """;

    @Bean
    @ConditionalOnMissingBean
    public RSAPublicKey clockifyPublicKey(ClockifyAddonProperties props) {
        return PublicKeyParser.parsePem(DEFAULT_CLOCKIFY_PUBLIC_KEY_PEM);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClockifySignatureParser clockifySignatureParser(
            ClockifyAddonProperties props, RSAPublicKey clockifyPublicKey) {
        return new ClockifySignatureParser(props.key(), clockifyPublicKey);
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenCodec addonTokenCodec(ClockifyAddonProperties props) {
        requireExplicitCryptoKeys(props);
        return new TokenCodec(props.crypto().keys(), props.crypto().activeKeyId());
    }

    private static void requireExplicitCryptoKeys(ClockifyAddonProperties props) {
        if (props.crypto() == null
                || props.crypto().keys() == null
                || props.crypto().keys().isEmpty()
                || props.crypto().activeKeyId() == null
                || props.crypto().activeKeyId().isBlank()) {
            throw new IllegalStateException(
                    "addon.crypto.keys and addon.crypto.active-key-id must be configured explicitly");
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public ClockifyLifecycleAuthFilter clockifyLifecycleAuthFilter(ClockifySignatureParser parser) {
        return new ClockifyLifecycleAuthFilter(parser);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClockifyIframeAuthFilter clockifyIframeAuthFilter(ClockifySignatureParser parser) {
        return new ClockifyIframeAuthFilter(parser);
    }

    @Bean
    @ConditionalOnBean(WebhookTokenLookup.class)
    @ConditionalOnMissingBean
    public ClockifyWebhookAuthFilter clockifyWebhookAuthFilter(
            ClockifySignatureParser parser,
            WebhookTokenLookup tokenLookup,
            TokenCodec tokenCodec,
            ClockifyAddonProperties props) {
        return new ClockifyWebhookAuthFilter(parser, tokenLookup, tokenCodec, props);
    }

    @Bean
    @ConditionalOnMissingBean(name = "lifecycleAuthFilterRegistration")
    public FilterRegistrationBean<ClockifyLifecycleAuthFilter> lifecycleAuthFilterRegistration(
            ClockifyLifecycleAuthFilter filter) {
        FilterRegistrationBean<ClockifyLifecycleAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/lifecycle/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean(name = "iframeAuthFilterRegistration")
    public FilterRegistrationBean<ClockifyIframeAuthFilter> iframeAuthFilterRegistration(
            ClockifyIframeAuthFilter filter) {
        FilterRegistrationBean<ClockifyIframeAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/iframe/*");
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }

    @Bean
    @ConditionalOnBean(ClockifyWebhookAuthFilter.class)
    @ConditionalOnMissingBean(name = "webhookAuthFilterRegistration")
    public FilterRegistrationBean<ClockifyWebhookAuthFilter> webhookAuthFilterRegistration(
            ClockifyWebhookAuthFilter filter) {
        FilterRegistrationBean<ClockifyWebhookAuthFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns("/webhook/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean(name = "securityHeadersFilterRegistration")
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration(
            ClockifyAddonProperties props) {
        FilterRegistrationBean<SecurityHeadersFilter> reg = new FilterRegistrationBean<>(new SecurityHeadersFilter(props));
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean(name = "addonCorsFilter")
    public FilterRegistrationBean<CorsFilter> addonCorsFilter(ClockifyAddonProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(props.corsAllowedOriginPatternList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Type", "Content-Length"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> reg = new FilterRegistrationBean<>(new CorsFilter(source));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean
    public ManifestController manifestController(ClockifyManifest manifest, ObjectMapper objectMapper) {
        return new ManifestController(manifest, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public LifecycleController lifecycleController(List<AddonLifecycleHandler> handlers, ObjectMapper objectMapper) {
        return new LifecycleController(handlers, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookController webhookController(
            List<AddonWebhookHandler> handlers,
            ClockifyAddonProperties properties,
            ObjectMapper objectMapper,
            @Autowired(required = false) WebhookEventService eventService) {
        return new WebhookController(handlers, properties, objectMapper, eventService);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public HealthController healthController() {
        return new HealthController();
    }

    /** Shared RestClient for Clockify backend API calls. Add-ons can override by defining their own bean. */
    @Bean(name = "clockifyRestClient")
    @ConditionalOnMissingBean(name = "clockifyRestClient")
    public RestClient clockifyRestClient() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    /** Shared RestClient for Clockify reports API (longer read timeout for large report payloads). */
    @Bean(name = "clockifyReportsRestClient")
    @ConditionalOnMissingBean(name = "clockifyReportsRestClient")
    public RestClient clockifyReportsRestClient() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
