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
  let projectIdByName = {};
  const pageState = { mine: 0, team: 0, conversion: 0 };
  const MINE_LABELS = ["Date", "Expense", "Source", "Status", "Miles", "Rate", "Amount", "Updated"];
  const TEAM_LABELS = ["Date", "Expense", "User", "Source", "Status", "Miles", "Rate", "Amount", "Updated"];
  const CONVERSION_LABELS = ["Date", "Expense", "Source", "User", "Status", "Miles", "Rate", "Amount", "Updated"];
  const DEFAULT_NOTE_TEMPLATE =
    "Mileage reimbursement: {{miles}} {{unit}} x {{rate}} = {{calculatedAmount}}{{categoryCharge}}. "
    + "Created/converted by Mileage for Clockify.";
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
      const active = button.dataset.tabTarget === tab;
      button.classList.toggle("active", active);
      button.setAttribute("aria-selected", active ? "true" : "false");
      button.tabIndex = active ? 0 : -1;
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

  // Roving-tabindex keyboard support for the ARIA tablist (Arrow keys + Home/End).
  function onTabKeydown(event) {
    const step = { ArrowDown: 1, ArrowRight: 1, ArrowUp: -1, ArrowLeft: -1 };
    if (!(event.key in step) && event.key !== "Home" && event.key !== "End") {
      return;
    }
    const tabs = Array.from(document.querySelectorAll(".nav-button")).filter(button => !button.hidden);
    if (!tabs.length) {
      return;
    }
    event.preventDefault();
    const activeTarget = document.querySelector(".nav-button.active")?.dataset.tabTarget;
    const current = Math.max(0, tabs.findIndex(button => button.dataset.tabTarget === activeTarget));
    let nextIndex;
    if (event.key === "Home") {
      nextIndex = 0;
    } else if (event.key === "End") {
      nextIndex = tabs.length - 1;
    } else {
      nextIndex = (current + step[event.key] + tabs.length) % tabs.length;
    }
    const next = tabs[nextIndex];
    if (next) {
      switchTab(next.dataset.tabTarget);
      next.focus();
    }
  }

  function toast(message, type) {
    const container = element("toast-container");
    if (!container) {
      return;
    }
    const node = document.createElement("div");
    node.className = "toast " + (type || "");
    // Errors are assertive and stay until dismissed; success is polite and auto-clears.
    node.setAttribute("role", type === "error" ? "alert" : "status");
    const messageNode = document.createElement("span");
    messageNode.className = "toast-message";
    messageNode.textContent = message;
    const close = document.createElement("button");
    close.type = "button";
    close.className = "toast-close";
    close.setAttribute("aria-label", "Dismiss");
    close.textContent = "×";
    close.addEventListener("click", () => node.remove());
    node.append(messageNode, close);
    container.appendChild(node);
    if (type !== "error") {
      setTimeout(() => node.remove(), 3500);
    }
  }

  function setFieldError(id, message) {
    const node = element(id);
    if (!node) {
      return;
    }
    node.setAttribute("aria-invalid", "true");
    let error = element(id + "-error");
    if (!error) {
      error = document.createElement("span");
      error.id = id + "-error";
      error.className = "field-error";
      error.setAttribute("role", "alert");
      node.insertAdjacentElement("afterend", error);
      node.setAttribute("aria-describedby", id + "-error");
    }
    error.textContent = message;
  }

  function clearFieldError(id) {
    const node = element(id);
    if (node) {
      node.removeAttribute("aria-invalid");
      node.removeAttribute("aria-describedby");
    }
    const error = element(id + "-error");
    if (error) {
      error.remove();
    }
  }

  function focusField(id) {
    const node = element(id);
    if (node && typeof node.focus === "function") {
      node.focus();
    }
  }

  function setBusy(node, busy, label) {
    if (!node) {
      return;
    }
    if (busy) {
      if (!node.dataset.idleLabel) {
        node.dataset.idleLabel = node.textContent;
      }
      node.setAttribute("aria-busy", "true");
      node.disabled = true;
      if (label) {
        node.textContent = label;
      }
    } else {
      node.removeAttribute("aria-busy");
      node.disabled = false;
      if (node.dataset.idleLabel) {
        node.textContent = node.dataset.idleLabel;
        delete node.dataset.idleLabel;
      }
    }
  }

  function refreshHandler(loader) {
    return function (event) {
      const button = event.currentTarget;
      setBusy(button, true, "Refreshing...");
      Promise.resolve(loader()).finally(() => setBusy(button, false));
    };
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
    pageState[scope] = 0;
    if (scope === "mine") {
      loadMine();
    } else if (scope === "team" && userIsAdmin) {
      loadTeam();
    } else if (scope === "conversion" && userIsAdmin) {
      loadConversions();
    }
  }

  function reloadScope(scope) {
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

  function pageParam(scope) {
    return "&page=" + (pageState[scope] || 0);
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
    clearFieldError(scope + "-range-from");
    clearFieldError(scope + "-range-to");
    if (!range) {
      return null;
    }
    if (!range.from || !range.to) {
      const missing = !range.from ? scope + "-range-from" : scope + "-range-to";
      setFieldError(missing, "Choose both From and To dates.");
      focusField(missing);
      toast("Choose both From and To dates.", "error");
      return null;
    }
    if (range.from > range.to) {
      setFieldError(scope + "-range-from", "From date must be on or before To date.");
      focusField(scope + "-range-from");
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
      projectId: resolveProjectId(formValue("field-project")),
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
      context.classList.toggle("context-warn", !createContext.complete);
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
        element("create-context").classList.add("context-warn");
        toast(error.message, "error");
      });
  }

  function previewMileage() {
    const button = element("btn-preview");
    const payload = mileagePayload();
    setBusy(button, true, "Previewing...");
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
    }).catch(error => toast(error.message, "error")).finally(() => setBusy(button, false));
  }

  function setCreateBusy(busy) {
    const submit = document.querySelector("#mileage-form button[type='submit']");
    if (!submit) {
      return;
    }
    submit.disabled = busy || !createContext.complete;
    if (busy) {
      submit.setAttribute("aria-busy", "true");
    } else {
      submit.removeAttribute("aria-busy");
    }
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
    clearFieldError("field-miles");
    const miles = formValue("field-miles");
    if (!miles || Number.isNaN(Number(miles)) || Number(miles) <= 0) {
      setFieldError("field-miles", "Enter the miles driven as a positive number.");
      focusField("field-miles");
      return;
    }
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
    const status = element("create-status");
    if (status) {
      const unit = createContext.unit || "mile";
      const miles = trimDecimal(result.miles);
      status.textContent = miles && result.roundedAmount
        ? "Created " + miles + " " + unitLabel(result.miles, unit) + " → " + result.roundedAmount
        : "Mileage expense created";
      if (result.expenseId) {
        status.title = "Clockify expense " + result.expenseId;
      }
    }
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
    renderLoadingRow(rows, MINE_LABELS.length);
    return apiFetch("/api/mileage/mine?pageSize=50" + query + pageParam("mine"))
      .then(data => {
        renderMileageRows(rows, data.conversions || [], false, "No mileage rows yet.");
        renderPager("mine", data);
      })
      .catch(error => {
        renderErrorRow(rows, MINE_LABELS.length);
        toast(error.message, "error");
      });
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
    renderLoadingRow(rows, TEAM_LABELS.length);
    return apiFetch("/api/mileage/team?pageSize=50" + query + userFilterQuery("team") + pageParam("team"))
      .then(data => {
        renderMileageRows(rows, data.conversions || [], true, "No team mileage rows yet.");
        renderPager("team", data);
      })
      .catch(error => {
        renderErrorRow(rows, TEAM_LABELS.length);
        toast(error.message, "error");
      });
  }

  function renderMileageRows(rows, items, includeUser, emptyText) {
    rows.replaceChildren();
    const labels = includeUser ? TEAM_LABELS : MINE_LABELS;
    if (!items.length) {
      renderEmptyRow(rows, labels.length, emptyText, "Adjust the date range or create a new mileage expense.");
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
      labelRow(row, labels);
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
    const datalist = element("project-options");
    if (!datalist) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/options/projects").then(data => {
      datalist.replaceChildren();
      projectIdByName = {};
      (data.projects || [])
        .slice()
        .sort((left, right) => String(left.name || "").localeCompare(String(right.name || "")))
        .forEach(item => {
          if (!item || !item.id) {
            return;
          }
          const name = item.name || item.id;
          projectIdByName[name.toLowerCase()] = item.id;
          datalist.appendChild(new Option(name));
        });
      if (data.warning) {
        toast(data.warning, "error");
      }
    }).catch(error => toast(error.message, "error"));
  }

  function resolveProjectId(value) {
    const name = (value || "").trim();
    if (!name) {
      return null;
    }
    return projectIdByName[name.toLowerCase()] || null;
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
      if (data.warning) {
        toast(data.warning, "error");
      }
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
          applyRateDefaultHint();
          renderNotePreview();
        });
      })
      .catch(error => toast(error.message, "error"));
  }

  // S-1: when no rate is saved (and none derived from a default category), pre-fill the visible default
  // rate the rest of the app already uses (create-context's effective 0.725) so Settings never reads as
  // "my rate vanished" while Mine shows a workspace rate.
  function applyRateDefaultHint() {
    const rateInput = element("settings-rate");
    const rateHint = element("settings-rate-hint");
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
    if (createContext.rate) {
      apply(createContext.rate);
      return;
    }
    apiFetch("/api/mileage/create-context")
      .then(data => {
        applyCreateContext(data);
        apply(createContext.rate);
      })
      .catch(() => {});
  }

  function renderNotePreview() {
    const textarea = element("settings-note-template");
    const box = element("settings-note-preview");
    const target = element("settings-note-preview-text");
    if (!textarea || !box || !target) {
      return;
    }
    const rate = formValue("settings-rate") || createContext.rate || "0.725";
    const miles = "10";
    const amount = sampleAmount(miles, rate);
    const values = {
      miles: miles,
      unit: "miles",
      rate: trimDecimal(rate),
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
    return trimDecimal(String(Math.round(product * 1e6) / 1e6));
  }

  function saveSettings(event) {
    event.preventDefault();
    const button = document.querySelector("#settings-form button[type='submit']");
    setBusy(button, true, "Saving...");
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
    }).catch(error => toast(error.message, "error")).finally(() => setBusy(button, false));
  }

  function setupMileageCategory() {
    const button = element("btn-setup-mileage-category");
    if (button && button.disabled) {
      return;
    }
    if (button) {
      button.disabled = true;
      button.setAttribute("aria-busy", "true");
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
          button.removeAttribute("aria-busy");
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
    renderLoadingRow(rows, CONVERSION_LABELS.length);
    return apiFetch("/api/mileage/conversions?pageSize=50" + query + userFilterQuery("conversion") + pageParam("conversion")).then(data => {
      rows.replaceChildren();
      const items = data.conversions || [];
      if (!items.length) {
        renderEmptyRow(rows, CONVERSION_LABELS.length, "No conversion rows yet.", "Conversions appear here as native and add-on expenses are processed.");
        renderPager("conversion", data);
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
        labelRow(row, CONVERSION_LABELS);
      });
      renderPager("conversion", data);
    }).catch(error => {
      renderErrorRow(rows, CONVERSION_LABELS.length);
      toast(error.message, "error");
    });
  }

  function renderPager(scope, data) {
    const pager = element(scope + "-pager");
    if (!pager) {
      return;
    }
    const total = Number(data.totalElements || 0);
    const page = Number(data.page || 0);
    const pageSize = Number(data.pageSize || 50);
    const shown = (data.conversions || []).length;
    const totalPages = Number(data.totalPages || (total ? Math.ceil(total / pageSize) : 1));
    if (total <= shown && page === 0) {
      pager.hidden = true;
      pager.replaceChildren();
      return;
    }
    pager.hidden = false;
    pager.replaceChildren();
    const start = total === 0 ? 0 : page * pageSize + 1;
    const end = page * pageSize + shown;
    const label = document.createElement("span");
    label.textContent = "Showing " + start + "–" + end + " of " + total;
    const buttons = document.createElement("div");
    buttons.className = "pager-buttons";
    const prev = document.createElement("button");
    prev.type = "button";
    prev.textContent = "Previous";
    prev.disabled = page <= 0;
    prev.addEventListener("click", () => {
      pageState[scope] = Math.max(0, page - 1);
      reloadScope(scope);
    });
    const next = document.createElement("button");
    next.type = "button";
    next.textContent = "Next";
    next.disabled = page + 1 >= totalPages;
    next.addEventListener("click", () => {
      pageState[scope] = page + 1;
      reloadScope(scope);
    });
    buttons.append(prev, next);
    pager.append(label, buttons);
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

  function labelRow(row, labels) {
    Array.from(row.cells).forEach((cell, index) => {
      if (labels[index]) {
        cell.setAttribute("data-label", labels[index]);
      }
    });
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

  function renderEmptyRow(rows, colspan, text, hint) {
    const row = rows.insertRow();
    const cell = row.insertCell();
    cell.colSpan = colspan;
    cell.className = "empty-table";
    const title = document.createElement("strong");
    title.textContent = text;
    cell.appendChild(title);
    if (hint) {
      cell.appendChild(document.createTextNode(hint));
    }
  }

  function renderLoadingRow(rows, colspan) {
    rows.replaceChildren();
    const row = rows.insertRow();
    row.className = "loading-row";
    const cell = row.insertCell();
    cell.colSpan = colspan;
    const skeleton = document.createElement("span");
    skeleton.className = "skeleton";
    cell.appendChild(skeleton);
  }

  function renderErrorRow(rows, colspan) {
    rows.replaceChildren();
    renderEmptyRow(rows, colspan, "Could not load rows.", "Check your connection and press Refresh.");
  }

  function loadDiagnostics() {
    const list = element("diagnostics-list");
    if (!list) {
      return Promise.resolve();
    }
    renderDiagnosticsStatus(list, "Status", "Checking…", "");
    return apiFetch("/api/mileage/diagnostics").then(data => {
      list.replaceChildren();
      [["Installation", data.installationAvailable], ["Settings", data.settingsComplete], ["Native conversion", data.nativeConversionReady]].forEach(([label, value]) => {
        const dt = document.createElement("dt");
        const dd = document.createElement("dd");
        dt.textContent = label;
        dd.textContent = value ? "OK" : "Needs attention";
        dd.className = value ? "ok" : "warn";
        list.append(dt, dd);
      });
      const warnings = element("diagnostics-warnings");
      warnings.replaceChildren();
      (data.warnings || []).forEach(text => {
        const item = document.createElement("li");
        item.textContent = text;
        warnings.appendChild(item);
      });
    }).catch(error => {
      renderDiagnosticsStatus(list, "Status", "Could not load diagnostics", "warn");
      toast(error.message, "error");
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
    setBusy(button, true, "Exporting...");
    Promise.resolve(downloadCsv(csvPath(exportConfig[0], exportConfig[1]), exportConfig[2])).finally(() => setBusy(button, false));
  }

  document.querySelectorAll("[data-tab-target]").forEach(button => {
    button.addEventListener("click", () => switchTab(button.dataset.tabTarget));
  });
  const tablist = document.querySelector("[role=\"tablist\"]");
  if (tablist) {
    tablist.addEventListener("keydown", onTabKeydown);
  }
  document.addEventListener("click", handleCsvExport);
  document.addEventListener("click", handleReportClick);
  on("btn-preview", "click", previewMileage);
  on("mileage-form", "submit", createMileage);
  on("settings-form", "submit", saveSettings);
  on("btn-setup-mileage-category", "click", setupMileageCategory);
  on("btn-refresh-mine", "click", refreshHandler(loadMine));
  on("btn-refresh-team", "click", refreshHandler(loadTeam));
  on("btn-refresh-conversions", "click", refreshHandler(loadConversions));
  on("btn-refresh-diagnostics", "click", refreshHandler(loadDiagnostics));
  on("team-user-filter", "change", () => { pageState.team = 0; loadTeam(); });
  on("conversion-user-filter", "change", () => { pageState.conversion = 0; loadConversions(); });
  on("settings-note-template", "input", renderNotePreview);
  on("settings-rate", "input", renderNotePreview);

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
