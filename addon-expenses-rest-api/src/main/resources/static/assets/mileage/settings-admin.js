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
    loadDiagnostics
  });
})();
