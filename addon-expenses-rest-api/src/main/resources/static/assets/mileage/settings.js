(function () {
  const url = new URL(window.location.href);
  const authToken = url.searchParams.get("auth_token") || "";
  const sessionRows = [];

  if (authToken) {
    url.searchParams.delete("auth_token");
    history.replaceState({}, document.title, url.pathname + url.search + url.hash);
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

  function applyRoleGate() {
    const claims = claimsFromToken();
    const role = roleFromClaims(claims);
    const isAdmin = role === "OWNER" || role === "ADMIN";
    document.querySelectorAll("[data-admin-only]").forEach(element => {
      element.hidden = !isAdmin;
    });
    if (!isAdmin && document.querySelector(".nav-button.active")?.dataset.tabTarget?.startsWith("admin")) {
      switchTab("create");
    }
  }

  function switchTab(tab) {
    document.querySelectorAll(".tab-panel").forEach(panel => {
      panel.classList.toggle("active", panel.id === "tab-" + tab);
    });
    document.querySelectorAll(".nav-button").forEach(button => {
      button.classList.toggle("active", button.dataset.tabTarget === tab);
    });
    if (tab === "admin-settings") {
      loadSettings();
    } else if (tab === "conversion-log") {
      loadConversions();
    } else if (tab === "diagnostics") {
      loadDiagnostics();
    }
  }

  function toast(message, type) {
    const container = document.getElementById("toast-container");
    const node = document.createElement("div");
    node.className = "toast " + (type || "");
    node.textContent = message;
    container.appendChild(node);
    setTimeout(() => node.remove(), 3500);
  }

  function formValue(id) {
    const node = document.getElementById(id);
    return node && node.value ? node.value.trim() : "";
  }

  function mileagePayload() {
    return {
      date: formValue("field-date"),
      projectId: formValue("field-project") || null,
      taskId: formValue("field-task") || null,
      miles: formValue("field-miles"),
      rate: formValue("field-rate") || null,
      billable: document.getElementById("field-billable").checked,
      notes: formValue("field-notes") || null
    };
  }

  function previewMileage() {
    const payload = mileagePayload();
    apiFetch("/api/mileage/preview", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ miles: payload.miles, rate: payload.rate })
    }).then(result => {
      document.getElementById("preview-result").textContent =
        result.miles + " x " + result.rate + " = " + result.roundedAmount;
    }).catch(error => toast(error.message, "error"));
  }

  function createMileage(event) {
    event.preventDefault();
    const file = document.getElementById("field-receipt").files[0];
    if (file) {
      const body = new FormData();
      Object.entries(mileagePayload()).forEach(([key, value]) => {
        if (value !== null && value !== "") {
          body.append(key, value);
        }
      });
      body.append("file", file);
      apiFetch("/api/mileage/expenses", { method: "POST", body }).then(recordSubmission).catch(error => toast(error.message, "error"));
      return;
    }
    apiFetch("/api/mileage/expenses", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(mileagePayload())
    }).then(recordSubmission).catch(error => toast(error.message, "error"));
  }

  function recordSubmission(result) {
    sessionRows.unshift(result);
    document.getElementById("create-status").textContent = "Created " + result.expenseId;
    renderSessionRows();
    toast("Mileage expense created.");
  }

  function renderSessionRows() {
    const target = document.getElementById("user-activity");
    if (!sessionRows.length) {
      target.textContent = "Mileage submissions from this session appear here.";
      return;
    }
    target.replaceChildren();
    sessionRows.slice(0, 10).forEach(row => {
      const line = document.createElement("div");
      line.textContent = row.expenseId + " - " + row.miles + " miles - " + row.roundedAmount;
      target.appendChild(line);
    });
  }

  function loadCategories() {
    return apiFetch("/api/mileage/options/categories").then(data => {
      const input = document.getElementById("settings-input-category");
      const output = document.getElementById("settings-output-category");
      input.replaceChildren();
      output.replaceChildren();
      data.categories.forEach(category => {
        const inputOption = new Option(category.name + " (" + category.type + ")", category.id);
        const outputOption = new Option(category.name + " (" + category.type + ")", category.id);
        input.appendChild(inputOption);
        output.appendChild(outputOption);
      });
    });
  }

  function appendOption(select, value, label) {
    select.appendChild(new Option(label, value || ""));
  }

  function loadProjects() {
    return apiFetch("/api/mileage/options/projects").then(data => {
      const project = document.getElementById("field-project");
      project.replaceChildren();
      appendOption(project, "", "No project");
      (data.projects || []).forEach(item => appendOption(project, item.id, item.name));
    }).catch(error => toast(error.message, "error"));
  }

  function loadTasks(projectId) {
    const task = document.getElementById("field-task");
    task.replaceChildren();
    appendOption(task, "", "No task");
    task.disabled = !projectId;
    if (!projectId) {
      return Promise.resolve();
    }
    return apiFetch("/api/mileage/options/tasks?projectId=" + encodeURIComponent(projectId)).then(data => {
      (data.tasks || []).forEach(item => appendOption(task, item.id, item.name));
      task.disabled = false;
    }).catch(error => toast(error.message, "error"));
  }

  function loadSettings() {
    Promise.all([apiFetch("/api/mileage/settings"), loadCategories()])
      .then(([settings]) => {
        document.getElementById("settings-enabled").checked = settings.enabled;
        document.getElementById("settings-rate").value = settings.rate || "";
        document.getElementById("settings-unit").value = settings.unit || "mi";
        document.getElementById("settings-input-category").value = settings.inputCategoryId || "";
        document.getElementById("settings-output-category").value = settings.outputCategoryId || "";
        document.getElementById("settings-rounding").value = settings.roundingMode || "HALF_UP";
        document.getElementById("settings-convert-create").checked = settings.convertOnCreate;
        document.getElementById("settings-convert-update").checked = settings.convertOnUpdate;
        document.getElementById("settings-preserve-notes").checked = settings.preserveOriginalNotes;
        document.getElementById("settings-dry-run").checked = settings.dryRunMode;
        document.getElementById("settings-rate-override").checked = settings.allowUserRateOverride;
        document.getElementById("settings-template").value = settings.noteTemplate || "";
        document.getElementById("settings-status").textContent = settings.completeForNativeConversion ? "Ready" : "Needs configuration";
      })
      .catch(error => toast(error.message, "error"));
  }

  function saveSettings(event) {
    event.preventDefault();
    const payload = {
      enabled: document.getElementById("settings-enabled").checked,
      rate: formValue("settings-rate"),
      unit: formValue("settings-unit") || "mi",
      inputCategoryId: formValue("settings-input-category") || null,
      outputCategoryId: formValue("settings-output-category") || null,
      roundingMode: formValue("settings-rounding") || "HALF_UP",
      convertOnCreate: document.getElementById("settings-convert-create").checked,
      convertOnUpdate: document.getElementById("settings-convert-update").checked,
      preserveOriginalNotes: document.getElementById("settings-preserve-notes").checked,
      dryRunMode: document.getElementById("settings-dry-run").checked,
      allowUserRateOverride: document.getElementById("settings-rate-override").checked,
      noteTemplate: formValue("settings-template") || null
    };
    apiFetch("/api/mileage/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }).then(() => {
      toast("Settings saved.");
      loadDiagnostics();
    }).catch(error => toast(error.message, "error"));
  }

  function loadConversions() {
    apiFetch("/api/mileage/conversions?pageSize=50").then(data => {
      const rows = document.getElementById("conversion-rows");
      rows.replaceChildren();
      data.conversions.forEach(item => {
        const row = rows.insertRow();
        [item.expenseId, item.status, item.miles, item.roundedAmount, item.updatedAt].forEach(value => {
          const cell = row.insertCell();
          cell.textContent = value == null ? "" : String(value);
        });
      });
    }).catch(error => toast(error.message, "error"));
  }

  function loadDiagnostics() {
    apiFetch("/api/mileage/diagnostics").then(data => {
      const list = document.getElementById("diagnostics-list");
      list.replaceChildren();
      [["Installation", data.installationAvailable], ["Settings", data.settingsComplete], ["Native conversion", data.nativeConversionReady]].forEach(([label, value]) => {
        const dt = document.createElement("dt");
        const dd = document.createElement("dd");
        dt.textContent = label;
        dd.textContent = value ? "OK" : "Needs attention";
        list.append(dt, dd);
      });
      const warnings = document.getElementById("diagnostics-warnings");
      warnings.replaceChildren();
      (data.warnings || []).forEach(text => {
        const item = document.createElement("li");
        item.textContent = text;
        warnings.appendChild(item);
      });
    }).catch(error => toast(error.message, "error"));
  }

  document.querySelectorAll("[data-tab-target]").forEach(button => {
    button.addEventListener("click", () => switchTab(button.dataset.tabTarget));
  });
  document.getElementById("btn-preview").addEventListener("click", previewMileage);
  document.getElementById("mileage-form").addEventListener("submit", createMileage);
  document.getElementById("settings-form").addEventListener("submit", saveSettings);
  document.getElementById("btn-refresh-conversions").addEventListener("click", loadConversions);
  document.getElementById("btn-refresh-diagnostics").addEventListener("click", loadDiagnostics);
  const projectSelect = document.getElementById("field-project");
  if (projectSelect) {
    projectSelect.addEventListener("change", () => loadTasks(projectSelect.value));
  }

  applyRoleGate();
  loadProjects();
  renderSessionRows();
})();
