(function () {
  const app = window.MileageSettingsApp;
  const MINE_LABELS = ["Date", "Expense", "Source", "Status", "Miles", "Rate", "Amount", "Updated"];
  const TEAM_LABELS = ["Date", "Expense", "User", "Source", "Status", "Miles", "Rate", "Amount", "Updated"];
  const CONVERSION_LABELS = ["Date", "Expense", "Source", "User", "Status", "Miles", "Rate", "Amount", "Updated"];

  function loadMine() {
    const rows = app.element("mine-rows");
    if (!rows) {
      return Promise.resolve();
    }
    const query = app.rangeQuery("mine");
    if (query === null) {
      return Promise.resolve();
    }
    app.renderLoadingRow(rows, MINE_LABELS.length);
    return app.apiFetch("/api/mileage/mine?pageSize=50" + query + app.pageParam("mine"))
      .then(data => {
        renderMileageRows(rows, data.conversions || [], false, "No mileage rows yet.");
        renderPager("mine", data);
      })
      .catch(error => {
        app.renderErrorRow(rows, MINE_LABELS.length);
        app.toast(error.message, "error");
      });
  }

  function loadTeam() {
    const rows = app.element("team-rows");
    if (!rows) {
      return Promise.resolve();
    }
    const query = app.rangeQuery("team");
    if (query === null) {
      return Promise.resolve();
    }
    app.renderLoadingRow(rows, TEAM_LABELS.length);
    return app.apiFetch("/api/mileage/team?pageSize=50" + query + app.userFilterQuery("team") + app.pageParam("team"))
      .then(data => {
        renderMileageRows(rows, data.conversions || [], true, "No team mileage rows yet.");
        renderPager("team", data);
      })
      .catch(error => {
        app.renderErrorRow(rows, TEAM_LABELS.length);
        app.toast(error.message, "error");
      });
  }

  function renderMileageRows(rows, items, includeUser, emptyText) {
    rows.replaceChildren();
    const labels = includeUser ? TEAM_LABELS : MINE_LABELS;
    if (!items.length) {
      app.renderEmptyRow(rows, labels.length, emptyText, "Adjust the date range or create a new mileage expense.");
      return;
    }
    items.forEach(item => {
      const row = rows.insertRow();
      app.appendTextCell(row, app.formatExpenseDate(item.expenseDate));
      app.appendTextCell(row, item.expenseId);
      if (includeUser) {
        app.appendTextCell(row, item.userName || item.userId);
      }
      app.appendTextCell(row, item.sourceLabel || app.sourceLabel(item.source));
      app.appendTextCell(row, item.status);
      app.appendTextCell(row, app.trimDecimal(item.miles));
      app.appendTextCell(row, app.trimDecimal(item.rate));
      app.appendAmountCell(row, item.calculatedAmount, item.roundedAmount);
      app.appendTextCell(row, app.formatDate(item.updatedAt));
      app.labelRow(row, labels);
    });
  }

  function loadConversions() {
    const rows = app.element("conversion-rows");
    if (!rows) {
      return Promise.resolve();
    }
    const query = app.rangeQuery("conversion");
    if (query === null) {
      return Promise.resolve();
    }
    app.renderLoadingRow(rows, CONVERSION_LABELS.length);
    return app.apiFetch("/api/mileage/conversions?pageSize=50" + query + app.userFilterQuery("conversion") + app.pageParam("conversion")).then(data => {
      rows.replaceChildren();
      const items = data.conversions || [];
      if (!items.length) {
        app.renderEmptyRow(rows, CONVERSION_LABELS.length, "No conversion rows yet.", "Conversions appear here as native and add-on expenses are processed.");
        renderPager("conversion", data);
        return;
      }
      items.forEach(item => {
        const row = rows.insertRow();
        app.appendTextCell(row, app.formatExpenseDate(item.expenseDate));
        app.appendTextCell(row, item.expenseId);
        app.appendTextCell(row, item.sourceLabel || app.sourceLabel(item.source));
        app.appendTextCell(row, item.userName || item.userId);
        app.appendTextCell(row, item.status);
        app.appendTextCell(row, app.trimDecimal(item.miles));
        app.appendTextCell(row, app.trimDecimal(item.rate));
        app.appendAmountCell(row, item.calculatedAmount, item.roundedAmount);
        app.appendTextCell(row, app.formatDate(item.updatedAt));
        app.labelRow(row, CONVERSION_LABELS);
      });
      renderPager("conversion", data);
    }).catch(error => {
      app.renderErrorRow(rows, CONVERSION_LABELS.length);
      app.toast(error.message, "error");
    });
  }

  function renderPager(scope, data) {
    const pager = app.element(scope + "-pager");
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
      app.state.pageState[scope] = Math.max(0, page - 1);
      app.reloadScope(scope);
    });
    const next = document.createElement("button");
    next.type = "button";
    next.textContent = "Next";
    next.disabled = page + 1 >= totalPages;
    next.addEventListener("click", () => {
      app.state.pageState[scope] = page + 1;
      app.reloadScope(scope);
    });
    buttons.append(prev, next);
    pager.append(label, buttons);
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
    let userName = "";
    if (config[1]) {
      const select = app.element(config[1]);
      userId = app.formValue(config[1]); // empty selection => all users (reportPath omits userId)
      if (select && select.selectedIndex >= 0 && userId) {
        userName = select.options[select.selectedIndex].textContent || "";
      }
    }
    const path = app.reportPath(config[0], userId, userName);
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
    app.setBusy(button, true, "Exporting...");
    Promise.resolve(app.downloadCsv(app.csvPath(exportConfig[0], exportConfig[1]), exportConfig[2])).finally(() => app.setBusy(button, false));
  }

  Object.assign(app, {
    loadMine,
    loadTeam,
    renderMileageRows,
    loadConversions,
    renderPager,
    handleReportClick,
    handleCsvExport
  });
})();
