package com.cake.clockify.addon.core.manifest;

import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManifestController.class)
class ManifestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class Config {
        @Bean
        public ClockifyManifest clockifyManifest() {
            return ClockifyManifest.v1_3Builder()
                    .key("test-key")
                    .name("Test Addon")
                    .baseUrl("https://test.com")
                    .requireFreePlan()
                    .build();
        }
    }

    @Test
    void getManifestReturns200AndValidJson() throws Exception {
        mockMvc.perform(get("/manifest"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.key").value("test-key"))
                .andExpect(jsonPath("$.name").value("Test Addon"))
                .andExpect(jsonPath("$.baseUrl").value("https://test.com"));
    }
}
