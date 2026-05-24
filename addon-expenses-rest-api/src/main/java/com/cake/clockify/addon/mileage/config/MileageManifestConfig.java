package com.cake.clockify.addon.mileage.config;

import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MileageManifestConfig {
    @Bean
    public ClockifyManifest clockifyManifest(ClockifyAddonProperties props) {
        return MileageManifestV15.from(props);
    }
}
