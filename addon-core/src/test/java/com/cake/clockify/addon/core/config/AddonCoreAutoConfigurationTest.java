package com.cake.clockify.addon.core.config;

import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import com.cake.clockify.addon.core.auth.filter.ClockifyIframeAuthFilter;
import com.cake.clockify.addon.core.auth.filter.ClockifyLifecycleAuthFilter;
import com.cake.clockify.addon.core.auth.filter.ClockifyWebhookAuthFilter;
import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.core.error.GlobalExceptionHandler;
import com.cake.clockify.addon.core.health.HealthController;
import com.cake.clockify.addon.core.lifecycle.LifecycleController;
import com.cake.clockify.addon.core.manifest.ManifestController;
import com.cake.clockify.addon.core.webhook.WebhookTokenLookup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CorsFilter;

import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

class AddonCoreAutoConfigurationTest {

    private static final String TEST_KEY_HEX =
            "00000000000000000000000000000000000000000000000000000000000000aa";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AddonCoreAutoConfiguration.class));

    @Configuration
    static class BaseTestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public ClockifyManifest clockifyManifest() {
            return ClockifyManifest.v1_3Builder()
                    .key("test-key")
                    .name("test")
                    .baseUrl("http://localhost")
                    .requireFreePlan()
                    .build();
        }
    }

    @Configuration
    static class WebhookEnabledConfig extends BaseTestConfig {
        @Bean
        public WebhookTokenLookup webhookTokenLookup() {
            return Mockito.mock(WebhookTokenLookup.class);
        }
    }

    @Test
    void registersExpectedBeansWithExplicitCryptoProperties() {
        this.contextRunner
                .withUserConfiguration(BaseTestConfig.class)
                .withPropertyValues(
                        "addon.key=my-key",
                        "addon.name=my-name",
                        "addon.base-url=https://my-addon.com",
                        "addon.crypto.active-key-id=k1",
                        "addon.crypto.keys.k1=" + TEST_KEY_HEX
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RSAPublicKey.class);
                    assertThat(context).hasSingleBean(ClockifySignatureParser.class);
                    assertThat(context).hasSingleBean(TokenCodec.class);
                    assertThat(context).hasSingleBean(ClockifyLifecycleAuthFilter.class);
                    assertThat(context).hasSingleBean(ClockifyIframeAuthFilter.class);
                    assertThat(context).doesNotHaveBean(ClockifyWebhookAuthFilter.class);
                    assertThat(context).hasBean("lifecycleAuthFilterRegistration");
                    assertThat(context).hasBean("iframeAuthFilterRegistration");
                    assertThat(context).hasBean("securityHeadersFilterRegistration");

                    assertThat(context).hasSingleBean(ManifestController.class);
                    assertThat(context).hasSingleBean(LifecycleController.class);
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(context).hasSingleBean(HealthController.class);
                });
    }

    @Test
    void failsStartupWhenCryptoKeysAreMissing() {
        this.contextRunner
                .withUserConfiguration(BaseTestConfig.class)
                .withPropertyValues(
                        "addon.key=my-key",
                        "addon.name=my-name",
                        "addon.base-url=https://my-addon.com"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "addon.crypto.keys and addon.crypto.active-key-id must be configured explicitly");
                });
    }

    @Test
    void registersWebhookAuthFilterWhenWebhookLookupPresent() {
        this.contextRunner
                .withUserConfiguration(WebhookEnabledConfig.class)
                .withPropertyValues(
                        "addon.key=my-key",
                        "addon.name=my-name",
                        "addon.base-url=https://my-addon.com",
                        "addon.crypto.active-key-id=k1",
                        "addon.crypto.keys.k1=" + TEST_KEY_HEX
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ClockifyWebhookAuthFilter.class);
                });
    }

    @Test
    void corsFilterAllowsClockifyIframeApiMethods() {
        this.contextRunner
                .withUserConfiguration(BaseTestConfig.class)
                .withPropertyValues(
                        "addon.key=my-key",
                        "addon.name=my-name",
                        "addon.base-url=https://my-addon.com",
                        "addon.crypto.active-key-id=k1",
                        "addon.crypto.keys.k1=" + TEST_KEY_HEX
                )
                .run(context -> {
                    CorsFilter filter = (CorsFilter) context
                            .getBean("addonCorsFilter", FilterRegistrationBean.class)
                            .getFilter();
                    org.springframework.mock.web.MockHttpServletRequest request =
                            new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/iframe/api/settings");
                    request.addHeader("Origin", "https://app.clockify.me");
                    request.addHeader("Access-Control-Request-Method", "PUT");
                    org.springframework.mock.web.MockHttpServletResponse response =
                            new org.springframework.mock.web.MockHttpServletResponse();

                    filter.doFilter(request, response, new org.springframework.mock.web.MockFilterChain());

                    assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("PUT", "PATCH", "DELETE");
                });
    }

    @Test
    void corsAllowedOriginPatternsAreTrimmedAndBlankEntriesIgnored() {
        this.contextRunner
                .withUserConfiguration(BaseTestConfig.class)
                .withPropertyValues(
                        "addon.key=my-key",
                        "addon.name=my-name",
                        "addon.base-url=https://my-addon.com",
                        "addon.cors-allowed-origin-patterns= https://app.clockify.me , https://developer.clockify.me ,, ",
                        "addon.crypto.active-key-id=k1",
                        "addon.crypto.keys.k1=" + TEST_KEY_HEX
                )
                .run(context -> {
                    CorsFilter filter = (CorsFilter) context
                            .getBean("addonCorsFilter", FilterRegistrationBean.class)
                            .getFilter();
                    org.springframework.mock.web.MockHttpServletRequest request =
                            new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/iframe/api/settings");
                    request.addHeader("Origin", "https://developer.clockify.me");
                    request.addHeader("Access-Control-Request-Method", "PUT");
                    org.springframework.mock.web.MockHttpServletResponse response =
                            new org.springframework.mock.web.MockHttpServletResponse();

                    filter.doFilter(request, response, new org.springframework.mock.web.MockFilterChain());

                    assertThat(response.getHeader("Access-Control-Allow-Origin"))
                            .isEqualTo("https://developer.clockify.me");
                });
    }

    @Test
    void filterRegistrationBeansDoNotBackOffEachOtherByType() {
        this.contextRunner
                .withUserConfiguration(BaseTestConfig.class)
                .withPropertyValues(
                        "addon.key=my-key",
                        "addon.name=my-name",
                        "addon.base-url=https://my-addon.com",
                        "addon.crypto.active-key-id=k1",
                        "addon.crypto.keys.k1=" + TEST_KEY_HEX
                )
                .run(context -> {
                    FilterRegistrationBean<?> lifecycle =
                            context.getBean("lifecycleAuthFilterRegistration", FilterRegistrationBean.class);
                    FilterRegistrationBean<?> iframe =
                            context.getBean("iframeAuthFilterRegistration", FilterRegistrationBean.class);
                    FilterRegistrationBean<?> security =
                            context.getBean("securityHeadersFilterRegistration", FilterRegistrationBean.class);

                    assertThat(lifecycle.getFilter()).isInstanceOf(ClockifyLifecycleAuthFilter.class);
                    assertThat(iframe.getFilter()).isInstanceOf(ClockifyIframeAuthFilter.class);
                    assertThat(iframe.getUrlPatterns()).containsExactlyInAnyOrder("/iframe/*", "/api/*");
                    assertThat(security.getFilter()).isInstanceOf(SecurityHeadersFilter.class);
                });
    }
}
