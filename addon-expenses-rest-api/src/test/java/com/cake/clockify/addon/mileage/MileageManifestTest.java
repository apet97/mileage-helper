package com.cake.clockify.addon.mileage;

import com.cake.clockify.addon.db.repository.AddonWebhookTokenRepository;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.db.service.AddonSettingsService;
import com.cake.clockify.addon.db.service.ClockifyClientFactory;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionReservationRepository;
import com.cake.clockify.addon.mileage.settings.MileageSettingsRepository;
import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        "addon.crypto.keys.k1=00000000000000000000000000000000000000000000000000000000000000aa"
})
@AutoConfigureMockMvc
class MileageManifestTest {
    @MockBean AddonWebhookTokenRepository webhookTokenRepository;
    @MockBean AddonSettingsService addonSettingsService;
    @MockBean ClockifyClientFactory clockifyClientFactory;
    @MockBean AddonInstallationService addonInstallationService;
    @MockBean MileageSettingsRepository mileageSettingsRepository;
    @MockBean MileageConversionRepository mileageConversionRepository;
    @MockBean MileageConversionReservationRepository mileageConversionReservationRepository;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ClockifyManifest manifest;

    @Test
    void manifestEndpointReturnsSchema15JsonThatValidatesAgainstLocalSchema() throws Exception {
        String body = mockMvc.perform(get("/manifest"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode manifestJson = objectMapper.readTree(body);
        JsonNode schemaJson = objectMapper.readTree(Files.readString(Path.of("manifest-schema.json")));
        ProcessingReport report = JsonSchemaFactory.byDefault().getJsonSchema(schemaJson).validate(manifestJson);

        assertThat(report.isSuccess()).as(report.toString()).isTrue();
        assertThat(manifestJson.path("schemaVersion").asText()).isEqualTo("1.5");
        assertThat(manifestJson.path("key").asText()).isEqualTo("mileage-for-clockify");
        assertThat(manifestJson.path("name").asText()).isEqualTo("Mileage for Clockify");
        assertThat(manifestJson.path("minimalSubscriptionPlan").asText()).isEqualTo("PRO");
        assertThat(values(manifestJson.path("scopes"))).contains(
                "EXPENSE_READ", "EXPENSE_WRITE", "USER_READ", "PROJECT_READ", "WORKSPACE_READ");
        assertThat(webhookEvents(manifestJson)).contains(
                "EXPENSE_CREATED", "EXPENSE_UPDATED", "EXPENSE_DELETED", "EXPENSE_RESTORED");
        assertThat(manifestJson.path("components").get(0).path("type").asText()).isEqualTo("sidebar");
        assertThat(manifestJson.path("components").get(0).path("path").asText()).isEqualTo("/iframe/mileage");
    }

    @Test
    void manifestBeanIsManualSchema15BecauseSdkHasNoV15Builder() {
        assertThat(manifest.getClass().getName()).isEqualTo("com.cake.clockify.addon.mileage.config.MileageManifestV15");
        assertThat(manifest.getSchemaVersion()).isEqualTo("1.5");
    }

    @Test
    void manifestIconAssetIsPackaged() throws Exception {
        String body = mockMvc.perform(get("/manifest"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode manifestJson = objectMapper.readTree(body);
        String iconPath = manifestJson.path("iconPath").asText();
        Path asset = Path.of("src/main/resources/static" + iconPath);

        assertThat(iconPath).isEqualTo("/assets/mileage/icon.png");
        assertThat(asset).exists().isRegularFile();
        assertThat(Files.size(asset)).isGreaterThan(1024L);
    }

    private static Set<String> values(JsonNode array) {
        Set<String> out = new HashSet<>();
        array.forEach(item -> out.add(item.asText()));
        return out;
    }

    private static Set<String> webhookEvents(JsonNode manifestJson) {
        Set<String> out = new HashSet<>();
        manifestJson.path("webhooks").forEach(item -> out.add(item.path("event").asText()));
        return out;
    }
}
