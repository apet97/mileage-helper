package com.cake.clockify.addon.mileage.metrics;

import com.cake.clockify.addon.db.repository.AddonWebhookTokenRepository;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.db.service.AddonSettingsService;
import com.cake.clockify.addon.db.service.ClockifyClientFactory;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionReservationRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.audit.MileageSkipReason;
import com.cake.clockify.addon.mileage.conversion.ConversionResult;
import com.cake.clockify.addon.mileage.policy.MileageRatePolicyRepository;
import com.cake.clockify.addon.mileage.settings.MileageSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "com.cake.clockify.addon.db.config.AddonDbAutoConfiguration",
        "addon.key=mileage-for-clockify",
        "addon.name=Mileage for Clockify",
        "addon.description=Create and convert precise mileage reimbursements into real Clockify flat expenses.",
        "addon.base-url=https://mileage.example.com",
        "addon.crypto.active-key-id=k1",
        "addon.crypto.keys.k1=00000000000000000000000000000000000000000000000000000000000000aa",
        "mileage.worker.enabled=false",
        "management.endpoints.web.exposure.include=health,info,prometheus"
})
@AutoConfigureMockMvc
class MileageActuatorPrometheusTest {

    @MockBean AddonWebhookTokenRepository webhookTokenRepository;
    @MockBean AddonSettingsService addonSettingsService;
    @MockBean ClockifyClientFactory clockifyClientFactory;
    @MockBean AddonInstallationService addonInstallationService;
    @MockBean MileageSettingsRepository mileageSettingsRepository;
    @MockBean MileageConversionRepository mileageConversionRepository;
    @MockBean MileageConversionReservationRepository mileageConversionReservationRepository;
    @MockBean MileageRatePolicyRepository mileageRatePolicyRepository;

    @Autowired MockMvc mockMvc;
    @Autowired MileageConversionMetrics conversionMetrics;

    @Test
    void prometheusEndpointExposesMileageConversionCounters() throws Exception {
        conversionMetrics.record(new ConversionResult(
                UUID.randomUUID(), "exp-1", MileageConversionStatus.CONVERTED, null, "ok"));
        conversionMetrics.record(new ConversionResult(
                UUID.randomUUID(), "exp-2", MileageConversionStatus.SKIPPED,
                MileageSkipReason.EVENT_DISABLED, "off"));
        conversionMetrics.record(new ConversionResult(
                UUID.randomUUID(), "exp-3", MileageConversionStatus.FAILED, null, "boom"));

        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("mileage_conversion_outcome_total");
        assertThat(body).contains("outcome=\"CONVERTED\"");
        assertThat(body).contains("outcome=\"SKIPPED\"");
        assertThat(body).contains("outcome=\"FAILED\"");
        assertThat(body).contains("application=\"mileage-for-clockify\"");
    }

    @Test
    void prometheusOutputContainsNoUserOrWorkspaceOrTokenTagsOnConversionCounters() throws Exception {
        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        body.lines()
                .filter(line -> line.startsWith("mileage_conversion_outcome_total"))
                .forEach(line -> {
                    assertThat(line.toLowerCase()).doesNotContain("userid=");
                    assertThat(line.toLowerCase()).doesNotContain("workspaceid=");
                    assertThat(line.toLowerCase()).doesNotContain("expenseid=");
                    assertThat(line.toLowerCase()).doesNotContain("token=");
                });
    }

    @Test
    void prometheusEndpointPublishesHttpServerRequestTimer() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("http_server_requests_seconds");
    }
}
