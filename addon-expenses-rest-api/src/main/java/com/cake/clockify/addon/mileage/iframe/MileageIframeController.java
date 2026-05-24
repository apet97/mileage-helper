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
                  <link rel="stylesheet" href="/assets/mileage/settings.css">
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
                    <nav class="side-nav" aria-label="Mileage sections">
                      <div class="brand">
                        <span class="brand-mark">M</span>
                        <span><strong>Mileage</strong><small>for Clockify</small></span>
                      </div>
                """
                + navButton("mine", "Mine", activeTab, false)
                + (admin
                        ? navButton("team", "Team", activeTab, true)
                        + navButton("admin-settings", "Settings", activeTab, true)
                        + navButton("conversion-log", "Conversions", activeTab, true)
                        + navButton("diagnostics", "Diagnostics", activeTab, true)
                        : "")
                + """
                    </nav>
                """;
    }

    private static String navButton(String tab, String label, String activeTab, boolean adminOnly) {
        return "      <button class=\"nav-button%s\" type=\"button\" data-tab-target=\"%s\"%s>%s</button>%n"
                .formatted(tab.equals(activeTab) ? " active" : "", tab, adminOnly ? " data-admin-only=\"true\"" : "", label);
    }

    private static String minePanel(boolean active) {
        return """
                      <section class="tab-panel%s" id="tab-mine">
                        <header class="panel-heading">
                          <div>
                            <h1>Mine</h1>
                            <p>Submit mileage and review your own mileage rows.</p>
                          </div>
                          <span id="create-status" class="status-text"></span>
                        </header>
                        <div class="context-strip" id="create-context">Loading workspace mileage settings...</div>
                        <form id="mileage-form" class="form-grid">
                          <label><span>Date</span><input id="field-date" name="date" type="date" required></label>
                          <label><span>Project</span><select id="field-project" name="projectId"><option value="">No project</option></select></label>
                          <label><span>Miles</span><input id="field-miles" name="miles" inputmode="decimal" required></label>
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
                              </div>
                            </div>
                          </div>
                          <div class="table-wrap"><table><thead><tr><th>Date</th><th>Expense</th><th>Source</th><th>Status</th><th>Miles</th><th>Rate</th><th>Amount</th><th>Updated</th></tr></thead><tbody id="mine-rows"></tbody></table></div>
                        </section>
                      </section>
                """.formatted(active ? " active" : "");
    }

    private static String adminPanels(String activeTab) {
        return teamPanel("team".equals(activeTab))
                + settingsPanel("admin-settings".equals(activeTab))
                + conversionsPanel("conversion-log".equals(activeTab))
                + diagnosticsPanel("diagnostics".equals(activeTab));
    }

    private static String teamPanel(boolean active) {
        return """
                      <section class="tab-panel%s" id="tab-team" data-admin-only="true">
                        <header class="panel-heading">
                          <div>
                            <h1>Team</h1>
                            <p>All mileage rows for this workspace.</p>
                          </div>
                          <div class="list-actions">
                """
                + dateRangeControls("team")
                + """
                            <div class="actions">
                              <button type="button" id="btn-refresh-team">Refresh</button>
                              <button type="button" id="btn-export-team">CSV</button>
                            </div>
                          </div>
                        </header>
                        <div class="table-wrap"><table><thead><tr><th>Date</th><th>Expense</th><th>User</th><th>Source</th><th>Status</th><th>Miles</th><th>Rate</th><th>Amount</th><th>Updated</th></tr></thead><tbody id="team-rows"></tbody></table></div>
                      </section>
                """.formatted(active ? " active" : "");
    }

    private static String settingsPanel(boolean active) {
        return """
                      <section class="tab-panel%s" id="tab-admin-settings" data-admin-only="true">
                        <header class="panel-heading"><h1>Workspace Settings</h1><span id="settings-status" class="status-text"></span></header>
                        <form id="settings-form" class="form-grid">
                          <fieldset class="wide settings-group">
                            <legend>Mileage setup</legend>
                            <div class="form-grid compact">
                              <label><span>Rate</span><input id="settings-rate" name="rate" inputmode="decimal"></label>
                              <label><span>Mileage category</span><select id="settings-mileage-category" name="mileageCategoryId"></select></label>
                              <div class="actions align-end"><button type="button" id="btn-setup-mileage-category">Create or Repair Mileage Category</button></div>
                              <label class="check-row"><input id="settings-enabled" name="enabled" type="checkbox"><span>Enabled</span></label>
                              <label class="check-row"><input id="settings-convert-create" name="convertOnCreate" type="checkbox"><span>Convert created native expenses</span></label>
                              <label class="check-row"><input id="settings-convert-update" name="convertOnUpdate" type="checkbox"><span>Convert updated native expenses</span></label>
                              <label class="check-row"><input id="settings-rate-override" name="allowUserRateOverride" type="checkbox"><span>Allow rate override</span></label>
                            </div>
                          </fieldset>
                          <div class="actions wide"><button type="submit">Save Settings</button></div>
                        </form>
                      </section>
                """.formatted(active ? " active" : "");
    }

    private static String conversionsPanel(boolean active) {
        return """
                      <section class="tab-panel%s" id="tab-conversion-log" data-admin-only="true">
                        <header class="panel-heading">
                          <div>
                            <h1>Conversions</h1>
                            <p>Technical audit trail for native and add-on mileage conversions.</p>
                          </div>
                          <div class="list-actions">
                """
                + dateRangeControls("conversion")
                + """
                            <div class="actions">
                              <button type="button" id="btn-refresh-conversions">Refresh</button>
                              <button type="button" id="btn-export-conversions">CSV</button>
                            </div>
                          </div>
                        </header>
                        <div class="table-wrap"><table><thead><tr><th>Date</th><th>Expense</th><th>Source</th><th>User</th><th>Status</th><th>Miles</th><th>Rate</th><th>Amount</th><th>Updated</th></tr></thead><tbody id="conversion-rows"></tbody></table></div>
                      </section>
                """.formatted(active ? " active" : "");
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
        return """
                      <section class="tab-panel%s" id="tab-diagnostics" data-admin-only="true">
                        <header class="panel-heading"><h1>Diagnostics</h1><button type="button" id="btn-refresh-diagnostics">Refresh</button></header>
                        <dl class="diagnostics" id="diagnostics-list"></dl>
                        <ul class="warnings" id="diagnostics-warnings"></ul>
                      </section>
                """.formatted(active ? " active" : "");
    }
}
