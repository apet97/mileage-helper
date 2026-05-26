package com.cake.clockify.client.spring;

import com.cake.clockify.client.ClockifyClient;
import com.cake.clockify.client.ClockifyClientBuilder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.net.URI;
import java.time.Duration;

@AutoConfiguration
@EnableConfigurationProperties(ClockifyAutoConfiguration.ClockifyProperties.class)
public class ClockifyAutoConfiguration {

    @Bean
    @Conditional(ClockifyClientConfiguredCondition.class)
    @ConditionalOnMissingBean
    public ClockifyClient clockifyClient(ClockifyProperties properties) {
        ClockifyClientBuilder builder = new ClockifyClientBuilder();

        if (properties.getAddonToken() != null && !properties.getAddonToken().isBlank()) {
            builder.addonToken(properties.getAddonToken());
        } else if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.apiKey(properties.getApiKey());
        }

        if (properties.getWorkspaceId() != null && !properties.getWorkspaceId().isBlank()) {
            builder.workspaceId(properties.getWorkspaceId());
        }

        if (properties.getBackendBaseUrl() != null) {
            builder.backendBaseUrl(properties.getBackendBaseUrl());
        }
        if (properties.getReportsBaseUrl() != null) {
            builder.reportsBaseUrl(properties.getReportsBaseUrl());
        }

        if (properties.getRequestTimeout() != null) {
            builder.requestTimeout(properties.getRequestTimeout());
        }
        if (properties.getMaxResponseBytes() != null) {
            builder.maxResponseBytes(properties.getMaxResponseBytes());
        }
        builder.followRedirects(properties.isFollowRedirects());
        builder.allowCrossHostRedirects(properties.isAllowCrossHostRedirects());

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public com.cake.clockify.client.auth.ClockifyTokenVerifier clockifyTokenVerifier(ClockifyProperties properties) {
        if (properties.getVerifierPublicKeyPem() != null && !properties.getVerifierPublicKeyPem().isBlank()) {
            return new com.cake.clockify.client.auth.ClockifyTokenVerifier(properties.getVerifierPublicKeyPem());
        }
        return new com.cake.clockify.client.auth.ClockifyTokenVerifier();
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(org.springframework.web.bind.annotation.RestControllerAdvice.class)
    static class WebExceptionConfiguration {
        @Bean
        @ConditionalOnMissingBean
        public ClockifyGlobalExceptionHandler clockifyGlobalExceptionHandler() {
            return new ClockifyGlobalExceptionHandler();
        }
    }

    static final class ClockifyClientConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String apiKey = firstText(
                    context.getEnvironment().getProperty("clockify.api-key"),
                    context.getEnvironment().getProperty("clockify.apiKey")
            );
            String addonToken = firstText(
                    context.getEnvironment().getProperty("clockify.addon-token"),
                    context.getEnvironment().getProperty("clockify.addonToken")
            );
            String backendBaseUrl = firstText(
                    context.getEnvironment().getProperty("clockify.backend-base-url"),
                    context.getEnvironment().getProperty("clockify.backendBaseUrl")
            );
            return hasText(backendBaseUrl) && (hasText(apiKey) || hasText(addonToken));
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }

        private static String firstText(String first, String second) {
            return hasText(first) ? first : second;
        }
    }

    @ConfigurationProperties(prefix = "clockify")
    public static class ClockifyProperties {
        private String apiKey;
        private String addonToken;
        private String workspaceId;
        private URI backendBaseUrl;
        private URI reportsBaseUrl;
        private Duration requestTimeout = Duration.ofSeconds(30);
        private Integer maxResponseBytes = 10 * 1024 * 1024;
        private boolean followRedirects = false;
        private boolean allowCrossHostRedirects = false;
        private String verifierPublicKeyPem;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getAddonToken() {
            return addonToken;
        }

        public void setAddonToken(String addonToken) {
            this.addonToken = addonToken;
        }

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }

        public URI getBackendBaseUrl() {
            return backendBaseUrl;
        }

        public void setBackendBaseUrl(URI backendBaseUrl) {
            this.backendBaseUrl = backendBaseUrl;
        }

        public URI getReportsBaseUrl() {
            return reportsBaseUrl;
        }

        public void setReportsBaseUrl(URI reportsBaseUrl) {
            this.reportsBaseUrl = reportsBaseUrl;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        public Integer getMaxResponseBytes() {
            return maxResponseBytes;
        }

        public void setMaxResponseBytes(Integer maxResponseBytes) {
            this.maxResponseBytes = maxResponseBytes;
        }

        public boolean isFollowRedirects() {
            return followRedirects;
        }

        public void setFollowRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
        }

        public boolean isAllowCrossHostRedirects() {
            return allowCrossHostRedirects;
        }

        public void setAllowCrossHostRedirects(boolean allowCrossHostRedirects) {
            this.allowCrossHostRedirects = allowCrossHostRedirects;
        }

        public String getVerifierPublicKeyPem() {
            return verifierPublicKeyPem;
        }

        public void setVerifierPublicKeyPem(String verifierPublicKeyPem) {
            this.verifierPublicKeyPem = verifierPublicKeyPem;
        }
    }
}
