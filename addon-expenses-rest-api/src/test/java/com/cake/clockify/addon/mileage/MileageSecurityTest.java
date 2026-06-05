package com.cake.clockify.addon.mileage;

import com.cake.clockify.addon.core.auth.filter.ClockifyIframeAuthFilter;
import com.cake.clockify.addon.mileage.iframe.MileageIframeController;
import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageSecurityTest {
    @Test
    void mileageIframeRequiresAuthToken() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MileageIframeController())
                .addFilters(new ClockifyIframeAuthFilter(new RejectingSignatureParser()))
                .build();

        mockMvc.perform(get("/iframe/mileage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mileageIframeUsesExternalCssAndJs() {
        String html = new MileageIframeController().mileage(requestWithRole("ADMIN")).getBody();

        assertThat(html).contains("href=\"/assets/mileage/settings.css\"");
        assertThat(html).contains("src=\"/assets/mileage/settings-date.js\" defer");
        assertThat(html).contains("src=\"/assets/mileage/settings.js\" defer");
        assertThat(html.indexOf("settings-date.js")).isLessThan(html.indexOf("settings.js"));
    }

    @Test
    void installationTokenNeverAppearsInIframeHtml() {
        String html = new MileageIframeController().mileage(requestWithRole("ADMIN")).getBody();

        assertThat(html).doesNotContain("secret-token-value");
        assertThat(html).doesNotContain("auth_token=");
        assertThat(html).doesNotContain("Bearer ");
    }

    @Test
    void mileageIframeDoesNotContainInlineScriptOrInlineStyle() {
        String html = new MileageIframeController().mileage(requestWithRole("ADMIN")).getBody();

        assertThat(html).doesNotContain("<style");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).doesNotContain("onclick=");
        assertThat(html).doesNotContain("style=");
    }

    @Test
    void mileageJavascriptRemovesAuthTokenFromLocation() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("url.searchParams.delete(\"auth_token\")");
        assertThat(javascript).contains("history.replaceState");
    }

    @Test
    void mileageJavascriptUsesAuthorizationHeaderForBackendCalls() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("Authorization");
        assertThat(javascript).contains("\"Bearer \" + authToken");
    }

    @Test
    void nonAdminUserDoesNotSeeAdminControlsAfterClaimsLoad() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("workspaceRole");
        assertThat(javascript).contains("data-admin-only");
        assertThat(javascript).contains("element.hidden = !isAdmin");
    }

    @Test
    void memberMileageIframeRendersOnlyMineNavigationAndNoAdminPanels() {
        String html = new MileageIframeController().mileage(requestWithRole("MEMBER")).getBody();

        assertThat(html).contains("data-tab-target=\"mine\"");
        assertThat(html).contains("id=\"mine-range-preset\"");
        assertThat(html).contains("id=\"mine-range-from\"");
        assertThat(html).contains("id=\"mine-range-to\"");
        assertThat(html).doesNotContain("data-tab-target=\"team\"");
        assertThat(html).doesNotContain("data-tab-target=\"admin-settings\"");
        assertThat(html).doesNotContain("data-tab-target=\"conversion-log\"");
        assertThat(html).doesNotContain("data-tab-target=\"diagnostics\"");
        assertThat(html).doesNotContain("id=\"tab-team\"");
        assertThat(html).doesNotContain("id=\"tab-admin-settings\"");
        assertThat(html).doesNotContain("id=\"tab-conversion-log\"");
        assertThat(html).doesNotContain("id=\"tab-diagnostics\"");
        assertThat(html).doesNotContain("id=\"team-range-preset\"");
        assertThat(html).doesNotContain("id=\"conversion-range-preset\"");
    }

    @Test
    void adminMileageIframeRendersMineTeamSettingsConversionsAndDiagnostics() {
        String html = new MileageIframeController().mileage(requestWithRole("OWNER")).getBody();

        assertThat(html).contains("data-tab-target=\"mine\"");
        assertThat(html).contains("data-tab-target=\"team\"");
        assertThat(html).contains("data-tab-target=\"admin-settings\"");
        assertThat(html).contains("data-tab-target=\"conversion-log\"");
        assertThat(html).contains("data-tab-target=\"diagnostics\"");
        assertThat(html).contains("id=\"mine-range-preset\"");
        assertThat(html).contains("id=\"team-range-preset\"");
        assertThat(html).contains("id=\"conversion-range-preset\"");
        assertThat(html).contains("<option value=\"this_week\" selected>This week</option>");
        assertThat(html).contains("<option value=\"custom\">Custom</option>");
        assertThat(html).contains("<option value=\"this_month\">This month</option>");
        assertThat(html).contains("<option value=\"last_week\">Last week</option>");
        assertThat(html).contains("<option value=\"last_month\">Last month</option>");
        assertThat(html).contains("<option value=\"this_year\">This year</option>");
        assertThat(html).contains("<option value=\"last_year\">Last year</option>");
    }

    @Test
    void mileageIframeHidesInactiveTabPanelsOnInitialRender() {
        String html = new MileageIframeController().mileage(requestWithRole("OWNER")).getBody();

        assertThat(html).contains("<section class=\"tab-panel active\" id=\"tab-mine\">");
        assertThat(html).contains("<section class=\"tab-panel\" hidden id=\"tab-team\" data-admin-only=\"true\">");
        assertThat(html).contains("<section class=\"tab-panel\" hidden id=\"tab-admin-settings\" data-admin-only=\"true\">");
        assertThat(html).contains("<section class=\"tab-panel\" hidden id=\"tab-conversion-log\" data-admin-only=\"true\">");
        assertThat(html).contains("<section class=\"tab-panel\" hidden id=\"tab-diagnostics\" data-admin-only=\"true\">");
    }

    @Test
    void settingsIframeHidesMineAndShowsSettingsOnInitialRender() {
        String html = new MileageIframeController().settings(requestWithRole("OWNER")).getBody();

        assertThat(html).contains("<section class=\"tab-panel\" hidden id=\"tab-mine\">");
        assertThat(html).contains("<section class=\"tab-panel active\" id=\"tab-admin-settings\" data-admin-only=\"true\">");
    }

    @Test
    void mileageJavascriptKeepsOnlyActiveTabPanelVisible() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("panel.hidden = !active");
        assertThat(javascript).contains("element.classList.contains(\"tab-panel\") && !element.classList.contains(\"active\")");
    }

    @Test
    void memberCannotOpenSettingsIframe() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MileageIframeController()).build();

        mockMvc.perform(get("/iframe/settings")
                        .requestAttr(com.cake.clockify.addon.core.auth.RequestAttributes.NORMALIZED_CLAIMS,
                                claims("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mileageIframeDoesNotAskForRawUserId() {
        String html = new MileageIframeController().mileage(requestWithRole("ADMIN")).getBody();

        assertThat(html).doesNotContain("User ID");
        assertThat(html).doesNotContain("id=\"field-user\"");
        assertThat(html).doesNotContain("name=\"userId\"");
    }

    @Test
    void mileageCreateFormDoesNotExposeTaskSelector() {
        String html = new MileageIframeController().mileage(requestWithRole("ADMIN")).getBody();

        assertThat(html).doesNotContain("id=\"field-task\"");
        assertThat(html).doesNotContain("name=\"taskId\"");
        assertThat(html).doesNotContain("<span>Task</span>");
    }

    @Test
    void mileageCreateFormDefaultsBillableAndGroupsRateOverride() {
        String html = new MileageIframeController().mileage(requestWithRole("ADMIN")).getBody();

        assertThat(html).contains("id=\"field-billable\" name=\"billable\" type=\"checkbox\" checked");
        assertThat(html).contains("id=\"rate-field-row\"");
    }

    @Test
    void mileageJavascriptDoesNotSendUserId() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).doesNotContain("field-user");
        assertThat(javascript).doesNotContain("currentUserIdFromClaims");
        assertThat(javascript).doesNotContain("userId:");
        assertThat(javascript).doesNotContain("append(\"userId\"");
    }

    @Test
    void mileageJavascriptDoesNotFetchTaskOptionsOrSendTaskId() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).doesNotContain("options/tasks");
        assertThat(javascript).doesNotContain("field-task");
        assertThat(javascript).doesNotContain("taskId");
    }

    @Test
    void mileageJavascriptLoadsCreateContextBeforeAllowingRateOverride() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("/api/mileage/create-context");
        assertThat(javascript).contains("allowUserRateOverride");
        assertThat(javascript).contains("field-rate");
    }

    @Test
    void mileageJavascriptDownloadsCsvWithBearerHeaderAndNoAuthTokenQuery() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("downloadCsv");
        assertThat(javascript).contains("handleCsvExport");
        assertThat(javascript).contains("csvPath(exportConfig[0], exportConfig[1])");
        assertThat(javascript).contains("\"btn-export-mine\": [\"mine\", \"/api/mileage/mine.csv\", \"mileage-mine.csv\"]");
        assertThat(javascript).contains("\"btn-export-team\": [\"team\", \"/api/mileage/team.csv\", \"mileage-team.csv\"]");
        assertThat(javascript).contains("\"btn-export-conversions\": [\"conversion\", \"/api/mileage/conversions.csv\", \"mileage-conversions.csv\"]");
        assertThat(javascript).contains("rangeQuery(\"mine\"");
        assertThat(javascript).contains("from=");
        assertThat(javascript).contains("to=");
        assertThat(javascript).contains("blob()");
        assertThat(javascript).contains("Authorization");
        assertThat(javascript).doesNotContain("mine.csv?auth_token");
        assertThat(javascript).doesNotContain("team.csv?auth_token");
        assertThat(javascript).doesNotContain("conversions.csv?auth_token");
    }

    @Test
    void mileageJavascriptDisplaysCalculatedAmountAsPrimaryAmount() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("result.calculatedAmount");
        assertThat(javascript).contains("item.calculatedAmount");
        assertThat(javascript).contains("Expense amount: \" + roundedAmount");
    }

    @Test
    void settingsIframeUsesSingleMileageCategorySetupAndHidesLegacyControls() {
        String html = new MileageIframeController().settings(requestWithRole("OWNER")).getBody();

        assertThat(html).contains("id=\"settings-mileage-category\"");
        assertThat(html).contains("id=\"btn-setup-mileage-category\"");
        assertThat(html).doesNotContain("id=\"settings-unit\"");
        assertThat(html).doesNotContain("id=\"settings-input-category\"");
        assertThat(html).doesNotContain("id=\"settings-output-category\"");
        assertThat(html).doesNotContain("id=\"settings-rounding\"");
        assertThat(html).doesNotContain("id=\"settings-template\"");
        assertThat(html).doesNotContain("id=\"settings-dry-run\"");
    }

    @Test
    void mileageJavascriptUsesReadableNamesDatesSourcesAndSingleCategorySettings() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("userName");
        assertThat(javascript).contains("sourceLabel");
        assertThat(javascript).contains("formatDate");
        assertThat(javascript).contains("formatExpenseDate");
        assertThat(javascript).contains("Intl.DateTimeFormat(\"en-US\"");
        assertThat(javascript).contains("mileageCategoryId");
        assertThat(javascript).contains("/api/mileage/settings/mileage-category");
    }

    @Test
    void mileageJavascriptHonorsClockifyUserTimeZoneClaimAlias() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));
        String dateJavascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings-date.js"));

        assertThat(javascript).contains("tokenClaims.userTimeZone");
        assertThat(javascript).contains("dateRangeForPreset(preset, timezoneFromClaims())");
        assertThat(dateJavascript).contains("Intl.DateTimeFormat(\"en-CA\"");
    }

    @Test
    void mileageJavascriptWiresDateRangePresetsToListsAndCsvExports() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("initDateRanges");
        assertThat(javascript).contains("rangePresets");
        assertThat(javascript).contains("dateRangeForPreset");
        assertThat(javascript).contains("loadMine");
        assertThat(javascript).contains("const query = rangeQuery(\"mine\")");
        assertThat(javascript).contains("/api/mileage/mine?pageSize=50\" + query");
        assertThat(javascript).contains("const query = rangeQuery(\"team\")");
        assertThat(javascript).contains("/api/mileage/team?pageSize=50\" + query");
        assertThat(javascript).contains("const query = rangeQuery(\"conversion\")");
        assertThat(javascript).contains("/api/mileage/conversions?pageSize=50\" + query");
        assertThat(javascript).contains("document.addEventListener(\"click\", handleCsvExport)");
        assertThat(javascript).contains("downloadCsv(csvPath(exportConfig[0], exportConfig[1]), exportConfig[2])");
    }

    @Test
    void mileageJavascriptDefaultsCreateDateFromClaimsTimezoneHelper() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("date.value = window.MileageDateHelpers.isoDate(");
        assertThat(javascript).contains("window.MileageDateHelpers.todayForTimeZone(timezoneFromClaims())");
        assertThat(javascript).doesNotContain("date.value = new Date().toISOString().slice(0, 10)");
    }

    @Test
    void mileageJavascriptHardensSettingsDateReceiptAndRepairFailureStates() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("const settingsPromise = apiFetch(\"/api/mileage/settings\")");
        assertThat(javascript).contains("const categoriesPromise = loadCategories().catch");
        assertThat(javascript).contains("Mileage categories could not be loaded: ");
        assertThat(javascript).contains("function validSelectedDateRange(scope)");
        assertThat(javascript).contains("const range = validSelectedDateRange(scope)");
        assertThat(javascript).contains("Choose both From and To dates.");
        assertThat(javascript).contains("if (query === null)");
        assertThat(javascript).contains("const maxReceiptBytes = 10 * 1024 * 1024");
        assertThat(javascript).contains("const allowedReceiptTypes = new Set");
        assertThat(javascript).contains("function validateReceipt(file)");
        assertThat(javascript).contains("if (!validateReceipt(file))");
        assertThat(javascript).contains("Number.isNaN(date.getTime())");
        assertThat(javascript).contains("button.textContent = \"Repairing...\"");
        assertThat(javascript).contains("button.textContent = \"Use or Repair Mileage Category\"");
    }

    @Test
    void mileageJavascriptAppliesTokenThemeWithoutExposingToken() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("function applyTheme()");
        assertThat(javascript).contains("themeFromClaims");
        assertThat(javascript).contains("document.documentElement.dataset.theme");
    }

    @Test
    void mileageCssDefinesDarkThemeVariables() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.css"));

        assertThat(css).contains(":root[data-theme=\"dark\"]");
        assertThat(css).contains("color-scheme: dark");
    }

    @Test
    void mileageCssHidesEmptyPreviewAndWarningsContainers() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.css"));

        assertThat(css).contains(".result-strip:empty");
        assertThat(css).contains(".warnings:empty");
        assertThat(css).contains("display: none");
    }

    @Test
    void teamAndConversionsPanelsExposeUserFilter() {
        String html = new MileageIframeController().mileage(requestWithRole("OWNER")).getBody();

        assertThat(html).contains("id=\"team-user-filter\"");
        assertThat(html).contains("id=\"conversion-user-filter\"");
        assertThat(html).doesNotContain("id=\"field-user\"");
        assertThat(html).doesNotContain("User ID");
    }

    @Test
    void mileageJavascriptWiresUserFilterWithoutSendingUserIdForCreate() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("/api/mileage/options/users");
        assertThat(javascript).contains("function userFilterQuery(scope)");
        assertThat(javascript).contains("\"&userId=\" + encodeURIComponent");
        // CSV export lines must stay byte-identical:
        assertThat(javascript).contains("downloadCsv(csvPath(exportConfig[0], exportConfig[1]), exportConfig[2])");
        // create flow still must not send userId:
        assertThat(javascript).doesNotContain("field-user");
        assertThat(javascript).doesNotContain("userId:");
        assertThat(javascript).doesNotContain("append(\"userId\"");
    }

    @Test
    void settingsIframeExposesEditableNoteTemplate() {
        String html = new MileageIframeController().settings(requestWithRole("OWNER")).getBody();

        assertThat(html).contains("id=\"settings-note-template\"");
        assertThat(html).doesNotContain("id=\"settings-template\"");
        assertThat(html).doesNotContain("<style");
        assertThat(html).doesNotContain("onclick=");
    }

    @Test
    void mileageJavascriptLoadsAndSavesNoteTemplate() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/settings.js"));

        assertThat(javascript).contains("settings-note-template");
        assertThat(javascript).contains("settings.noteTemplate");
        assertThat(javascript).contains("noteTemplate: formValue(\"settings-note-template\") || null");
    }

    @Test
    void reportJavascriptStripsAuthTokenAndWiresPrint() throws Exception {
        String javascript = Files.readString(Path.of("src/main/resources/static/assets/mileage/report.js"));

        assertThat(javascript).contains("auth_token");
        assertThat(javascript).contains("history.replaceState");
        assertThat(javascript).contains("window.print()");
        assertThat(javascript).contains("btn-print");
    }

    private static MockHttpServletRequest requestWithRole(String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(com.cake.clockify.addon.core.auth.RequestAttributes.NORMALIZED_CLAIMS, claims(role));
        return request;
    }

    private static com.cake.clockify.addon.core.auth.NormalizedClaims claims(String role) {
        return new com.cake.clockify.addon.core.auth.NormalizedClaims(
                "ws-iframe",
                "mileage-for-clockify",
                "https://backend.example.test",
                "https://reports.example.test",
                null,
                null,
                "user-claims",
                role,
                "en",
                "DEFAULT",
                "UTC",
                java.time.Instant.now());
    }

    private static final class RejectingSignatureParser extends ClockifySignatureParser {
        private RejectingSignatureParser() {
            super("mileage-for-clockify", publicKey());
        }

        @Override
        public Map<String, Object> parseClaims(String token) {
            throw new IllegalArgumentException("invalid signature");
        }

        private static RSAPublicKey publicKey() {
            try {
                return (RSAPublicKey) KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
