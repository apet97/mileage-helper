package com.cake.clockify.addon.mileage.settings;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.mileage.api.model.MileageSettingsRequest;
import com.cake.clockify.addon.mileage.api.model.MileageSettingsResponse;
import com.cake.clockify.addon.mileage.audit.MileageConversion;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionReservationRepository;
import com.cake.clockify.addon.mileage.audit.MileageConversionSource;
import com.cake.clockify.addon.mileage.audit.MileageConversionStatus;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
        "mileage.worker.enabled=false",
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
    @Autowired MileageConversionReservationRepository reservationRepository;
    @Autowired JdbcTemplate jdbcTemplate;
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
    void mileageConversionSchemaOmitsObsoleteAuditSurface() {
        var columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'mileage_test'
                  AND table_name = 'mileage_conversion'
                """, String.class);

        assertThat(columns)
                .doesNotContain("currency", "raw_event_hash", "clockify_request_id");
        assertThat(MileageConversionStatus.values())
                .extracting(Enum::name)
                .doesNotContain("FETCHED");
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO mileage_test.mileage_conversion
                    (id, workspace_id, expense_id, source, status)
                VALUES (?, 'ws-schema', 'exp-schema', 'WEBHOOK_CREATED', 'FETCHED')
                """, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void conversionReservationReturnsSameIdForDuplicateWorkspaceExpense() {
        UUID first = reservationRepository.reserve(
                "ws-reserve",
                "exp-reserve",
                MileageConversionSource.WEBHOOK_CREATED,
                "EXPENSE_CREATED");
        UUID second = reservationRepository.reserve(
                "ws-reserve",
                "exp-reserve",
                MileageConversionSource.WEBHOOK_UPDATED,
                "EXPENSE_UPDATED");

        assertThat(second).isEqualTo(first);
        assertThat(conversionRepository.countByWorkspaceIdAndExpenseId("ws-reserve", "exp-reserve"))
                .isEqualTo(1);
        MileageConversion conversion = conversionRepository
                .findByWorkspaceIdAndExpenseId("ws-reserve", "exp-reserve")
                .orElseThrow();
        assertThat(conversion.getSource()).isEqualTo(MileageConversionSource.WEBHOOK_CREATED);
        assertThat(conversion.getStatus()).isEqualTo(MileageConversionStatus.RECEIVED);
    }

    @Test
    void conversionReservationUsesPreferredIdForNewRows() {
        UUID preferred = UUID.fromString("00000000-0000-0000-0000-000000000777");

        UUID reserved = reservationRepository.reserve(
                preferred,
                "ws-reserve-preferred",
                "exp-reserve-preferred",
                MileageConversionSource.ADDON_FORM,
                "ADDON_FORM");

        assertThat(reserved).isEqualTo(preferred);
        assertThat(conversionRepository.findByIdAndWorkspaceId(preferred, "ws-reserve-preferred"))
                .isPresent();
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
    void conversionQueriesFilterByWorkspaceUserStatusAndExpenseDate() {
        MileageConversion thisWeek = conversion("ws-dates", "exp-this-week", MileageConversionStatus.CONVERTED);
        thisWeek.setExpenseDate(LocalDate.parse("2026-05-24"));
        conversionRepository.saveAndFlush(thisWeek);
        MileageConversion lastWeek = conversion("ws-dates", "exp-last-week", MileageConversionStatus.CONVERTED);
        lastWeek.setExpenseDate(LocalDate.parse("2026-05-17"));
        conversionRepository.saveAndFlush(lastWeek);
        MileageConversion failed = conversion("ws-dates", "exp-failed", MileageConversionStatus.FAILED);
        failed.setExpenseDate(LocalDate.parse("2026-05-25"));
        conversionRepository.saveAndFlush(failed);
        MileageConversion otherUser = conversion("ws-dates", "exp-other-user", MileageConversionStatus.CONVERTED);
        otherUser.setUserId("user-two");
        otherUser.setExpenseDate(LocalDate.parse("2026-05-26"));
        conversionRepository.saveAndFlush(otherUser);
        MileageConversion otherWorkspace = conversion("ws-other-dates", "exp-other-ws", MileageConversionStatus.CONVERTED);
        otherWorkspace.setExpenseDate(LocalDate.parse("2026-05-24"));
        conversionRepository.saveAndFlush(otherWorkspace);

        LocalDate from = LocalDate.parse("2026-05-24");
        LocalDate to = LocalDate.parse("2026-05-30");

        assertThat(conversionRepository.findAllByWorkspaceIdAndUserIdAndExpenseDateBetween(
                        "ws-dates", "user-one", from, to, PageRequest.of(0, 10)))
                .extracting(MileageConversion::getExpenseId)
                .containsExactlyInAnyOrder("exp-this-week", "exp-failed");
        assertThat(conversionRepository.findAllByWorkspaceIdAndStatusAndExpenseDateBetween(
                        "ws-dates", MileageConversionStatus.CONVERTED, from, to, PageRequest.of(0, 10)))
                .extracting(MileageConversion::getExpenseId)
                .containsExactlyInAnyOrder("exp-this-week", "exp-other-user");
        assertThat(conversionRepository.findAllByWorkspaceIdAndExpenseDateBetween(
                        "ws-dates", from, to, PageRequest.of(0, 10)))
                .extracting(MileageConversion::getExpenseId)
                .containsExactlyInAnyOrder("exp-this-week", "exp-failed", "exp-other-user");
    }

    @Test
    void visibleMileageQueriesExcludeDeletedConversions() {
        MileageConversion visible = conversion("ws-visible", "exp-visible", MileageConversionStatus.CONVERTED);
        MileageConversion deleted = conversion("ws-visible", "exp-deleted", MileageConversionStatus.DELETED);
        deleted.setDeletedAt(Instant.parse("2026-05-24T12:00:00Z"));
        MileageConversion otherUser = conversion("ws-visible", "exp-other-user", MileageConversionStatus.CONVERTED);
        otherUser.setUserId("user-two");
        MileageConversion otherWorkspace = conversion("ws-other-visible", "exp-other-workspace", MileageConversionStatus.CONVERTED);
        conversionRepository.saveAndFlush(visible);
        conversionRepository.saveAndFlush(deleted);
        conversionRepository.saveAndFlush(otherUser);
        conversionRepository.saveAndFlush(otherWorkspace);

        LocalDate from = LocalDate.parse("2026-05-24");
        LocalDate to = LocalDate.parse("2026-05-30");

        assertThat(conversionRepository.findAllByWorkspaceIdAndUserIdAndStatusNotAndExpenseDateBetween(
                        "ws-visible", "user-one", MileageConversionStatus.DELETED, from, to, PageRequest.of(0, 10)))
                .extracting(MileageConversion::getExpenseId)
                .containsExactly("exp-visible");
        assertThat(conversionRepository.findAllByWorkspaceIdAndStatusNotAndExpenseDateBetween(
                        "ws-visible", MileageConversionStatus.DELETED, from, to, PageRequest.of(0, 10)))
                .extracting(MileageConversion::getExpenseId)
                .containsExactlyInAnyOrder("exp-visible", "exp-other-user");
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
        assertThat(response.unit()).isEqualTo("mile");
        assertThat(response.fixedUnit()).isEqualTo("mile");
        assertThat(response.roundingMode()).isEqualTo("HALF_UP");
        assertThat(response.fixedRoundingMode()).isEqualTo("HALF_UP");
        assertThat(response.allowUserRateOverride()).isFalse();
        assertThat(response.preserveOriginalNotes()).isFalse();
        assertThat(response.convertOnCreate()).isTrue();
        assertThat(response.convertOnUpdate()).isTrue();
        assertThat(response.completeForAddonCreate()).isFalse();
        assertThat(response.completeForNativeConversion()).isFalse();
        assertThat(response.diagnostics()).contains("rate is required", "outputCategoryId is required");
    }

    @Test
    void databaseDefaultsMatchSingleMileageCategoryDefaults() {
        jdbcTemplate.update("INSERT INTO mileage_test.mileage_workspace_settings (workspace_id) VALUES (?)", "ws-db-defaults");

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT unit, rounding_mode, preserve_original_notes
                FROM mileage_test.mileage_workspace_settings
                WHERE workspace_id = ?
                """, "ws-db-defaults");

        assertThat(row.get("unit")).isEqualTo("mile");
        assertThat(row.get("rounding_mode")).isEqualTo("HALF_UP");
        assertThat(row.get("preserve_original_notes")).isEqualTo(false);
    }

    @Test
    void savesSettingsWithBigDecimalRate() {
        MileageSettingsRequest request = new MileageSettingsRequest(
                true, "0.655", "km", "cat-input", "cat-output", "cat-mileage", "HALF_EVEN",
                true, false, true, false, true, "custom {{marker}}");

        MileageWorkspaceSettings saved = settingsService.saveSettings("ws-save", request, "admin-1");

        assertThat(saved.getRate()).isEqualByComparingTo(new BigDecimal("0.655"));
        assertThat(saved.getUnit()).isEqualTo("mile");
        assertThat(saved.getInputCategoryId()).isEqualTo("cat-mileage");
        assertThat(saved.getOutputCategoryId()).isEqualTo("cat-mileage");
        assertThat(saved.getRoundingMode()).isEqualTo("HALF_UP");
        assertThat(saved.isPreserveOriginalNotes()).isFalse();
        assertThat(saved.getUpdatedByUserId()).isEqualTo("admin-1");
    }

    @Test
    void normalizesExistingTwoCategorySettingsToSingleMileageCategory() {
        MileageWorkspaceSettings settings = new MileageWorkspaceSettings();
        settings.setWorkspaceId("ws-existing");
        settings.setRate(new BigDecimal("0.725"));
        settings.setUnit("mi");
        settings.setInputCategoryId("cat-input");
        settings.setOutputCategoryId("cat-output");
        settings.setRoundingMode("DOWN");
        settings.setPreserveOriginalNotes(true);
        settingsRepository.saveAndFlush(settings);

        MileageSettingsResponse response = settingsService.getEffectiveSettings("ws-existing");

        assertThat(response.unit()).isEqualTo("mile");
        assertThat(response.roundingMode()).isEqualTo("HALF_UP");
        assertThat(response.inputCategoryId()).isEqualTo("cat-input");
        assertThat(response.outputCategoryId()).isEqualTo("cat-input");
        assertThat(response.mileageCategoryId()).isEqualTo("cat-input");
        assertThat(response.preserveOriginalNotes()).isFalse();
    }

    @Test
    void rejectsInvalidRoundingMode() {
        MileageSettingsRequest request = new MileageSettingsRequest(
                true, "0.655", "mi", null, "cat-output", null, "ROUNDISH",
                true, true, true, false, false, null);

        MileageWorkspaceSettings saved = settingsService.saveSettings("ws-rounding", request, "admin-1");

        assertThat(saved.getRoundingMode()).isEqualTo("HALF_UP");
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
        conversion.setExpenseDate(LocalDate.parse("2026-05-24"));
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
