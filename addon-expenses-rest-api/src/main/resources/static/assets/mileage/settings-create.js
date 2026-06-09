(function () {
  const app = window.MileageSettingsApp;
  const maxReceiptBytes = 10 * 1024 * 1024;
  const allowedReceiptTypes = new Set([
    "image/png",
    "image/jpeg",
    "image/gif",
    "image/webp",
    "image/heic",
    "application/pdf"
  ]);

  function defaultDate() {
    const date = app.element("field-date");
    if (date && !date.value) {
      date.value = window.MileageDateHelpers.isoDate(
        window.MileageDateHelpers.todayForTimeZone(app.timezoneFromClaims())
      );
    }
  }

  function mileagePayload() {
    const createContext = app.state.createContext;
    const rateAllowed = Boolean(createContext.allowUserRateOverride);
    return {
      date: app.formValue("field-date"),
      projectId: resolveProjectId(app.formValue("field-project")),
      miles: app.formValue("field-miles"),
      rate: rateAllowed ? (app.formValue("field-rate") || null) : null,
      billable: app.element("field-billable").checked,
      notes: app.formValue("field-notes") || null
    };
  }

  function applyCreateContext(data) {
    const createContext = app.state.createContext = Object.assign({}, app.state.createContext, data || {});
    const rateRow = app.element("rate-field-row");
    const rate = app.element("field-rate");
    const preview = app.element("btn-preview");
    const submit = document.querySelector("#mileage-form button[type='submit']");
    const context = app.element("create-context");
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
    if (!app.element("create-context")) {
      return Promise.resolve();
    }
    return app.apiFetch("/api/mileage/create-context")
      .then(applyCreateContext)
      .catch(error => {
        app.element("create-context").textContent = "Mileage settings could not be loaded.";
        app.element("create-context").classList.add("context-warn");
        app.toast(error.message, "error");
      });
  }

  function previewMileage() {
    const button = app.element("btn-preview");
    const payload = mileagePayload();
    app.setBusy(button, true, "Previewing...");
    app.apiFetch("/api/mileage/preview", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ miles: payload.miles, rate: payload.rate })
    }).then(result => {
      const target = app.element("preview-result");
      const unit = app.state.createContext.unit || "mile";
      target.replaceChildren();
      const primary = document.createElement("div");
      primary.className = "amount-primary";
      primary.textContent = app.trimDecimal(result.miles) + " " + app.unitLabel(result.miles, unit) + " x " + app.trimDecimal(result.rate) + " = " + app.trimDecimal(result.calculatedAmount);
      const secondary = document.createElement("div");
      secondary.className = "amount-secondary";
      secondary.textContent = "Expense amount: " + result.roundedAmount;
      target.append(primary, secondary);
    }).catch(error => app.toast(error.message, "error")).finally(() => app.setBusy(button, false));
  }

  function setCreateBusy(busy) {
    const submit = document.querySelector("#mileage-form button[type='submit']");
    if (!submit) {
      return;
    }
    submit.disabled = busy || !app.state.createContext.complete;
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
      app.toast("Receipt file exceeds 10 MB.", "error");
      return false;
    }
    if (!allowedReceiptTypes.has(file.type || "")) {
      app.toast("Unsupported receipt file type.", "error");
      return false;
    }
    return true;
  }

  function createMileage(event) {
    event.preventDefault();
    app.clearFieldError("field-miles");
    const miles = app.formValue("field-miles");
    if (!miles || Number.isNaN(Number(miles)) || Number(miles) <= 0) {
      app.setFieldError("field-miles", "Enter the miles driven as a positive number.");
      app.focusField("field-miles");
      return;
    }
    const file = app.element("field-receipt").files[0];
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
      app.apiFetch("/api/mileage/expenses", { method: "POST", body })
        .then(recordSubmission)
        .catch(error => app.toast(error.message, "error"))
        .finally(() => setCreateBusy(false));
      return;
    }
    app.apiFetch("/api/mileage/expenses", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(mileagePayload())
    }).then(recordSubmission).catch(error => app.toast(error.message, "error")).finally(() => setCreateBusy(false));
  }

  function recordSubmission(result) {
    const status = app.element("create-status");
    if (status) {
      const unit = app.state.createContext.unit || "mile";
      const miles = app.trimDecimal(result.miles);
      status.textContent = miles && result.roundedAmount
        ? "Created " + miles + " " + app.unitLabel(result.miles, unit) + " → " + result.roundedAmount
        : "Mileage expense created";
      if (result.expenseId) {
        status.title = "Clockify expense " + result.expenseId;
      }
    }
    app.element("field-miles").value = "";
    app.element("field-rate").value = "";
    app.element("field-notes").value = "";
    app.element("field-receipt").value = "";
    const target = app.element("preview-result");
    if (target) {
      target.replaceChildren();
    }
    app.loadMine();
    app.toast("Mileage expense created.");
  }

  function loadProjects() {
    const datalist = app.element("project-options");
    if (!datalist) {
      return Promise.resolve();
    }
    return app.apiFetch("/api/mileage/options/projects").then(data => {
      datalist.replaceChildren();
      app.state.projectIdByName = {};
      (data.projects || [])
        .slice()
        .sort((left, right) => String(left.name || "").localeCompare(String(right.name || "")))
        .forEach(item => {
          if (!item || !item.id) {
            return;
          }
          const name = item.name || item.id;
          app.state.projectIdByName[name.toLowerCase()] = item.id;
          datalist.appendChild(new Option(name));
        });
      if (data.warning) {
        app.toast(data.warning, "error");
      }
    }).catch(error => app.toast(error.message, "error"));
  }

  function resolveProjectId(value) {
    const name = (value || "").trim();
    if (!name) {
      return null;
    }
    return app.state.projectIdByName[name.toLowerCase()] || null;
  }

  Object.assign(app, {
    defaultDate,
    mileagePayload,
    applyCreateContext,
    loadCreateContext,
    previewMileage,
    createMileage,
    loadProjects,
    resolveProjectId
  });
})();
