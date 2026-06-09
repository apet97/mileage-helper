package com.cake.clockify.addon.mileage.iframe;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MileageIframeController {
    private final MileageAuthorizationService authorizationService;

    public MileageIframeController() {
        this(new MileageAuthorizationService());
    }

    public MileageIframeController(MileageAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @GetMapping(value = "/iframe/mileage", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> mileage(HttpServletRequest request) {
        return ResponseEntity.ok(html(isAdmin(RequestAttributes.requireClaims(request)), "mine"));
    }

    @GetMapping(value = "/iframe/settings", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> settings(HttpServletRequest request) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        authorizationService.requireAdmin(claims);
        return ResponseEntity.ok(html(true, "admin-settings"));
    }

    private boolean isAdmin(NormalizedClaims claims) {
        String role = claims.workspaceRole();
        return "OWNER".equals(role) || "ADMIN".equals(role);
    }

    private static String html(boolean admin, String activeTab) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Mileage for Clockify</title>
                  <link rel="icon" type="image/png" href="/assets/mileage/icon.png">
                  <link rel="stylesheet" href="/assets/mileage/settings.css">
                  <script src="/assets/mileage/settings-date.js" defer></script>
                  <script src="/assets/mileage/settings-core.js" defer></script>
                  <script src="/assets/mileage/settings-ranges.js" defer></script>
                  <script src="/assets/mileage/settings-create.js" defer></script>
                  <script src="/assets/mileage/settings-admin.js" defer></script>
                  <script src="/assets/mileage/settings-tables.js" defer></script>
                  <script src="/assets/mileage/settings.js" defer></script>
                </head>
                <body>
                  <div class="app-shell">
                """
                + nav(admin, activeTab)
                + """
                    <main class="workspace">
                """
                + minePanel("mine".equals(activeTab))
                + (admin ? adminPanels(activeTab) : "")
                + """
                    </main>
                  </div>
                  <div class="toast-container" id="toast-container" aria-live="polite"></div>
                </body>
                </html>
                """;
    }

    private static String nav(boolean admin, String activeTab) {
        return """
                    <nav class="side-nav">
                      <div class="brand">
                        <img class="brand-mark" src="/assets/mileage/icon.png" alt="" width="32" height="32">
                        <span><strong>Mileage</strong><small>for Clockify</small></span>
                      </div>
                      <div class="nav-tabs" role="tablist" aria-orientation="vertical" aria-label="Mileage sections">
                """
                + navButton("mine", "Mine", ICON_MINE, activeTab, false)
                + (admin
                        ? navButton("team", "Team", ICON_TEAM, activeTab, true)
                        + navButton("admin-settings", "Settings", ICON_SETTINGS, activeTab, true)
                        + navButton("conversion-log", "Conversions", ICON_CONVERSIONS, activeTab, true)
                        + navButton("diagnostics", "Diagnostics", ICON_DIAGNOSTICS, activeTab, true)
                        : "")
                + """
                      </div>
                    </nav>
                """;
    }

    private static String navButton(String tab, String label, String icon, String activeTab, boolean adminOnly) {
        boolean active = tab.equals(activeTab);
        return ("      <button class=\"nav-button%s\" type=\"button\" role=\"tab\" id=\"tab-btn-%s\""
                + " data-tab-target=\"%s\" aria-controls=\"tab-%s\" aria-selected=\"%s\" tabindex=\"%s\"%s>%s<span>%s</span></button>%n")
                .formatted(
                        active ? " active" : "",
                        tab,
                        tab,
                        tab,
                        active ? "true" : "false",
                        active ? "0" : "-1",
                        adminOnly ? " data-admin-only=\"true\"" : "",
                        icon,
                        label);
    }

    /** Opening tag for a tab panel, wired as an ARIA tabpanel labelled by its nav tab button. */
    private static String panelOpen(String tab, boolean active, boolean adminOnly) {
        return ("      <section class=\"tab-panel%s\"%s id=\"tab-%s\" role=\"tabpanel\""
                + " aria-labelledby=\"tab-btn-%s\" tabindex=\"0\"%s>%n")
                .formatted(
                        active ? " active" : "",
                        active ? "" : " hidden",
                        tab,
                        tab,
                        adminOnly ? " data-admin-only=\"true\"" : "");
    }

    private static String minePanel(boolean active) {
        return panelOpen("mine", active, false)
                + """
                        <header class="panel-heading">
                          <div>
                            <h1>Mine</h1>
                            <p>Submit mileage and review your own mileage rows.</p>
                          </div>
                          <span id="create-status" class="status-text"></span>
                        </header>
                        <div class="context-strip" id="create-context">Loading workspace mileage settings...</div>
                        <form id="mileage-form" class="form-grid">
                          <label><span>Date <abbr class="req" title="required" aria-hidden="true">*</abbr></span><input id="field-date" name="date" type="date" required aria-required="true"></label>
                          <label><span>Project</span><input id="field-project" name="projectId" list="project-options" autocomplete="off" placeholder="Type to search projects (optional)"><datalist id="project-options"></datalist></label>
                          <label><span>Miles <abbr class="req" title="required" aria-hidden="true">*</abbr></span><input id="field-miles" name="miles" inputmode="decimal" required aria-required="true"></label>
                          <label id="rate-field-row"><span>Rate override</span><input id="field-rate" name="rate" inputmode="decimal" disabled></label>
                          <label class="wide"><span>Notes</span><textarea id="field-notes" name="notes" rows="4"></textarea></label>
                          <label class="check-row"><input id="field-billable" name="billable" type="checkbox" checked><span>Billable</span></label>
                          <label class="wide"><span>Receipt</span><input id="field-receipt" name="file" type="file" accept="image/png,image/jpeg,image/gif,image/webp,image/heic,application/pdf"></label>
                          <div class="actions wide">
                            <button type="button" id="btn-preview" disabled>Preview</button>
                            <button type="submit" disabled>Create Expense</button>
                          </div>
                        </form>
                        <div class="result-strip" id="preview-result" aria-live="polite"></div>
                        <section class="history-panel" aria-labelledby="mine-history-title">
                          <div class="toolbar">
                            <div>
                              <h2 id="mine-history-title">My Mileage</h2>
                              <p>Calculated amount keeps every decimal. Expense amount is what Clockify receives.</p>
                            </div>
                            <div class="list-actions">
                """
                + dateRangeControls("mine")
                + """
                              <div class="actions">
                                <button type="button" id="btn-refresh-mine">Refresh</button>
                                <button type="button" id="btn-export-mine">CSV</button>
                                <button type="button" id="btn-report-mine">Report</button>
                              </div>
                            </div>
                          </div>
                          <div class="table-wrap"><table><thead><tr><th scope="col">Date</th><th scope="col">Expense</th><th scope="col">Source</th><th scope="col">Status</th><th scope="col">Miles</th><th scope="col">Rate</th><th scope="col">Amount</th><th scope="col">Updated</th></tr></thead><tbody id="mine-rows"></tbody></table></div>
                          <div class="pager" id="mine-pager" hidden></div>
                        </section>
                      </section>
                """;
    }

    private static String adminPanels(String activeTab) {
        return teamPanel("team".equals(activeTab))
                + settingsPanel("admin-settings".equals(activeTab))
                + conversionsPanel("conversion-log".equals(activeTab))
                + diagnosticsPanel("diagnostics".equals(activeTab));
    }

    private static String teamPanel(boolean active) {
        return panelOpen("team", active, true)
                + """
                        <header class="panel-heading">
                          <div>
                            <h1>Team</h1>
                            <p>All mileage rows for this workspace.</p>
                          </div>
                          <div class="list-actions">
                """
                + userFilterControls("team")
                + dateRangeControls("team")
                + """
                            <div class="actions">
                              <button type="button" id="btn-refresh-team">Refresh</button>
                              <button type="button" id="btn-export-team">CSV</button>
                              <button type="button" id="btn-report-team">Report</button>
                            </div>
                          </div>
                        </header>
                        <div class="table-wrap"><table><thead><tr><th scope="col">Date</th><th scope="col">Expense</th><th scope="col">User</th><th scope="col">Source</th><th scope="col">Status</th><th scope="col">Miles</th><th scope="col">Rate</th><th scope="col">Amount</th><th scope="col">Updated</th></tr></thead><tbody id="team-rows"></tbody></table></div>
                        <div class="pager" id="team-pager" hidden></div>
                      </section>
                """;
    }

    private static String settingsPanel(boolean active) {
        return panelOpen("admin-settings", active, true)
                + """
                        <header class="panel-heading"><h1>Workspace Settings</h1><span id="settings-status" class="status-text"></span></header>
                        <form id="settings-form" class="form-grid">
                          <fieldset class="wide settings-group">
                            <legend>Mileage setup</legend>
                            <div class="form-grid compact">
                              <label><span>Rate</span><input id="settings-rate" name="rate" inputmode="decimal"><span class="hint" id="settings-rate-hint" hidden></span></label>
                              <label><span>Mileage category</span><select id="settings-mileage-category" name="mileageCategoryId"></select></label>
                              <div class="actions align-end"><button type="button" id="btn-setup-mileage-category">Use or Repair Mileage Category</button></div>
                              <label class="check-row"><input id="settings-enabled" name="enabled" type="checkbox"><span>Enabled</span></label>
                              <label class="check-row"><input id="settings-convert-create" name="convertOnCreate" type="checkbox"><span>Convert created native expenses</span></label>
                              <label class="check-row"><input id="settings-convert-update" name="convertOnUpdate" type="checkbox"><span>Convert updated native expenses</span></label>
                              <label class="check-row"><input id="settings-rate-override" name="allowUserRateOverride" type="checkbox"><span>Allow rate override</span></label>
                            </div>
                          </fieldset>
                          <fieldset class="wide settings-group">
                            <legend>Expense note</legend>
                            <label class="wide"><span>Note template</span><textarea id="settings-note-template" name="noteTemplate" rows="3" placeholder="Mileage reimbursement: {{miles}} {{unit}} x {{rate}} = {{calculatedAmount}}{{categoryCharge}}. Created/converted by Mileage for Clockify."></textarea></label>
                            <p class="hint">Tokens: {{miles}} {{unit}} {{rate}} {{calculatedAmount}} {{amount}} {{categoryCharge}}. Leave blank for the default note. A hidden loop-safety marker is added automatically unless your template already includes the standard signature line, so conversions never re-convert.</p>
                            <div class="note-preview wide" id="settings-note-preview" hidden><span class="note-preview-label">Sample note</span><span id="settings-note-preview-text"></span></div>
                          </fieldset>
                          <div class="actions wide"><button type="submit">Save Settings</button></div>
                        </form>
                      </section>
                """;
    }

    private static String conversionsPanel(boolean active) {
        return panelOpen("conversion-log", active, true)
                + """
                        <header class="panel-heading">
                          <div>
                            <h1>Conversions</h1>
                            <p>Technical audit trail for native and add-on mileage conversions.</p>
                          </div>
                          <div class="list-actions">
                """
                + userFilterControls("conversion")
                + dateRangeControls("conversion")
                + """
                            <div class="actions">
                              <button type="button" id="btn-refresh-conversions">Refresh</button>
                              <button type="button" id="btn-export-conversions">CSV</button>
                            </div>
                          </div>
                        </header>
                        <div class="table-wrap"><table><thead><tr><th scope="col">Date</th><th scope="col">Expense</th><th scope="col">Source</th><th scope="col">User</th><th scope="col">Status</th><th scope="col">Miles</th><th scope="col">Rate</th><th scope="col">Amount</th><th scope="col">Updated</th></tr></thead><tbody id="conversion-rows"></tbody></table></div>
                        <div class="pager" id="conversion-pager" hidden></div>
                      </section>
                """;
    }

    private static String userFilterControls(String prefix) {
        return """
                              <div class="user-filter">
                                <label><span>User</span><select id="%1$s-user-filter"><option value="">All users</option></select></label>
                              </div>
                """.formatted(prefix);
    }

    private static String dateRangeControls(String prefix) {
        return """
                              <div class="range-controls" data-range-scope="%1$s">
                                <label><span>Range</span><select id="%1$s-range-preset">
                                  <option value="this_week" selected>This week</option>
                                  <option value="custom">Custom</option>
                                  <option value="this_month">This month</option>
                                  <option value="last_week">Last week</option>
                                  <option value="last_month">Last month</option>
                                  <option value="this_year">This year</option>
                                  <option value="last_year">Last year</option>
                                </select></label>
                                <div class="range-custom" id="%1$s-range-custom" hidden>
                                  <label><span>From</span><input id="%1$s-range-from" type="date"></label>
                                  <label><span>To</span><input id="%1$s-range-to" type="date"></label>
                                </div>
                              </div>
                """.formatted(prefix);
    }

    private static String diagnosticsPanel(boolean active) {
        return panelOpen("diagnostics", active, true)
                + """
                        <header class="panel-heading"><h1>Diagnostics</h1><div class="actions"><button type="button" id="btn-refresh-diagnostics">Refresh</button></div></header>
                        <dl class="diagnostics" id="diagnostics-list"></dl>
                        <ul class="warnings" id="diagnostics-warnings"></ul>
                      </section>
                """;
    }

    // Inline, decorative (aria-hidden) section icons. Stroke icons that inherit currentColor via the
    // .nav-ico class — no inline style attribute, no <style>/<script>, so the CSP-safe contract holds.
    private static final String ICON_MINE =
            "<svg class=\"nav-ico\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\""
                    + " stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">"
                    + "<circle cx=\"12\" cy=\"8\" r=\"4\"/><path d=\"M4 21c0-4 4-6 8-6s8 2 8 6\"/></svg>";
    private static final String ICON_TEAM =
            "<svg class=\"nav-ico\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\""
                    + " stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">"
                    + "<circle cx=\"9\" cy=\"8\" r=\"3.2\"/><path d=\"M2.5 20c0-3.6 3-5.5 6.5-5.5s6.5 1.9 6.5 5.5\"/>"
                    + "<path d=\"M16.5 5.2a3.2 3.2 0 0 1 0 6\"/><path d=\"M18 14.8c2.3.6 3.5 2.3 3.5 5.2\"/></svg>";
    private static final String ICON_SETTINGS =
            "<svg class=\"nav-ico\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\""
                    + " stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">"
                    + "<path d=\"M4 7h8\"/><path d=\"M16 7h4\"/><circle cx=\"14\" cy=\"7\" r=\"2\"/>"
                    + "<path d=\"M4 17h4\"/><path d=\"M12 17h8\"/><circle cx=\"10\" cy=\"17\" r=\"2\"/></svg>";
    private static final String ICON_CONVERSIONS =
            "<svg class=\"nav-ico\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\""
                    + " stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">"
                    + "<path d=\"M4 9a8 8 0 0 1 13.7-4.2L20 7\"/><path d=\"M20 4v3h-3\"/>"
                    + "<path d=\"M20 15a8 8 0 0 1-13.7 4.2L4 17\"/><path d=\"M4 20v-3h3\"/></svg>";
    private static final String ICON_DIAGNOSTICS =
            "<svg class=\"nav-ico\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\""
                    + " stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\">"
                    + "<path d=\"M3 12h4l2.5 7 5-14 2.5 7H21\"/></svg>";
}
