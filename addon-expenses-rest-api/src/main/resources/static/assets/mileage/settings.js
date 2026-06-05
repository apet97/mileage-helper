(function () {
  const url = new URL(window.location.href);
  const authToken = url.searchParams.get("auth_token") || "";
  let createContext = {
    rate: null,
    unit: "mile",
    allowUserRateOverride: false,
    complete: false
  };
  let defaultMileageCategory = null;
  let userIsAdmin = false;
  let tokenClaims = {};
  const rangePresets = {
    this_week: "This week",
    custom: "Custom",
    this_month: "This month",
    last_week: "Last week",
    last_month: "Last month",
    this_year: "This year",
    last_year: "Last year"
  };
  const maxReceiptBytes = 10 * 1024 * 1024;
  const allowedReceiptTypes = new Set([
    "image/png",
    "image/jpeg",
    "image/gif",
    "image/webp",
    "image/heic",
    "application/pdf"
  ]);

  if (authToken) {
    url.searchParams.delete("auth_token");
    history.replaceState({}, document.title, url.pathname + url.search + url.hash);
  }

  function element(id) {
    return document.getElementById(id);
  }

  function authHeaders(extra) {
    const headers = Object.assign({}, extra || {});
    if (authToken) {
      headers.Authorization = "Bearer " + authToken;
    }
    return headers;
  }

  function apiFetch(path, options) {
    const init = Object.assign({}, options || {});
    init.headers = authHeaders(init.headers);
    return fetch(path, init).then(async response => {
      if (response.ok) {
        return response.status === 204 ? null : response.json();
      }
      let message = "Request failed.";
      try {
        const body = await response.json();
        if (body && body.message) {
          message = body.message;
        }
      } catch (e) {
        message = "Request failed.";
      }
      throw new Error(message);
    });
  }

  function downloadCsv(path, fallbackName) {
    if (path === null) {
      return Promise.resolve();
    }
    return fetch(path, { headers: authHeaders() }).then(async response => {
      if (!response.ok) {
        throw new Error("CSV export failed.");
      }
      const blob = await response.blob();
      const href = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = href;
      link.download = filenameFromDisposition(response.headers.get("Content-Disposition")) || fallbackName;
      link.hidden = true;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(href), 0);
    }).catch(error => toast(error.message, "error"));
  }

  function filenameFromDisposition(disposition) {
    const match = disposition && disposition.match(/filename="?([^"]+)"?/);
    return match ? match[1] : "";
  }

  function claimsFromToken() {
    if (!authToken || authToken.split(".").length < 2) {
      return {};
    }
    try {
      const base64 = authToken.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
      const json = decodeURIComponent(atob(base64).split("").map(char => {
        return "%" + ("00" + char.charCodeAt(0).toString(16)).slice(-2);
      }).join(""));
      return JSON.parse(json);
    } catch (e) {
      return {};
    }
  }

  function roleFromClaims(claims) {
    return claims.workspaceRole || claims.role || claims.wsRole || "";
  }

  function timezoneFromClaims() {
    return tokenClaims.userTimeZone || tokenClaims.timeZone || tokenClaims.userTimezone || tokenClaims.timezone || tokenClaims.tz || "";
  }

  function themeFromClaims(claims) {
    const theme = String(claims.theme || claims.uiTheme || "").toLowerCase();
    if (theme === "dark" || theme === "light") {
      return theme;
    }
    if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
      return "dark";
    }
    return "light";
  }

  function applyTheme() {
    document.documentElement.dataset.theme = themeFromClaims(tokenClaims);
  }

  function applyRoleGate() {
    const role = roleFromClaims(tokenClaims);
    const isAdmin = role === "OWNER" || role === "ADMIN";
    document.querySelectorAll("[data-admin-only]").forEach(element => {
      element.hidden = !isAdmin || (element.classList.contains("tab-panel") && !element.classList.contains("active"));
    });
    if (!isAdmin && document.querySelector(".nav-button.active")?.dataset.tabTarget !== "mine") {
      switchTab("mine");
    }
    return isAdmin;
  }

  function switchTab(tab) {
    const targetPanel = element("tab-" + tab);
    if (!targetPanel) {
      return;
    }
    document.querySelectorAll(".tab-panel").forEach(panel => {
      const active = panel === targetPanel;
      panel.classList.toggle("active", active);
      panel.hidden = !active;
    });
    document.querySelectorAll(".nav-button").forEach(button => {
      button.classList.toggle("active", button.dataset.tabTarget === tab);
    });
    if (tab === "mine") {
      loadMine();
    } else if (tab === "team" && userIsAdmin) {
      loadTeam();
    } else if (tab === "admin-settings" && userIsAdmin) {
      loadSettings();
    } else if (tab === "conversion-log" && userIsAdmin) {
      loadConversions();
    } else if (tab === "diagnostics" && userIsAdmin) {
      loadDiagnostics();
    }
  }

  function toast(message, type) {
    const container = element("toast-container");
    if (!container) {
      return;
    }
    const node = document.createElement("div");
    node.className = "toast " + (type || "");
    node.textContent = message;
    container.appendChild(node);
    setTimeout(() => node.remove(), 3500);
  }

  function formValue(id) {
    const node = element(id);
    return node && node.value ? node.value.trim() : "";
  }

  function defaultDate() {
    const date = element("field-date");
    if (date && !date.value) {
      date.value = window.MileageDateHelpers.isoDate(
        window.MileageDateHelpers.todayForTimeZone(timezoneFromClaims())
      );
    }
  }

  function initDateRanges() {
    ["mine", "team", "conversion"].forEach(initDateRange);
  }

  function initDateRange(scope) {
    const preset = element(scope + "-range-preset");
    if (!preset) {
      return;
    }
    applyDateRange(scope);
    preset.addEventListener("change", () => {
      applyDateRange(scope);
      reloadRangeScope(scope);
    });
    ["from", "to"].forEach(part => {
      const input = element(scope + "-range-" + part);
      if (input) {
        input.addEventListener("change", () => {
          if (preset.value === "custom") {
            reloadRangeScope(scope);
          }
        });
      }
    });
  }

  function applyDateRange(scope) {
    const preset = element(scope + "-range-preset");
    const custom = element(scope + "-range-custom");
    if (!preset) {
      return;
    }
    const isCustom = preset.value === "custom";
    if (custom) {
      custom.hidden = !isCustom;
    }
    if (!isCustom) {
      const range = dateRangeForPreset(preset.value);
      setRangeInputs(scope, range);
    }
  }

  function reloadRangeScope(scope) {
    if (scope === "mine") {
      loadMine();
    } else if (scope === "team" && userIsAdmin) {
      loadTeam();
    } else if (scope === "conversion" && userIsAdmin) {
      loadConversions();
    }
  }

  function rangeQuery(scope) {
    const range = validSelectedDateRange(scope);
    if (!range) {
      return null;
    }
    return "&from=" + encodeURIComponent(range.from) + "&to=" + encodeURIComponent(range.to);
  }

  function csvPath(scope, path) {
    const query = rangeQuery(scope);
    if (query === null) {
      return null;
    }
    return path + (query ? "?" + query.slice(1) : "") + userFilterQuery(scope);
  }

  function selectedDateRange(scope) {
    const preset = element(scope + "-range-preset");
    if (!preset) {
      return dateRangeForPreset("this_week");
    }
    if (preset.value !== "custom") {
      const range = dateRangeForPreset(preset.value);
      setRangeInputs(scope, range);
      return range;
    }
    const from = formValue(scope + "-range-from");
    const to = formValue(scope + "-range-to");
    return { from, to };
  }

  function validSelectedDateRange(scope) {
    const range = selectedDateRange(scope);
    if (!range) {
      return null;
    }
    if (!range.from || !range.to) {
      toast("Choose both From and To dates.", "error");
      return null;
    }
    if (range.from > range.to) {
      toast("From date must be on or before To date.", "error");
      return null;
    }
    return range;
  }

  function setRangeInputs(scope, range) {
    const from = element(scope + "-range-from");
    const to = element(scope + "-range-to");
    if (from) {
      from.value = range.from;
    }
    if (to) {
      to.value = range.to;
    }
  }

  function dateRangeForPreset(preset) {
    if (!Object.prototype.hasOwnProperty.call(rangePresets, preset)) {
      preset = "this_week";
    }
    return window.MileageDateHelpers.dateRangeForPreset(preset, timezoneFromClaims());
  }

  function mileagePayload() {
    const rateAllowed = Boolean(createContext.allowUserRateOverride);
    return {
      date: formValue("field-date"),
      projectId: formValue("field-project") || null,
      miles: formValue("field-miles"),
      rate: rateAllowed ? (formValue("field-rate") || null) : null,
      billable: element("field-billable").checked,
      notes: formValue("field-notes") || null
    };
  }

  function applyCreateContext(data) {
    createContext = Object.assign({}, createContext, data || {});
    const rateRow = element("rate-field-row");
    const rate = element("field-rate");
    const preview = element("btn-preview");
    const submit = document.querySelector("#mileage-form button[type='submit']");
    const context = element("create-context");
    const unit = createContext.unit || "mile";
    const rateText = createContext.rate ? createContext.rate + " per " + unit : "not configured";
    if (context) {
      context.textContent = createContext.complete
        ? "Workspace rate: " + rateText
        : "Mileage settings need a rate and Mileage category before users can create expenses.";
    }
    if (rate) {
      rate.disabled = !createContext.allowUserRateOverride;
      if (!createContext.allowUserRateOverride) {
        rate.value = "";
      }
    }
    if (rateRow) {
      rateRow.hidden = !createContext.allowUserRateOverride;
    }
    if (preview) {
      preview.disabled = !createContext.complete;
    }
    if (submit) {
      submit.disabled = !createContext.complete;
    }
  }

  function loadCreateContext() {
    if (!element("create-context")) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/create-context")
      .then(applyCreateContext)
      .catch(error => {
        element("create-context").textContent = "Mileage settings could not be loaded.";
        toast(error.message, "error");
      });
  }

  function previewMileage() {
    const payload = mileagePayload();
    apiFetch("/api/mileage/preview", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ miles: payload.miles, rate: payload.rate })
    }).then(result => {
      const target = element("preview-result");
      const unit = createContext.unit || "mile";
      target.replaceChildren();
      const primary = document.createElement("div");
      primary.className = "amount-primary";
      primary.textContent = trimDecimal(result.miles) + " " + unitLabel(result.miles, unit) + " x " + trimDecimal(result.rate) + " = " + trimDecimal(result.calculatedAmount);
      const secondary = document.createElement("div");
      secondary.className = "amount-secondary";
      secondary.textContent = "Expense amount: " + result.roundedAmount;
      target.append(primary, secondary);
    }).catch(error => toast(error.message, "error"));
  }

  function setCreateBusy(busy) {
    const submit = document.querySelector("#mileage-form button[type='submit']");
    if (!submit) {
      return;
    }
    submit.disabled = busy || !createContext.complete;
    submit.textContent = busy ? "Creating..." : "Create Expense";
  }

  function validateReceipt(file) {
    if (!file) {
      return true;
    }
    if (file.size > maxReceiptBytes) {
      toast("Receipt file exceeds 10 MB.", "error");
      return false;
    }
    if (!allowedReceiptTypes.has(file.type || "")) {
      toast("Unsupported receipt file type.", "error");
      return false;
    }
    return true;
  }

  function createMileage(event) {
    event.preventDefault();
    const file = element("field-receipt").files[0];
    if (!validateReceipt(file)) {
      return;
    }
    setCreateBusy(true);
    if (file) {
      const body = new FormData();
      Object.entries(mileagePayload()).forEach(([key, value]) => {
        if (value !== null && value !== "") {
          body.append(key, value);
        }
      });
      body.append("file", file);
      apiFetch("/api/mileage/expenses", { method: "POST", body })
        .then(recordSubmission)
        .catch(error => toast(error.message, "error"))
        .finally(() => setCreateBusy(false));
      return;
    }
    apiFetch("/api/mileage/expenses", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(mileagePayload())
    }).then(recordSubmission).catch(error => toast(error.message, "error")).finally(() => setCreateBusy(false));
  }

  function recordSubmission(result) {
    element("create-status").textContent = "Created " + result.expenseId;
    element("field-miles").value = "";
    element("field-rate").value = "";
    element("field-notes").value = "";
    element("field-receipt").value = "";
    const target = element("preview-result");
    if (target) {
      target.replaceChildren();
    }
    loadMine();
    toast("Mileage expense created.");
  }

  function loadMine() {
    const rows = element("mine-rows");
    if (!rows) {
      return Promise.resolve();
    }
    const query = rangeQuery("mine");
    if (query === null) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/mine?pageSize=50" + query)
      .then(data => renderMileageRows(rows, data.conversions || [], false, "No mileage rows yet."))
      .catch(error => toast(error.message, "error"));
  }

  function loadTeam() {
    const rows = element("team-rows");
    if (!rows) {
      return Promise.resolve();
    }
    const query = rangeQuery("team");
    if (query === null) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/team?pageSize=50" + query + userFilterQuery("team"))
      .then(data => renderMileageRows(rows, data.conversions || [], true, "No team mileage rows yet."))
      .catch(error => toast(error.message, "error"));
  }

  function renderMileageRows(rows, items, includeUser, emptyText) {
    rows.replaceChildren();
    if (!items.length) {
      renderEmptyRow(rows, includeUser ? 9 : 8, emptyText);
      return;
    }
    items.forEach(item => {
      const row = rows.insertRow();
      appendTextCell(row, formatExpenseDate(item.expenseDate));
      appendTextCell(row, item.expenseId);
      if (includeUser) {
        appendTextCell(row, item.userName || item.userId);
      }
      appendTextCell(row, item.sourceLabel || sourceLabel(item.source));
      appendTextCell(row, item.status);
      appendTextCell(row, trimDecimal(item.miles));
      appendTextCell(row, trimDecimal(item.rate));
      appendAmountCell(row, item.calculatedAmount, item.roundedAmount);
      appendTextCell(row, formatDate(item.updatedAt));
    });
  }

  function loadCategories() {
    return apiFetch("/api/mileage/options/categories").then(data => {
      const categorySelect = element("settings-mileage-category");
      if (!categorySelect) {
        return;
      }
      defaultMileageCategory = null;
      categorySelect.replaceChildren();
      appendOption(categorySelect, "", "Choose Mileage category");
      (data.categories || []).forEach(category => {
        const rate = centsToRate(category.unitPrice);
        const option = Object.assign({}, category, { rate });
        if (isDefaultMileageCategory(option)) {
          defaultMileageCategory = option;
        }
        const rateText = rate ? ", " + rate + "/" + category.unit : "";
        const suffix = category.unit ? " (" + category.type + ": " + category.unit + rateText + ")" : " (" + category.type + ")";
        appendOption(categorySelect, category.id, category.name + suffix);
      });
      if (data.warning) {
        appendOption(categorySelect, "", "Category list unavailable");
        toast(data.warning, "error");
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

  function loadProjects() {
    const project = element("field-project");
    if (!project) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/options/projects").then(data => {
      project.replaceChildren();
      appendOption(project, "", "No project");
      (data.projects || []).forEach(item => appendOption(project, item.id, item.name));
    }).catch(error => toast(error.message, "error"));
  }

  function loadUserOptions() {
    const selects = [element("team-user-filter"), element("conversion-user-filter")].filter(Boolean);
    if (!selects.length) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/options/users").then(data => {
      selects.forEach(select => {
        const current = select.value;
        select.replaceChildren();
        appendOption(select, "", "All users");
        (data.users || []).forEach(user => appendOption(select, user.id, user.name || user.id));
        select.value = current;
      });
    }).catch(error => toast(error.message, "error"));
  }

  function userFilterQuery(scope) {
    const selectId = scope === "team" ? "team-user-filter" : scope === "conversion" ? "conversion-user-filter" : "";
    if (!selectId) {
      return "";
    }
    const userId = formValue(selectId);
    return userId ? "&userId=" + encodeURIComponent(userId) : "";
  }

  function loadSettings() {
    if (!element("settings-form")) {
      return Promise.resolve();
    }
    const settingsPromise = apiFetch("/api/mileage/settings");
    const categoriesPromise = loadCategories().catch(error => {
      toast("Mileage categories could not be loaded: " + error.message, "error");
    });
    return settingsPromise
      .then(settings => {
        element("settings-enabled").checked = settings.enabled;
        element("settings-rate").value = settings.rate || "";
        return categoriesPromise.then(() => {
          const selectedCategory = settings.mileageCategoryId || settings.inputCategoryId || settings.outputCategoryId || "";
          const categorySelect = element("settings-mileage-category");
          ensureCategoryOption(categorySelect, selectedCategory, settings.mileageCategoryName || "Configured Mileage category");
          categorySelect.value = selectedCategory;
          if (!selectedCategory && defaultMileageCategory) {
            categorySelect.value = defaultMileageCategory.id || "";
          }
          if (!settings.rate && defaultMileageCategory && defaultMileageCategory.rate) {
            element("settings-rate").value = defaultMileageCategory.rate;
          }
          element("settings-convert-create").checked = settings.convertOnCreate;
          element("settings-convert-update").checked = settings.convertOnUpdate;
          element("settings-rate-override").checked = settings.allowUserRateOverride;
          element("settings-note-template").value = settings.noteTemplate || "";
          element("settings-status").textContent = settings.completeForNativeConversion
            ? "Ready"
            : defaultMileageCategory ? "Default Mileage found" : "Needs configuration";
        });
      })
      .catch(error => toast(error.message, "error"));
  }

  function saveSettings(event) {
    event.preventDefault();
    const payload = {
      enabled: element("settings-enabled").checked,
      rate: formValue("settings-rate"),
      mileageCategoryId: formValue("settings-mileage-category") || null,
      convertOnCreate: element("settings-convert-create").checked,
      convertOnUpdate: element("settings-convert-update").checked,
      allowUserRateOverride: element("settings-rate-override").checked,
      noteTemplate: formValue("settings-note-template"),
    };
    apiFetch("/api/mileage/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }).then(() => {
      toast("Settings saved.");
      loadCreateContext();
      loadDiagnostics();
    }).catch(error => toast(error.message, "error"));
  }

  function setupMileageCategory() {
    const button = element("btn-setup-mileage-category");
    if (button && button.disabled) {
      return;
    }
    if (button) {
      button.disabled = true;
      button.textContent = "Repairing...";
    }
    apiFetch("/api/mileage/settings/mileage-category", { method: "POST" })
      .then(settings => {
        toast("Mileage category is ready.");
        return loadCategories().then(() => {
          const categorySelect = element("settings-mileage-category");
          if (categorySelect) {
            ensureCategoryOption(categorySelect, settings.mileageCategoryId || "", settings.mileageCategoryName || "Mileage");
            categorySelect.value = settings.mileageCategoryId || "";
          }
          loadSettings();
          loadCreateContext();
          loadDiagnostics();
        });
      })
      .catch(error => toast(error.message, "error"))
      .finally(() => {
        if (button) {
          button.disabled = false;
          button.textContent = "Use or Repair Mileage Category";
        }
      });
  }

  function loadConversions() {
    const rows = element("conversion-rows");
    if (!rows) {
      return Promise.resolve();
    }
    const query = rangeQuery("conversion");
    if (query === null) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/conversions?pageSize=50" + query + userFilterQuery("conversion")).then(data => {
      rows.replaceChildren();
      const items = data.conversions || [];
      if (!items.length) {
        renderEmptyRow(rows, 9, "No conversion rows yet.");
        return;
      }
      items.forEach(item => {
        const row = rows.insertRow();
        appendTextCell(row, formatExpenseDate(item.expenseDate));
        appendTextCell(row, item.expenseId);
        appendTextCell(row, item.sourceLabel || sourceLabel(item.source));
        appendTextCell(row, item.userName || item.userId);
        appendTextCell(row, item.status);
        appendTextCell(row, trimDecimal(item.miles));
        appendTextCell(row, trimDecimal(item.rate));
        appendAmountCell(row, item.calculatedAmount, item.roundedAmount);
        appendTextCell(row, formatDate(item.updatedAt));
      });
    }).catch(error => toast(error.message, "error"));
  }

  function appendTextCell(row, value) {
    const cell = row.insertCell();
    cell.textContent = value == null ? "" : String(value);
  }

  function appendAmountCell(row, calculatedAmount, roundedAmount) {
    const cell = row.insertCell();
    const stack = document.createElement("div");
    stack.className = "metric-stack";
    const primary = document.createElement("span");
    primary.className = "amount-primary";
    primary.textContent = calculatedAmount == null ? "" : trimDecimal(calculatedAmount);
    const secondary = document.createElement("span");
    secondary.className = "amount-secondary";
    secondary.textContent = roundedAmount == null ? "Expense amount: " : "Expense amount: " + roundedAmount;
    stack.append(primary, secondary);
    cell.appendChild(stack);
  }

  function trimDecimal(value) {
    if (value == null || value === "") {
      return "";
    }
    const text = String(value);
    return text.indexOf(".") >= 0 ? text.replace(/0+$/, "").replace(/\.$/, "") : text;
  }

  function unitLabel(miles, unit) {
    return unit === "mile" && trimDecimal(miles) !== "1" ? "miles" : unit;
  }

  function sourceLabel(source) {
    if (source === "ADDON_FORM") {
      return "Created through add-on";
    }
    if (source) {
      return "Created through Expenses";
    }
    return "";
  }

  function formatDate(value) {
    if (!value) {
      return "";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    const options = { year: "numeric", month: "2-digit", day: "2-digit", hour: "numeric", minute: "2-digit" };
    const timezone = timezoneFromClaims();
    if (timezone) {
      options.timeZone = timezone;
    }
    try {
      return new Intl.DateTimeFormat("en-US", options).format(date);
    } catch (e) {
      return new Intl.DateTimeFormat("en-US", { year: "numeric", month: "2-digit", day: "2-digit", hour: "numeric", minute: "2-digit" }).format(date);
    }
  }

  function formatExpenseDate(value) {
    if (!value) {
      return "";
    }
    const parts = String(value).split("-").map(part => Number(part));
    if (parts.length !== 3 || parts.some(part => Number.isNaN(part))) {
      return String(value);
    }
    return new Intl.DateTimeFormat("en-US", { year: "numeric", month: "2-digit", day: "2-digit" })
      .format(new Date(parts[0], parts[1] - 1, parts[2]));
  }

  function renderEmptyRow(rows, colspan, text) {
    const row = rows.insertRow();
    const cell = row.insertCell();
    cell.colSpan = colspan;
    cell.className = "empty-table";
    cell.textContent = text;
  }

  function loadDiagnostics() {
    if (!element("diagnostics-list")) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/diagnostics").then(data => {
      const list = element("diagnostics-list");
      list.replaceChildren();
      [["Installation", data.installationAvailable], ["Settings", data.settingsComplete], ["Native conversion", data.nativeConversionReady]].forEach(([label, value]) => {
        const dt = document.createElement("dt");
        const dd = document.createElement("dd");
        dt.textContent = label;
        dd.textContent = value ? "OK" : "Needs attention";
        list.append(dt, dd);
      });
      const warnings = element("diagnostics-warnings");
      warnings.replaceChildren();
      (data.warnings || []).forEach(text => {
        const item = document.createElement("li");
        item.textContent = text;
        warnings.appendChild(item);
      });
    }).catch(error => toast(error.message, "error"));
  }

  function on(id, event, handler) {
    const node = element(id);
    if (node) {
      node.addEventListener(event, handler);
    }
  }

  function reportPath(scope, userId) {
    const range = validSelectedDateRange(scope);
    if (!range) {
      return null;
    }
    let path = "/iframe/report?from=" + encodeURIComponent(range.from) + "&to=" + encodeURIComponent(range.to);
    path += "&scope=" + encodeURIComponent(scope); // "mine" pins to the requester; "team" = admin all-users/filter
    if (userId) {
      path += "&userId=" + encodeURIComponent(userId);
    }
    if (authToken) {
      path += "&auth_token=" + encodeURIComponent(authToken);
    }
    return path;
  }

  function handleReportClick(event) {
    const button = event.target.closest("button");
    if (!button) {
      return;
    }
    const reports = {
      "btn-report-mine": ["mine", null],
      "btn-report-team": ["team", "team-user-filter"]
    };
    const config = reports[button.id];
    if (!config) {
      return;
    }
    event.preventDefault();
    let userId = null;
    if (config[1]) {
      userId = formValue(config[1]); // empty selection => all users (reportPath omits userId)
    }
    const path = reportPath(config[0], userId);
    if (path) {
      const reportWindow = window.open(path, "_blank");
      if (reportWindow) {
        reportWindow.opener = null;
      }
    }
  }

  function handleCsvExport(event) {
    const button = event.target.closest("button");
    if (!button) {
      return;
    }
    const exports = {
      "btn-export-mine": ["mine", "/api/mileage/mine.csv", "mileage-mine.csv"],
      "btn-export-team": ["team", "/api/mileage/team.csv", "mileage-team.csv"],
      "btn-export-conversions": ["conversion", "/api/mileage/conversions.csv", "mileage-conversions.csv"]
    };
    const exportConfig = exports[button.id];
    if (!exportConfig) {
      return;
    }
    event.preventDefault();
    downloadCsv(csvPath(exportConfig[0], exportConfig[1]), exportConfig[2]);
  }

  document.querySelectorAll("[data-tab-target]").forEach(button => {
    button.addEventListener("click", () => switchTab(button.dataset.tabTarget));
  });
  document.addEventListener("click", handleCsvExport);
  document.addEventListener("click", handleReportClick);
  on("btn-preview", "click", previewMileage);
  on("mileage-form", "submit", createMileage);
  on("settings-form", "submit", saveSettings);
  on("btn-setup-mileage-category", "click", setupMileageCategory);
  on("btn-refresh-mine", "click", loadMine);
  on("btn-refresh-team", "click", loadTeam);
  on("btn-refresh-conversions", "click", loadConversions);
  on("btn-refresh-diagnostics", "click", loadDiagnostics);
  on("team-user-filter", "change", loadTeam);
  on("conversion-user-filter", "change", loadConversions);

  tokenClaims = claimsFromToken();
  applyTheme();
  userIsAdmin = applyRoleGate();
  initDateRanges();
  defaultDate();
  loadCreateContext();
  loadProjects();
  loadUserOptions();
  switchTab(userIsAdmin && window.location.pathname.endsWith("/iframe/settings") ? "admin-settings" : "mine");
})();
