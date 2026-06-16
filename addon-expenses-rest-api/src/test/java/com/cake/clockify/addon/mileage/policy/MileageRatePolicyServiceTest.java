package com.cake.clockify.addon.mileage.policy;

import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyRequest;
import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyResponse;
import com.cake.clockify.addon.mileage.settings.MileageSettingsRepository;
import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;
import com.cake.clockify.addon.mileage.settings.MileageWorkspaceSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

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
        "spring.jpa.properties.hibernate.default_schema=mileage_policy_test",
        "spring.flyway.schemas=mileage_policy_test",
        "spring.flyway.create-schemas=true"
})
@Testcontainers
class MileageRatePolicyServiceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MileageRatePolicyRepository policyRepository;
    @Autowired MileageSettingsRepository settingsRepository;
    @Autowired MileageRatePolicyService policyService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        policyRepository.deleteAll();
        settingsRepository.deleteAll();
    }

    @Test
    void migrationCreatesRatePolicyTableWithoutDatabaseUuidFunction() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'mileage_policy_test'
                  AND table_name = 'mileage_rate_policy'
                """, String.class);

        assertThat(columns)
                .contains("id", "workspace_id", "name", "rate", "unit", "effective_from", "effective_to",
                        "active", "created_at", "updated_at", "updated_by_user_id");
    }

    @Test
    void migrationAddsRatePolicyAuditColumnsToConversions() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'mileage_policy_test'
                  AND table_name = 'mileage_conversion'
                """, String.class);

        assertThat(columns).contains("rate_source", "rate_policy_id", "rate_policy_name");
    }

    @Test
    void createsDefaultPolicyFromSettingsRateWhenNoneExists() {
        MileageRatePolicyResponse created = policyService.ensureDefaultPolicy(
                "ws-policy-default",
                settings("ws-policy-default", new BigDecimal("0.725")),
                "admin-1");

        assertThat(created.name()).isEqualTo("Default mileage rate");
        assertThat(created.rate()).isEqualTo("0.725");
        assertThat(created.effectiveFrom()).isEqualTo(LocalDate.parse("1970-01-01"));
        assertThat(created.effectiveTo()).isNull();
        assertThat(created.active()).isTrue();
        assertThat(created.updatedByUserId()).isEqualTo("admin-1");

        MileageRatePolicyResponse again = policyService.ensureDefaultPolicy(
                "ws-policy-default",
                settings("ws-policy-default", new BigDecimal("0.800")),
                "admin-2");

        assertThat(again.id()).isEqualTo(created.id());
        assertThat(policyRepository.findByWorkspaceIdOrderByEffectiveFromDescCreatedAtDesc("ws-policy-default"))
                .hasSize(1);
    }

    @Test
    void listsPoliciesOnlyForWorkspaceNewestEffectiveDateFirst() {
        policyService.createPolicy("ws-a", request("Old", "0.60", "2025-01-01", "2025-12-31", true), "admin-a");
        policyService.createPolicy("ws-a", request("New", "0.70", "2026-01-01", null, true), "admin-a");
        policyService.createPolicy("ws-b", request("Other workspace", "0.80", "2026-01-01", null, true), "admin-b");

        assertThat(policyService.listPolicies("ws-a"))
                .extracting(MileageRatePolicyResponse::name)
                .containsExactly("New", "Old");
    }

    @Test
    void rejectsOverlappingActivePoliciesButAllowsAdjacentAndInactiveOverlap() {
        policyService.createPolicy("ws-overlap", request("First", "0.60", "2026-01-01", "2026-06-30", true), "admin-1");

        assertThatThrownBy(() -> policyService.createPolicy(
                "ws-overlap",
                request("Overlap", "0.65", "2026-06-01", null, true),
                "admin-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");

        MileageRatePolicyResponse adjacent = policyService.createPolicy(
                "ws-overlap",
                request("Adjacent", "0.70", "2026-07-01", null, true),
                "admin-1");
        MileageRatePolicyResponse inactive = policyService.createPolicy(
                "ws-overlap",
                request("Inactive overlap", "0.55", "2026-02-01", "2026-03-01", false),
                "admin-1");

        assertThat(adjacent.active()).isTrue();
        assertThat(inactive.active()).isFalse();
    }

    @Test
    void validatesPolicyInput() {
        assertThatThrownBy(() -> policyService.createPolicy(
                "ws-invalid", request("", "0.60", "2026-01-01", null, true), "admin-1"))
                .hasMessage("name is required");
        assertThatThrownBy(() -> policyService.createPolicy(
                "ws-invalid", request("Zero", "0", "2026-01-01", null, true), "admin-1"))
                .hasMessage("rate must be greater than zero");
        assertThatThrownBy(() -> policyService.createPolicy(
                "ws-invalid", request("Negative", "-1", "2026-01-01", null, true), "admin-1"))
                .hasMessage("rate must be greater than zero");
        assertThatThrownBy(() -> policyService.createPolicy(
                "ws-invalid", request("Backwards", "0.60", "2026-06-01", "2026-05-31", true), "admin-1"))
                .hasMessage("effectiveTo must be on or after effectiveFrom");
    }

    @Test
    void updateCannotMovePolicyIntoOverlap() {
        MileageRatePolicyResponse first = policyService.createPolicy(
                "ws-update",
                request("First", "0.60", "2026-01-01", "2026-03-31", true),
                "admin-1");
        policyService.createPolicy("ws-update", request("Second", "0.70", "2026-04-01", null, true), "admin-1");

        assertThatThrownBy(() -> policyService.updatePolicy(
                "ws-update",
                first.id(),
                request("Moved", "0.65", "2026-02-01", "2026-04-15", true),
                "admin-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void deactivateSoftDeletesPolicyAndProtectsOtherWorkspaces() {
        MileageRatePolicyResponse policy = policyService.createPolicy(
                "ws-delete",
                request("Active", "0.60", "2026-01-01", null, true),
                "admin-1");

        assertThatThrownBy(() -> policyService.deactivatePolicy("ws-other", policy.id(), "admin-2"))
                .hasMessageContaining("Rate policy not found");

        MileageRatePolicyResponse deactivated = policyService.deactivatePolicy("ws-delete", policy.id(), "admin-2");

        assertThat(deactivated.active()).isFalse();
        assertThat(policyRepository.findById(policy.id())).isPresent();
    }

    @Test
    void doesNotDeactivateLastActivePolicyWhenFallbackRateIsMissing() {
        MileageWorkspaceSettings settings = new MileageWorkspaceSettings();
        settings.setWorkspaceId("ws-no-fallback");
        settings.setOutputCategoryId("cat-mileage");
        settings.setInputCategoryId("cat-mileage");
        settingsRepository.saveAndFlush(settings);
        MileageRatePolicyResponse policy = policyService.createPolicy(
                "ws-no-fallback",
                request("Active", "0.60", "2026-01-01", null, true),
                "admin-1");

        assertThatThrownBy(() -> policyService.deactivatePolicy("ws-no-fallback", policy.id(), "admin-2"))
                .hasMessageContaining("last active policy");
    }

    @Test
    void resolvesPolicyRateByExpenseDateThenSettingsFallback() {
        policyService.createPolicy("ws-resolve", request("2026", "0.700", "2026-01-01", null, true), "admin-1");

        MileageRateResolution policy = policyService.resolveRate(
                "ws-resolve",
                LocalDate.parse("2026-06-16"),
                settings("ws-resolve", new BigDecimal("0.655")));
        MileageRateResolution fallback = policyService.resolveRate(
                "ws-resolve",
                LocalDate.parse("2025-06-16"),
                settings("ws-resolve", new BigDecimal("0.655")));

        assertThat(policy.source()).isEqualTo("POLICY");
        assertThat(policy.rate()).isEqualByComparingTo("0.700");
        assertThat(policy.policyName()).isEqualTo("2026");
        assertThat(fallback.source()).isEqualTo("SETTINGS_FALLBACK");
        assertThat(fallback.rate()).isEqualByComparingTo("0.655");
    }

    private static MileageRatePolicyRequest request(
            String name,
            String rate,
            String effectiveFrom,
            String effectiveTo,
            Boolean active) {
        return new MileageRatePolicyRequest(
                name,
                rate,
                LocalDate.parse(effectiveFrom),
                effectiveTo == null ? null : LocalDate.parse(effectiveTo),
                active);
    }

    private static MileageSettingsValidation settings(String workspaceId, BigDecimal rate) {
        return new MileageSettingsValidation(
                workspaceId,
                true,
                rate != null,
                rate,
                "mile",
                "cat-mileage",
                "cat-mileage",
                RoundingMode.HALF_UP,
                true,
                true,
                false,
                false,
                false,
                null,
                rate == null ? List.of("rate is required") : List.of());
    }
}
