(function () {
  const app = window.MileageSettingsApp;
  const DEFAULT_NOTE_TEMPLATE =
    "Mileage reimbursement: {{miles}} {{unit}} x {{rate}} = {{calculatedAmount}}{{categoryCharge}}. "
    + "Created/converted by Mileage for Clockify.";

  function loadCategories() {
    return app.apiFetch("/api/mileage/options/categories").then(data => {
      const categorySelect = app.element("settings-mileage-category");
      if (!categorySelect) {
        return;
      }
      app.state.defaultMileageCategory = null;
      categorySelect.replaceChildren();
      appendOption(categorySelect, "", "Choose Mileage category");
      (data.categories || []).forEach(category => {
        const rate = centsToRate(category.unitPrice);
        const option = Object.assign({}, category, { rate });
        if (isDefaultMileageCategory(option)) {
          app.state.defaultMileageCategory = option;
        }
        const rateText = rate ? ", " + rate + "/" + category.unit : "";
        const suffix = category.unit ? " (" + category.type + ": " + category.unit + rateText + ")" : " (" + category.type + ")";
        appendOption(categorySelect, category.id, category.name + suffix);
      });
      if (data.warning) {
        appendOption(categorySelect, "", "Category list unavailable");
        app.toast(data.warning, "error");
      }
    });
  }

  function appendOption(select, value, label) {
    select.appendChild(new Option(label, value || ""));
  }

  function ensureCategoryOption(select, value, label) {
    if (!select || !value) {
      return;
    }
    const exists = Array.from(select.options).some(option => option.value === value);
    if (!exists) {
      appendOption(select, value, label || "Configured Mileage category");
    }
  }

  function isDefaultMileageCategory(category) {
    return category
      && String(category.name || "").toLowerCase() === "mileage"
      && String(category.type || "").toLowerCase() === "unit"
      && String(category.unit || "").toLowerCase() === "mile"
      && Boolean(category.rate);
  }

  function centsToRate(value) {
    const raw = String(value || "").trim();
    if (!raw) {
      return "";
    }
    const cents = raw.split(".")[0];
    if (!/^\d+$/.test(cents)) {
      return "";
    }
    const padded = cents.padStart(3, "0");
    const whole = padded.slice(0, -2).replace(/^0+(?=\d)/, "") || "0";
    const fraction = padded.slice(-2).replace(/0+$/, "");
    return fraction ? whole + "." + fraction : whole;
  }

  function loadUserOptions() {
    const selects = [app.element("team-user-filter"), app.element("conversion-user-filter")].filter(Boolean);
    if (!selects.length) {
      return Promise.resolve();
    }
    return app.apiFetch("/api/mileage/options/users").then(data => {
      selects.forEach(select => {
        const current = select.value;
        select.replaceChildren();
        appendOption(select, "", "All users");
        (data.users || []).forEach(user => appendOption(select, user.id, user.name || user.id));
        select.value = current;
      });
      if (data.warning) {
        app.toast(data.warning, "error");
      }
    }).catch(error => app.toast(error.message, "error"));
  }

  function loadSettings() {
    if (!app.element("settings-form")) {
      return Promise.resolve();
    }
    const settingsPromise = app.apiFetch("/api/mileage/settings");
    const categoriesPromise = loadCategories().catch(error => {
      app.toast("Mileage categories could not be loaded: " + error.message, "error");
    });
    return settingsPromise
      .then(settings => {
        app.element("settings-enabled").checked = settings.enabled;
        app.element("settings-rate").value = settings.rate || "";
        return categoriesPromise.then(() => {
          const selectedCategory = settings.mileageCategoryId || settings.inputCategoryId || settings.outputCategoryId || "";
          const categorySelect = app.element("settings-mileage-category");
          ensureCategoryOption(categorySelect, selectedCategory, settings.mileageCategoryName || "Configured Mileage category");
          categorySelect.value = selectedCategory;
          if (!selectedCategory && app.state.defaultMileageCategory) {
            categorySelect.value = app.state.defaultMileageCategory.id || "";
          }
          if (!settings.rate && app.state.defaultMileageCategory && app.state.defaultMileageCategory.rate) {
            app.element("settings-rate").value = app.state.defaultMileageCategory.rate;
          }
          app.element("settings-convert-create").checked = settings.convertOnCreate;
          app.element("settings-convert-update").checked = settings.convertOnUpdate;
          app.element("settings-rate-override").checked = settings.allowUserRateOverride;
          app.element("settings-note-template").value = settings.noteTemplate || "";
          app.element("settings-status").textContent = settings.completeForNativeConversion
            ? "Ready"
            : app.state.defaultMileageCategory ? "Default Mileage found" : "Needs configuration";
          applyRateDefaultHint();
          renderNotePreview();
          return loadRatePolicies();
        });
      })
      .catch(error => app.toast(error.message, "error"));
  }

  // S-1: when no rate is saved (and none derived from a default category), pre-fill the visible default
  // rate the rest of the app already uses (create-context's effective 0.725) so Settings never reads as
  // "my rate vanished" while Mine shows a workspace rate.
  function applyRateDefaultHint() {
    const rateInput = app.element("settings-rate");
    const rateHint = app.element("settings-rate-hint");
    if (!rateInput) {
      return;
    }
    if (rateInput.value) {
      if (rateHint) {
        rateHint.hidden = true;
      }
      return;
    }
    const apply = rate => {
      if (rate && !rateInput.value) {
        rateInput.value = rate;
        if (rateHint) {
          rateHint.textContent = "Showing the workspace default " + rate + " — Save to store it.";
          rateHint.hidden = false;
        }
        renderNotePreview();
      }
    };
    if (app.state.createContext.rate) {
      apply(app.state.createContext.rate);
      return;
    }
    app.apiFetch("/api/mileage/create-context")
      .then(data => {
        app.applyCreateContext(data);
        apply(app.state.createContext.rate);
      })
      .catch(() => {});
  }

  function renderNotePreview() {
    const textarea = app.element("settings-note-template");
    const box = app.element("settings-note-preview");
    const target = app.element("settings-note-preview-text");
    if (!textarea || !box || !target) {
      return;
    }
    const rate = app.formValue("settings-rate") || app.state.createContext.rate || "0.725";
    const miles = "10";
    const amount = sampleAmount(miles, rate);
    const values = {
      miles: miles,
      unit: "miles",
      rate: app.trimDecimal(rate),
      calculatedAmount: amount,
      amount: amount,
      categoryCharge: ""
    };
    const template = textarea.value.trim() || DEFAULT_NOTE_TEMPLATE;
    target.textContent = renderTemplate(template, values);
    box.hidden = false;
  }

  function renderTemplate(template, values) {
    return template.replace(/\{\{(\w+)\}\}/g, (match, key) =>
      Object.prototype.hasOwnProperty.call(values, key) ? values[key] : "");
  }

  function sampleAmount(miles, rate) {
    const product = Number(miles) * Number(rate);
    if (!Number.isFinite(product)) {
      return "";
    }
    return app.trimDecimal(String(Math.round(product * 1e6) / 1e6));
  }

  function saveSettings(event) {
    event.preventDefault();
    const button = document.querySelector("#settings-form button[type='submit']");
    app.setBusy(button, true, "Saving...");
    const payload = {
      enabled: app.element("settings-enabled").checked,
      rate: app.formValue("settings-rate"),
      mileageCategoryId: app.formValue("settings-mileage-category") || null,
      convertOnCreate: app.element("settings-convert-create").checked,
      convertOnUpdate: app.element("settings-convert-update").checked,
      allowUserRateOverride: app.element("settings-rate-override").checked,
      noteTemplate: app.formValue("settings-note-template")
    };
    return app.apiFetch("/api/mileage/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }).then(settings => {
      const warnings = settings && Array.isArray(settings.warnings) ? settings.warnings : [];
      if (warnings.length) {
        warnings.forEach(message => app.toast(message, "error"));
      } else {
        app.toast("Settings saved.");
      }
      app.loadCreateContext();
      app.loadDiagnostics();
    }).catch(error => app.toast(error.message, "error")).finally(() => app.setBusy(button, false));
  }

  function setupMileageCategory() {
    const button = app.element("btn-setup-mileage-category");
    if (button && button.disabled) {
      return;
    }
    if (button) {
      button.disabled = true;
      button.setAttribute("aria-busy", "true");
      button.textContent = "Repairing...";
    }
    app.apiFetch("/api/mileage/settings/mileage-category", { method: "POST" })
      .then(settings => {
        app.toast("Mileage category is ready.");
        return loadCategories().then(() => {
          const categorySelect = app.element("settings-mileage-category");
          if (categorySelect) {
            ensureCategoryOption(categorySelect, settings.mileageCategoryId || "", settings.mileageCategoryName || "Mileage");
            categorySelect.value = settings.mileageCategoryId || "";
          }
          loadSettings();
          app.loadCreateContext();
          app.loadDiagnostics();
        });
      })
      .catch(error => app.toast(error.message, "error"))
      .finally(() => {
        if (button) {
          button.disabled = false;
          button.removeAttribute("aria-busy");
          button.textContent = "Use or Repair Mileage Category";
        }
      });
  }

  function loadRatePolicies() {
    const rows = app.element("rate-policy-rows");
    const status = app.element("rate-policy-status");
    const warnings = app.element("rate-policy-warnings");
    if (!rows) {
      return Promise.resolve();
    }
    rows.replaceChildren();
    app.renderLoadingRow(rows, 7);
    if (status) {
      status.textContent = "Loading policies...";
    }
    if (warnings) {
      warnings.replaceChildren();
    }
    return app.apiFetch("/api/mileage/rate-policies")
      .then(data => {
        const policies = data.policies || [];
        rows.replaceChildren();
        if (data.warning && warnings) {
          const item = document.createElement("li");
          item.textContent = data.warning;
          warnings.appendChild(item);
        }
        if (!policies.length) {
          app.renderEmptyRow(rows, 7, "No rate policies yet.", "");
        } else {
          policies.forEach(policy => appendRatePolicyRow(rows, policy));
        }
        if (status) {
          status.textContent = policies.length + " policies";
        }
      })
      .catch(error => {
        app.renderErrorRow(rows, 7);
        if (status) {
          status.textContent = "Could not load policies";
        }
        app.toast(error.message, "error");
      });
  }

  function appendRatePolicyRow(rows, policy) {
    const row = rows.insertRow();
    row.dataset.policyId = policy.id || "";
    row.dataset.policy = JSON.stringify(policy);
    app.appendTextCell(row, policy.name);
    app.appendTextCell(row, policy.rate);
    app.appendTextCell(row, app.formatExpenseDate(policy.effectiveFrom));
    app.appendTextCell(row, policy.effectiveTo ? app.formatExpenseDate(policy.effectiveTo) : "Open");
    app.appendTextCell(row, policy.active ? "Active" : "Inactive");
    app.appendTextCell(row, app.formatDate(policy.updatedAt));
    const actions = row.insertCell();
    const edit = document.createElement("button");
    edit.type = "button";
    edit.textContent = "Edit";
    edit.dataset.policyAction = "edit";
    edit.dataset.policyId = policy.id || "";
    const deactivate = document.createElement("button");
    deactivate.type = "button";
    deactivate.textContent = "Deactivate";
    deactivate.dataset.policyAction = "deactivate";
    deactivate.dataset.policyId = policy.id || "";
    deactivate.disabled = !policy.active;
    actions.append(edit, deactivate);
    app.labelRow(row, ["Policy", "Rate", "From", "To", "Status", "Updated", "Actions"]);
  }

  function saveRatePolicy(event) {
    event.preventDefault();
    const button = app.element("btn-save-rate-policy");
    app.setBusy(button, true, "Saving...");
    const id = app.formValue("rate-policy-id");
    const payload = {
      name: app.formValue("rate-policy-name"),
      rate: app.formValue("rate-policy-rate"),
      effectiveFrom: app.formValue("rate-policy-effective-from") || null,
      effectiveTo: app.formValue("rate-policy-effective-to") || null,
      active: app.element("rate-policy-active") ? app.element("rate-policy-active").checked : true
    };
    const path = id ? "/api/mileage/rate-policies/" + encodeURIComponent(id) : "/api/mileage/rate-policies";
    const method = id ? "PUT" : "POST";
    return app.apiFetch(path, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }).then(() => {
      clearRatePolicyForm();
      app.toast("Rate policy saved.");
      return loadRatePolicies();
    }).catch(error => app.toast(error.message, "error")).finally(() => app.setBusy(button, false));
  }

  function handleRatePolicyTableClick(event) {
    const button = event.target.closest("[data-policy-action]");
    if (!button) {
      return;
    }
    const action = button.dataset.policyAction;
    const id = button.dataset.policyId;
    if (!id) {
      return;
    }
    if (action === "edit") {
      editRatePolicy(id);
    } else if (action === "deactivate") {
      deactivateRatePolicy(id, button);
    }
  }

  function editRatePolicy(id) {
    const row = Array.from(document.querySelectorAll("#rate-policy-rows tr"))
      .find(candidate => candidate.dataset.policyId === id);
    if (!row || !row.dataset.policy) {
      return;
    }
    const policy = JSON.parse(row.dataset.policy);
    app.element("rate-policy-id").value = policy.id || "";
    app.element("rate-policy-name").value = policy.name || "";
    app.element("rate-policy-rate").value = policy.rate || "";
    app.element("rate-policy-effective-from").value = policy.effectiveFrom || "";
    app.element("rate-policy-effective-to").value = policy.effectiveTo || "";
    app.element("rate-policy-active").checked = Boolean(policy.active);
    app.element("btn-save-rate-policy").textContent = "Save policy";
    app.focusField("rate-policy-name");
  }

  function deactivateRatePolicy(id, button) {
    app.setBusy(button, true, "Deactivating...");
    return app.apiFetch("/api/mileage/rate-policies/" + encodeURIComponent(id), { method: "DELETE" })
      .then(() => {
        app.toast("Rate policy deactivated.");
        return loadRatePolicies();
      })
      .catch(error => app.toast(error.message, "error"))
      .finally(() => app.setBusy(button, false));
  }

  function clearRatePolicyForm() {
    ["rate-policy-id", "rate-policy-name", "rate-policy-rate", "rate-policy-effective-from", "rate-policy-effective-to"]
      .forEach(id => {
        const node = app.element(id);
        if (node) {
          node.value = "";
        }
      });
    const active = app.element("rate-policy-active");
    if (active) {
      active.checked = true;
    }
    const save = app.element("btn-save-rate-policy");
    if (save) {
      save.textContent = "Save policy";
    }
  }

  function loadDiagnostics() {
    const list = app.element("diagnostics-list");
    if (!list) {
      return Promise.resolve();
    }
    renderDiagnosticsStatus(list, "Status", "Checking…", "");
    return app.apiFetch("/api/mileage/diagnostics").then(data => {
      list.replaceChildren();
      [["Installation", data.installationAvailable], ["Settings", data.settingsComplete], ["Native conversion", data.nativeConversionReady]].forEach(([label, value]) => {
        const dt = document.createElement("dt");
        const dd = document.createElement("dd");
        dt.textContent = label;
        dd.textContent = value ? "OK" : "Needs attention";
        dd.className = value ? "ok" : "warn";
        list.append(dt, dd);
      });
      const warnings = app.element("diagnostics-warnings");
      warnings.replaceChildren();
      (data.warnings || []).forEach(text => {
        const item = document.createElement("li");
        item.textContent = text;
        warnings.appendChild(item);
      });
      renderDiagnosticsChecklist(data.checklist || []);
      renderOperationalHealth(data.operationalHealth || {});
    }).catch(error => {
      renderDiagnosticsStatus(list, "Status", "Could not load diagnostics", "warn");
      app.toast(error.message, "error");
    });
  }

  function renderDiagnosticsStatus(list, label, value, className) {
    list.replaceChildren();
    const dt = document.createElement("dt");
    const dd = document.createElement("dd");
    dt.textContent = label;
    dd.textContent = value;
    if (className) {
      dd.className = className;
    }
    list.append(dt, dd);
  }

  function renderDiagnosticsChecklist(items) {
    const checklist = app.element("diagnostics-checklist");
    if (!checklist) {
      return;
    }
    checklist.replaceChildren();
    items.forEach(item => {
      const row = document.createElement("li");
      row.className = item.complete ? "ok" : "warn";
      row.textContent = (item.label || "") + " — " + (item.complete ? "OK" : (item.action || "Needs attention"));
      checklist.appendChild(row);
    });
  }

  function renderOperationalHealth(health) {
    const list = app.element("diagnostics-health");
    if (!list) {
      return;
    }
    list.replaceChildren();
    appendHealthMetric(list, "Pending jobs", health.pendingJobs);
    appendHealthMetric(list, "Claimed jobs", health.claimedJobs);
    appendHealthMetric(list, "Failed jobs", health.failedJobs);
    appendHealthMetric(list, "Oldest pending age", formatAge(health.oldestPendingAgeSeconds));
    appendHealthMetric(list, "Last completed job", health.lastCompletedJobAt || "None");
  }

  function appendHealthMetric(list, label, value) {
    const dt = document.createElement("dt");
    const dd = document.createElement("dd");
    dt.textContent = label;
    dd.textContent = value == null || value === "" ? "None" : String(value);
    list.append(dt, dd);
  }

  function formatAge(seconds) {
    if (seconds == null) {
      return "None";
    }
    const value = Number(seconds);
    if (!Number.isFinite(value)) {
      return "None";
    }
    return Math.max(0, Math.round(value)) + "s";
  }

  function loadInsights() {
    const statusRows = app.element("insights-status-rows");
    if (!statusRows) {
      return Promise.resolve();
    }
    const query = app.rangeQuery("insights");
    if (query === null) {
      return Promise.resolve();
    }
    app.renderLoadingRow(statusRows, 2);
    if (app.element("insights-skip-rows")) {
      app.renderLoadingRow(app.element("insights-skip-rows"), 2);
    }
    if (app.element("insights-project-rows")) {
      app.renderLoadingRow(app.element("insights-project-rows"), 4);
    }
    if (app.element("insights-user-rows")) {
      app.renderLoadingRow(app.element("insights-user-rows"), 4);
    }
    return app.apiFetch("/api/mileage/insights?" + query.slice(1))
      .then(renderInsights)
      .catch(error => {
        app.renderErrorRow(statusRows, 2);
        app.toast(error.message, "error");
      });
  }

  function renderInsights(data) {
    setText("insights-total-miles", app.trimDecimal(data.totalConvertedMiles));
    setText("insights-calculated-amount", data.totalCalculatedAmount || "0.00");
    setText("insights-rounded-amount", data.totalRoundedAmount || "0.00");
    setText("insights-failed-count", data.failedConversions || 0);
    setText("insights-missing-purpose", data.rowsMissingTripPurpose || 0);
    setText("insights-policy-exceptions", data.rowsWithPolicyExceptions || 0);
    renderCountRows("insights-status-rows", data.statusCounts || [], "Status", "Rows", "No status rows.");
    renderCountRows("insights-skip-rows", data.skipReasonCounts || [], "Reason", "Rows", "No skipped rows.");
    renderTopRows("insights-project-rows", data.topProjects || [], "Project");
    renderTopRows("insights-user-rows", data.topUsers || [], "User");
  }

  function setText(id, value) {
    const node = app.element(id);
    if (node) {
      node.textContent = value == null || value === "" ? "0" : String(value);
    }
  }

  function renderCountRows(id, items, keyLabel, countLabel, emptyText) {
    const rows = app.element(id);
    if (!rows) {
      return;
    }
    rows.replaceChildren();
    if (!items.length) {
      app.renderEmptyRow(rows, 2, emptyText, "");
      return;
    }
    items.forEach(item => {
      const row = rows.insertRow();
      app.appendTextCell(row, item.key);
      app.appendTextCell(row, item.count);
      app.labelRow(row, [keyLabel, countLabel]);
    });
  }

  function renderTopRows(id, items, label) {
    const rows = app.element(id);
    if (!rows) {
      return;
    }
    rows.replaceChildren();
    if (!items.length) {
      app.renderEmptyRow(rows, 4, "No converted rows.", "");
      return;
    }
    items.forEach(item => {
      const row = rows.insertRow();
      app.appendTextCell(row, item.name || item.id);
      app.appendTextCell(row, item.calculatedAmount);
      app.appendTextCell(row, app.trimDecimal(item.miles));
      app.appendTextCell(row, item.count);
      app.labelRow(row, [label, "Amount", "Miles", "Rows"]);
    });
  }

  Object.assign(app, {
    loadCategories,
    appendOption,
    ensureCategoryOption,
    centsToRate,
    loadUserOptions,
    loadSettings,
    applyRateDefaultHint,
    renderNotePreview,
    saveSettings,
    setupMileageCategory,
    loadRatePolicies,
    saveRatePolicy,
    handleRatePolicyTableClick,
    clearRatePolicyForm,
    loadDiagnostics,
    loadInsights
  });
})();
