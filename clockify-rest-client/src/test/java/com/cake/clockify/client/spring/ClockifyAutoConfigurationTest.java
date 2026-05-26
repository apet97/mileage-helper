package com.cake.clockify.client.spring;

import com.cake.clockify.client.ClockifyClient;
import com.cake.clockify.client.auth.ClockifyTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ClockifyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ClockifyAutoConfiguration.class));

    @Test
    void shouldRegisterClockifyClientBeanWithProperties() {
        this.contextRunner
                .withPropertyValues(
                        "clockify.apiKey=test-api-key",
                        "clockify.workspaceId=test-workspace-id",
                        "clockify.backendBaseUrl=https://custom-api.clockify.me",
                        "clockify.reportsBaseUrl=https://custom-reports.clockify.me",
                        "clockify.requestTimeout=15s",
                        "clockify.maxResponseBytes=5242880",
                        "clockify.followRedirects=true",
                        "clockify.verifierPublicKeyPem=" + ClockifyTokenVerifier.DEFAULT_PUBLIC_KEY_PEM
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ClockifyClient.class);
                    assertThat(context).hasSingleBean(ClockifyTokenVerifier.class);
                    
                    ClockifyClient client = context.getBean(ClockifyClient.class);
                    assertThat(client).isNotNull();

                    // Indirectly verify config through properties
                    var config = client.config();
                    assertThat(config.workspaceId()).isEqualTo("test-workspace-id");
                    assertThat(config.backendBaseUrl()).isEqualTo(URI.create("https://custom-api.clockify.me"));
                    assertThat(config.reportsBaseUrl()).isEqualTo(URI.create("https://custom-reports.clockify.me"));
                    assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(15));
                    assertThat(config.maxResponseBytes()).isEqualTo(5242880);
                    assertThat(config.followRedirects()).isTrue();
                });
    }

    @Test
    void shouldNotRegisterClockifyClientBeanWithoutStaticCredentials() {
        this.contextRunner
                .withPropertyValues(
                        "clockify.api-key=",
                        "clockify.addon-token="
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ClockifyClient.class);
                    assertThat(context).hasSingleBean(ClockifyTokenVerifier.class);
                });
    }

    @Test
    void shouldNotRegisterClockifyClientBeanWithoutBackendBaseUrl() {
        this.contextRunner
                .withPropertyValues("clockify.addonToken=test-addon-token")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ClockifyClient.class);
                    assertThat(context).hasSingleBean(ClockifyTokenVerifier.class);
                });
    }

    @Test
    void shouldRegisterClockifyClientBeanWithAddonToken() {
        this.contextRunner
                .withPropertyValues(
                        "clockify.apiKey=",
                        "clockify.addonToken=test-addon-token",
                        "clockify.backendBaseUrl=https://custom-api.clockify.me",
                        "clockify.reportsBaseUrl=https://custom-reports.clockify.me"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ClockifyClient.class);
                    assertThat(context).hasSingleBean(ClockifyTokenVerifier.class);
                    
                    ClockifyClient client = context.getBean(ClockifyClient.class);
                    assertThat(client).isNotNull();
                    assertThat(client.config().authProvider().getClass().getSimpleName())
                            .isEqualTo("AddonTokenAuthProvider");
                });
    }

    @Test
    void addonTokenShouldTakePrecedenceWhenBothAuthValuesAreConfigured() {
        this.contextRunner
                .withPropertyValues(
                        "clockify.apiKey=test-api-key",
                        "clockify.addonToken=test-addon-token",
                        "clockify.backendBaseUrl=https://custom-api.clockify.me",
                        "clockify.reportsBaseUrl=https://custom-reports.clockify.me"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ClockifyClient.class);
                    ClockifyClient client = context.getBean(ClockifyClient.class);
                    assertThat(client.config().authProvider().getClass().getSimpleName())
                            .isEqualTo("AddonTokenAuthProvider");
                });
    }

}
