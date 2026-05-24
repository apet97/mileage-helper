package com.cake.clockify.addon.mileage.iframe;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MileageIframeController {
    private static final String HTML = """
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
                <nav class="side-nav" aria-label="Mileage sections">
                  <div class="brand">
                    <span class="brand-mark">M</span>
                    <span><strong>Mileage</strong><small>for Clockify</small></span>
                  </div>
                  <button class="nav-button active" type="button" data-tab-target="create">Create</button>
                  <button class="nav-button" type="button" data-tab-target="mine">Mine</button>
                  <button class="nav-button" type="button" data-tab-target="admin-settings" data-admin-only="true">Settings</button>
                  <button class="nav-button" type="button" data-tab-target="conversion-log" data-admin-only="true">Conversions</button>
                  <button class="nav-button" type="button" data-tab-target="diagnostics" data-admin-only="true">Diagnostics</button>
                </nav>
                <main class="workspace">
                  <section class="tab-panel active" id="tab-create">
                    <header class="panel-heading"><h1>Create Mileage Expense</h1><span id="create-status" class="status-text"></span></header>
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
                  </section>
                  <section class="tab-panel" id="tab-mine">
                    <header class="panel-heading"><h1>Recent Activity</h1></header>
                    <div class="empty-state" id="user-activity">Mileage submissions from this session appear here.</div>
                  </section>
                  <section class="tab-panel" id="tab-admin-settings">
                    <header class="panel-heading"><h1>Workspace Settings</h1><span id="settings-status" class="status-text"></span></header>
                    <form id="settings-form" class="form-grid">
                      <fieldset class="wide settings-group">
                        <legend>Manual mileage</legend>
                        <div class="form-grid compact">
                          <label><span>Rate</span><input id="settings-rate" name="rate" inputmode="decimal"></label>
                          <label><span>Unit</span><input id="settings-unit" name="unit" autocomplete="off"></label>
                          <label><span>Output category</span><select id="settings-output-category" name="outputCategoryId"></select></label>
                          <label><span>Rounding</span><select id="settings-rounding" name="roundingMode"><option>HALF_UP</option><option>HALF_EVEN</option><option>DOWN</option><option>UP</option></select></label>
                          <label class="check-row"><input id="settings-enabled" name="enabled" type="checkbox"><span>Enabled</span></label>
                          <label class="check-row"><input id="settings-rate-override" name="allowUserRateOverride" type="checkbox"><span>Allow rate override</span></label>
                        </div>
                      </fieldset>
                      <fieldset class="wide settings-group">
                        <legend>Native expense conversion</legend>
                        <div class="form-grid compact">
                          <label><span>Input category</span><select id="settings-input-category" name="inputCategoryId"></select></label>
                          <label class="check-row"><input id="settings-convert-create" name="convertOnCreate" type="checkbox"><span>Convert created native expenses</span></label>
                          <label class="check-row"><input id="settings-convert-update" name="convertOnUpdate" type="checkbox"><span>Convert updated native expenses</span></label>
                          <label class="check-row"><input id="settings-preserve-notes" name="preserveOriginalNotes" type="checkbox"><span>Preserve notes</span></label>
                          <label class="check-row"><input id="settings-dry-run" name="dryRunMode" type="checkbox"><span>Dry run</span></label>
                          <label class="wide"><span>Note template</span><textarea id="settings-template" name="noteTemplate" rows="3"></textarea></label>
                        </div>
                      </fieldset>
                      <div class="actions wide"><button type="submit">Save Settings</button></div>
                    </form>
                  </section>
                  <section class="tab-panel" id="tab-conversion-log">
                    <header class="panel-heading"><h1>Conversion Log</h1><button type="button" id="btn-refresh-conversions">Refresh</button></header>
                    <div class="table-wrap"><table><thead><tr><th>Expense</th><th>Status</th><th>Miles</th><th>Amount</th><th>Updated</th></tr></thead><tbody id="conversion-rows"></tbody></table></div>
                  </section>
                  <section class="tab-panel" id="tab-diagnostics">
                    <header class="panel-heading"><h1>Diagnostics</h1><button type="button" id="btn-refresh-diagnostics">Refresh</button></header>
                    <dl class="diagnostics" id="diagnostics-list"></dl>
                    <ul class="warnings" id="diagnostics-warnings"></ul>
                  </section>
                </main>
              </div>
              <div class="toast-container" id="toast-container" aria-live="polite"></div>
            </body>
            </html>
            """;

    @GetMapping(value = "/iframe/mileage", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> mileage() {
        return ResponseEntity.ok(HTML);
    }

    @GetMapping(value = "/iframe/settings", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> settings() {
        return ResponseEntity.ok(HTML);
    }
}
