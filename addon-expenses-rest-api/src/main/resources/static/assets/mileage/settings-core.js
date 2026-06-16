(function () {
  const app = window.MileageSettingsApp = window.MileageSettingsApp || {};
  const url = new URL(window.location.href);
  const authToken = url.searchParams.get("auth_token") || "";

  app.authToken = authToken;
  app.state = {
    createContext: {
      rate: null,
      unit: "mile",
      allowUserRateOverride: false,
      complete: false
    },
    defaultMileageCategory: null,
    userIsAdmin: false,
    tokenClaims: {},
    projectIdByName: {},
    pageState: { mine: 0, team: 0, conversion: 0 }
  };

  function hideAuthTokenFromLocation() {
    if (!authToken) {
      return;
    }
    url.searchParams.delete("auth_token");
    history.replaceState({}, document.title, url.pathname + url.search + url.hash);
  }

  function element(id) {
    return document.getElementById(id);
  }

  function authHeaders(extra) {
    const headers = Object.assign({}, extra || {});
    const token = app.authToken || authToken;
    if (token) {
      headers.Authorization = "Bearer " + token;
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
    const claims = app.state.tokenClaims;
    return claims.userTimeZone || claims.timeZone || claims.userTimezone || claims.timezone || claims.tz || "";
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
    document.documentElement.dataset.theme = themeFromClaims(app.state.tokenClaims);
  }

  function applyRoleGate() {
    const role = roleFromClaims(app.state.tokenClaims);
    const isAdmin = role === "OWNER" || role === "ADMIN";
    document.querySelectorAll("[data-admin-only]").forEach(node => {
      node.hidden = !isAdmin || (node.classList.contains("tab-panel") && !node.classList.contains("active"));
    });
    if (!isAdmin && document.querySelector(".nav-button.active")?.dataset.tabTarget !== "mine") {
      app.switchTab("mine");
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
      app.loadMine();
    } else if (tab === "team" && app.state.userIsAdmin) {
      app.loadTeam();
    } else if (tab === "insights" && app.state.userIsAdmin) {
      app.loadInsights();
    } else if (tab === "admin-settings" && app.state.userIsAdmin) {
      app.loadSettings();
    } else if (tab === "conversion-log" && app.state.userIsAdmin) {
      app.loadConversions();
    } else if (tab === "diagnostics" && app.state.userIsAdmin) {
      app.loadDiagnostics();
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
      app.switchTab(next.dataset.tabTarget);
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

  function on(id, event, handler) {
    const node = element(id);
    if (node) {
      node.addEventListener(event, handler);
    }
  }

  Object.assign(app, {
    element,
    authHeaders,
    hideAuthTokenFromLocation,
    apiFetch,
    downloadCsv,
    claimsFromToken,
    timezoneFromClaims,
    applyTheme,
    applyRoleGate,
    switchTab,
    onTabKeydown,
    toast,
    setFieldError,
    clearFieldError,
    focusField,
    setBusy,
    refreshHandler,
    formValue,
    appendTextCell,
    appendAmountCell,
    labelRow,
    trimDecimal,
    unitLabel,
    sourceLabel,
    formatDate,
    formatExpenseDate,
    renderEmptyRow,
    renderLoadingRow,
    renderErrorRow,
    on
  });
})();
