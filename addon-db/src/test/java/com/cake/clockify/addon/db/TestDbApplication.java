package com.cake.clockify.addon.db;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.cake.clockify.addon.core.config.AddonCoreAutoConfiguration;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.cake.clockify.addon.core.crypto.TokenCodec;

@SpringBootApplication(exclude = AddonCoreAutoConfiguration.class)
public class TestDbApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestDbApplication.class, args);
    }

    @Bean
    public TokenCodec tokenCodec() {
        return new TokenCodec(
                java.util.Map.of("k1", "00000000000000000000000000000000000000000000000000000000000000aa"),
                "k1"
        );
    }

    @Bean
    public ClockifyAddonProperties clockifyAddonProperties() {
        return new ClockifyAddonProperties(
                "test-addon",
                "Test Addon",
                "https://test.local",
                "A test addon",
                true,
                "",
                "",
                new ClockifyAddonProperties.Crypto(
                        java.util.Map.of("k1", "00000000000000000000000000000000000000000000000000000000000000aa"),
                        "k1"
                )
        );
    }
}
