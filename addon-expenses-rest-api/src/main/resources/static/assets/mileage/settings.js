(function () {
  const url = new URL(window.location.href);
  const authToken = url.searchParams.get("auth_token") || "";
  let createContext = {
    rate: null,
    unit: "mile",
    allowUserRateOverride: false,
    complete: false
  };
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
    return tokenClaims.timeZone || tokenClaims.timezone || tokenClaims.tz || "";
  }

  function applyRoleGate() {
    tokenClaims = claimsFromToken();
    const role = roleFromClaims(tokenClaims);
    const isAdmin = role === "OWNER" || role === "ADMIN";
    document.querySelectorAll("[data-admin-only]").forEach(element => {
      element.hidden = !isAdmin;
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
      panel.classList.toggle("active", panel === targetPanel);
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
      date.value = new Date().toISOString().slice(0, 10);
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
    const range = selectedDateRange(scope);
    if (!range) {
      return "";
    }
    return "&from=" + encodeURIComponent(range.from) + "&to=" + encodeURIComponent(range.to);
  }

  function csvPath(scope, path) {
    const query = rangeQuery(scope);
    return path + (query ? "?" + query.slice(1) : "");
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
    const today = todayLocalDate();
    const weekStart = startOfWeek(today);
    if (preset === "this_month") {
      return rangeForDates(new Date(today.getFullYear(), today.getMonth(), 1),
        new Date(today.getFullYear(), today.getMonth() + 1, 0));
    }
    if (preset === "last_week") {
      return rangeForDates(addDays(weekStart, -7), addDays(weekStart, -1));
    }
    if (preset === "last_month") {
      return rangeForDates(new Date(today.getFullYear(), today.getMonth() - 1, 1),
        new Date(today.getFullYear(), today.getMonth(), 0));
    }
    if (preset === "this_year") {
      return rangeForDates(new Date(today.getFullYear(), 0, 1), new Date(today.getFullYear(), 11, 31));
    }
    if (preset === "last_year") {
      return rangeForDates(new Date(today.getFullYear() - 1, 0, 1), new Date(today.getFullYear() - 1, 11, 31));
    }
    return rangeForDates(weekStart, addDays(weekStart, 6));
  }

  function todayLocalDate() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  }

  function startOfWeek(date) {
    return addDays(date, -date.getDay());
  }

  function addDays(date, days) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
  }

  function rangeForDates(from, to) {
    return { from: isoDate(from), to: isoDate(to) };
  }

  function isoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return year + "-" + month + "-" + day;
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

  function createMileage(event) {
    event.preventDefault();
    const file = element("field-receipt").files[0];
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
    return apiFetch("/api/mileage/mine?pageSize=50" + rangeQuery("mine"))
      .then(data => renderMileageRows(rows, data.conversions || [], false, "No mileage rows yet."))
      .catch(error => toast(error.message, "error"));
  }

  function loadTeam() {
    const rows = element("team-rows");
    if (!rows) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/team?pageSize=50" + rangeQuery("team"))
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
      categorySelect.replaceChildren();
      appendOption(categorySelect, "", "Choose Mileage category");
      data.categories.forEach(category => {
        const suffix = category.unit ? " (" + category.type + ": " + category.unit + ")" : " (" + category.type + ")";
        appendOption(categorySelect, category.id, category.name + suffix);
      });
    });
  }

  function appendOption(select, value, label) {
    select.appendChild(new Option(label, value || ""));
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

  function loadSettings() {
    if (!element("settings-form")) {
      return Promise.resolve();
    }
    return Promise.all([apiFetch("/api/mileage/settings"), loadCategories()])
      .then(([settings]) => {
        element("settings-enabled").checked = settings.enabled;
        element("settings-rate").value = settings.rate || "";
        element("settings-mileage-category").value = settings.mileageCategoryId || settings.inputCategoryId || settings.outputCategoryId || "";
        element("settings-convert-create").checked = settings.convertOnCreate;
        element("settings-convert-update").checked = settings.convertOnUpdate;
        element("settings-rate-override").checked = settings.allowUserRateOverride;
        element("settings-status").textContent = settings.completeForNativeConversion ? "Ready" : "Needs configuration";
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
    apiFetch("/api/mileage/settings/mileage-category", { method: "POST" })
      .then(settings => {
        toast("Mileage category is ready.");
        return loadCategories().then(() => {
          if (element("settings-mileage-category")) {
            element("settings-mileage-category").value = settings.mileageCategoryId || "";
          }
          loadSettings();
          loadCreateContext();
          loadDiagnostics();
        });
      })
      .catch(error => toast(error.message, "error"));
  }

  function loadConversions() {
    const rows = element("conversion-rows");
    if (!rows) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/conversions?pageSize=50" + rangeQuery("conversion")).then(data => {
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
    const options = { year: "numeric", month: "2-digit", day: "2-digit", hour: "numeric", minute: "2-digit" };
    const timezone = timezoneFromClaims();
    if (timezone) {
      options.timeZone = timezone;
    }
    try {
      return new Intl.DateTimeFormat("en-US", options).format(new Date(value));
    } catch (e) {
      return new Intl.DateTimeFormat("en-US", { year: "numeric", month: "2-digit", day: "2-digit", hour: "numeric", minute: "2-digit" }).format(new Date(value));
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

  document.querySelectorAll("[data-tab-target]").forEach(button => {
    button.addEventListener("click", () => switchTab(button.dataset.tabTarget));
  });
  on("btn-preview", "click", previewMileage);
  on("mileage-form", "submit", createMileage);
  on("settings-form", "submit", saveSettings);
  on("btn-setup-mileage-category", "click", setupMileageCategory);
  on("btn-refresh-mine", "click", loadMine);
  on("btn-refresh-team", "click", loadTeam);
  on("btn-refresh-conversions", "click", loadConversions);
  on("btn-refresh-diagnostics", "click", loadDiagnostics);
  on("btn-export-mine", "click", () => downloadCsv(csvPath("mine", "/api/mileage/mine.csv"), "mileage-mine.csv"));
  on("btn-export-team", "click", () => downloadCsv(csvPath("team", "/api/mileage/team.csv"), "mileage-team.csv"));
  on("btn-export-conversions", "click", () => downloadCsv(csvPath("conversion", "/api/mileage/conversions.csv"), "mileage-conversions.csv"));

  userIsAdmin = applyRoleGate();
  initDateRanges();
  defaultDate();
  loadCreateContext();
  loadProjects();
  switchTab(userIsAdmin && window.location.pathname.endsWith("/iframe/settings") ? "admin-settings" : "mine");
})();
