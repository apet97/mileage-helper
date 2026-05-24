package com.cake.clockify.addon.mileage.settings;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.mileage.api.model.MileageSettingsRequest;
import com.cake.clockify.addon.mileage.api.model.MileageSettingsResponse;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "addon.key=mileage-for-clockify",
        "addon.name=Mileage for Clockify",
        "addon.description=Create and convert precise mileage reimbursements into real Clockify flat expenses.",
        "addon.base-url=https://mileage.example.com",
        "addon.crypto.active-key-id=k1",
        "addon.crypto.keys.k1=00000000000000000000000000000000000000000000000000000000000000aa",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=mileage_test",
        "spring.flyway.schemas=mileage_test",
        "spring.flyway.create-schemas=true"
})
@Testcontainers
class MileageSettingsServiceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MileageSettingsRepository settingsRepository;
    @Autowired MileageConversionRepository conversionRepository;
    @Autowired MileageSettingsService settingsService;
    @Autowired MileageAuthorizationService authorizationService;

    @Test
    void settingsRepositorySavesBigDecimalRatePerWorkspace() {
        MileageWorkspaceSettings settings = new MileageWorkspaceSettings();
        settings.setWorkspaceId("ws-settings");
        settings.setRate(new BigDecimal("0.655000"));
        settings.setUnit("mi");

        settingsRepository.saveAndFlush(settings);

        MileageWorkspaceSettings reloaded = settingsRepository.findById("ws-settings").orElseThrow();
        assertThat(reloaded.getRate()).isEqualByComparingTo(new BigDecimal("0.655000"));
        assertThat(reloaded.getRate().scale()).isEqualTo(6);
        assertThat(reloaded.getWorkspaceId()).isEqualTo("ws-settings");
    }

    @Test
    void conversionRepositoryEnforcesWorkspaceExpenseUniqueness() {
        conversionRepository.saveAndFlush(conversion("ws-unique", "exp-unique", MileageConversionStatus.CONVERTED));

        assertThatThrownBy(() -> conversionRepository.saveAndFlush(
                conversion("ws-unique", "exp-unique", MileageConversionStatus.RECEIVED)))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    void conversionQueriesAreWorkspaceIsolated() {
        conversionRepository.saveAndFlush(conversion("ws-one", "exp-a", MileageConversionStatus.CONVERTED));
        MileageConversion otherUser = conversion("ws-one", "exp-c", MileageConversionStatus.CONVERTED);
        otherUser.setUserId("user-two");
        conversionRepository.saveAndFlush(otherUser);
        conversionRepository.saveAndFlush(conversion("ws-two", "exp-b", MileageConversionStatus.CONVERTED));

        assertThat(conversionRepository.findAllByWorkspaceId("ws-one", PageRequest.of(0, 10)))
                .extracting(MileageConversion::getExpenseId)
                .containsExactlyInAnyOrder("exp-a", "exp-c");
        assertThat(conversionRepository.findAllByWorkspaceIdAndUserId("ws-one", "user-one", PageRequest.of(0, 10)))
                .extracting(MileageConversion::getExpenseId)
                .containsExactly("exp-a");
        assertThat(conversionRepository.findByWorkspaceIdAndExpenseId("ws-one", "exp-b")).isEmpty();
    }

    @Test
    void markDeletedUpdatesStatusAndDeletedAtWithoutDeletingRow() {
        MileageConversion conversion = conversion("ws-delete", "exp-delete", MileageConversionStatus.CONVERTED);
        conversionRepository.saveAndFlush(conversion);

        conversion.setStatus(MileageConversionStatus.DELETED);
        conversion.setDeletedAt(Instant.now());
        conversion.setUpdatedAt(Instant.now());
        conversionRepository.saveAndFlush(conversion);

        MileageConversion reloaded = conversionRepository.findByWorkspaceIdAndExpenseId("ws-delete", "exp-delete").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MileageConversionStatus.DELETED);
        assertThat(reloaded.getDeletedAt()).isNotNull();
        assertThat(conversionRepository.countByWorkspaceIdAndExpenseId("ws-delete", "exp-delete")).isEqualTo(1);
    }

    @Test
    void returnsDefaultIncompleteSettingsWhenWorkspaceHasNoRow() {
        MileageSettingsResponse response = settingsService.getEffectiveSettings("ws-default");

        assertThat(response.enabled()).isTrue();
        assertThat(response.unit()).isEqualTo("mi");
        assertThat(response.roundingMode()).isEqualTo("HALF_UP");
        assertThat(response.allowUserRateOverride()).isFalse();
        assertThat(response.convertOnCreate()).isTrue();
        assertThat(response.convertOnUpdate()).isTrue();
        assertThat(response.completeForAddonCreate()).isFalse();
        assertThat(response.completeForNativeConversion()).isFalse();
        assertThat(response.diagnostics()).contains("rate is required", "outputCategoryId is required");
    }

    @Test
    void savesSettingsWithBigDecimalRate() {
        MileageSettingsRequest request = new MileageSettingsRequest(
                true, "0.655", "km", "cat-input", "cat-output", "HALF_EVEN",
                true, false, true, false, true, "custom {{marker}}");

        MileageWorkspaceSettings saved = settingsService.saveSettings("ws-save", request, "admin-1");

        assertThat(saved.getRate()).isEqualByComparingTo(new BigDecimal("0.655"));
        assertThat(saved.getUnit()).isEqualTo("km");
        assertThat(saved.getInputCategoryId()).isEqualTo("cat-input");
        assertThat(saved.getOutputCategoryId()).isEqualTo("cat-output");
        assertThat(saved.getRoundingMode()).isEqualTo("HALF_EVEN");
        assertThat(saved.getUpdatedByUserId()).isEqualTo("admin-1");
    }

    @Test
    void rejectsInvalidRoundingMode() {
        MileageSettingsRequest request = new MileageSettingsRequest(
                true, "0.655", "mi", null, "cat-output", "ROUNDISH",
                true, true, true, false, false, null);

        assertThatThrownBy(() -> settingsService.saveSettings("ws-rounding", request, "admin-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("roundingMode must be a Java RoundingMode name");
    }

    @Test
    void validationRequiresRateAndOutputCategoryForCreateExpense() {
        MileageSettingsValidation validation = settingsService.validateForAddonCreate("ws-incomplete");

        assertThat(validation.complete()).isFalse();
        assertThat(validation.diagnostics()).contains("rate is required", "outputCategoryId is required");
    }

    @Test
    void adminAuthorizationAllowsOwnerAndAdmin() {
        authorizationService.requireAdmin(claims("OWNER"));
        authorizationService.requireAdmin(claims("ADMIN"));
    }

    @Test
    void adminAuthorizationRejectsMember() {
        assertThatThrownBy(() -> authorizationService.requireAdmin(claims("MEMBER")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Admin role is required");
    }

    private static MileageConversion conversion(String workspaceId, String expenseId, MileageConversionStatus status) {
        MileageConversion conversion = new MileageConversion();
        conversion.setId(UUID.randomUUID());
        conversion.setWorkspaceId(workspaceId);
        conversion.setExpenseId(expenseId);
        conversion.setSource(MileageConversionSource.WEBHOOK_CREATED);
        conversion.setStatus(status);
        conversion.setUserId("user-one");
        conversion.setMiles(new BigDecimal("37.400000"));
        conversion.setRate(new BigDecimal("0.655000"));
        conversion.setCalculatedAmount(new BigDecimal("24.497000"));
        conversion.setRoundedAmount(new BigDecimal("24.50"));
        return conversion;
    }

    private static NormalizedClaims claims(String role) {
        return new NormalizedClaims(
                "ws-auth",
                "mileage-for-clockify",
                "https://backend.example.test",
                "https://reports.example.test",
                null,
                null,
                "user-auth",
                role,
                "en",
                "DEFAULT",
                "UTC",
                Instant.now());
    }
}
