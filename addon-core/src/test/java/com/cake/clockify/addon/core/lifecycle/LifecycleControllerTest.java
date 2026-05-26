package com.cake.clockify.addon.core.lifecycle;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LifecycleController.class)
class LifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AddonLifecycleHandler handler;

    private NormalizedClaims claims;

    @BeforeEach
    void setUp() {
        claims = new NormalizedClaims("ws1", "ad1", "https://api.clockify.me/api", "https://reports.api.clockify.me", "https://locations.api.clockify.me", "https://screenshots.api.clockify.me", "u1", "ADMIN", "en", "default", "UTC", java.time.Instant.now());
    }

    @Test
    @SuppressWarnings("unchecked")
    void postInstalledDispatchesToHandlerAndEnrichesClaims() throws Exception {
        NormalizedClaims enriched = new NormalizedClaims("ws1", "addon1", null, null, null, null, null, null, null, null, "tz", java.time.Instant.now());
        String payload = "{\"apiUrl\":\"https://api.clockify.me/api\", \"asUser\":\"user123\"}";
        mockMvc.perform(post("/lifecycle/installed")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, enriched)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<NormalizedClaims> claimsCaptor = ArgumentCaptor.forClass(NormalizedClaims.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(handler).onInstalled(claimsCaptor.capture(), payloadCaptor.capture());

        assertEquals("ws1", claimsCaptor.getValue().workspaceId());
        assertEquals("https://api.clockify.me/api", claimsCaptor.getValue().backendUrl());
        assertEquals("user123", claimsCaptor.getValue().userId());
        assertEquals("https://api.clockify.me/api", payloadCaptor.getValue().get("apiUrl"));
    }

    @Test
    void postDeletedDispatchesToHandler() throws Exception {
        mockMvc.perform(post("/lifecycle/deleted")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(handler).onDeleted(claims);
    }

    @Test
    void postDeletedStillAcknowledgesWhenHandlerFails() throws Exception {
        doThrow(new IllegalStateException("cleanup failed")).when(handler).onDeleted(claims);

        mockMvc.perform(post("/lifecycle/deleted")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @SuppressWarnings("unchecked")
    void postSettingsUpdatedDispatchesToHandler() throws Exception {
        String payload = "{\"settings\": [{\"id\":\"s1\", \"value\":\"val1\"}]}";
        mockMvc.perform(post("/lifecycle/settings-updated")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        ArgumentCaptor<List<Map<String, Object>>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(handler).onSettingsUpdated(eq(claims), listCaptor.capture());

        List<Map<String, Object>> updates = listCaptor.getValue();
        assertEquals(1, updates.size());
        assertEquals("s1", updates.get(0).get("id"));
        assertEquals("val1", updates.get(0).get("value"));
    }

    @Test
    void postStatusChangedDispatchesToHandler() throws Exception {
        String payload = "{\"status\": \"ACTIVE\"}";
        mockMvc.perform(post("/lifecycle/status-changed")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(handler).onStatusChanged(claims, "ACTIVE");
    }
}
