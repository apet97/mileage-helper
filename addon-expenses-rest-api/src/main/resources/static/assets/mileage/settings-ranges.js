(function () {
  const app = window.MileageSettingsApp;
  const rangePresets = {
    this_week: "This week",
    custom: "Custom",
    this_month: "This month",
    last_week: "Last week",
    last_month: "Last month",
    this_year: "This year",
    last_year: "Last year"
  };

  function initDateRanges() {
    ["mine", "team", "insights", "conversion"].forEach(initDateRange);
  }

  function initDateRange(scope) {
    const preset = app.element(scope + "-range-preset");
    if (!preset) {
      return;
    }
    applyDateRange(scope);
    preset.addEventListener("change", () => {
      applyDateRange(scope);
      reloadRangeScope(scope);
    });
    ["from", "to"].forEach(part => {
      const input = app.element(scope + "-range-" + part);
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
    const preset = app.element(scope + "-range-preset");
    const custom = app.element(scope + "-range-custom");
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
    app.state.pageState[scope] = 0;
    reloadScope(scope);
  }

  function reloadScope(scope) {
    if (scope === "mine") {
      app.loadMine();
    } else if (scope === "team" && app.state.userIsAdmin) {
      app.loadTeam();
    } else if (scope === "insights" && app.state.userIsAdmin) {
      app.loadInsights();
    } else if (scope === "conversion" && app.state.userIsAdmin) {
      app.loadConversions();
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
    return "&page=" + (app.state.pageState[scope] || 0);
  }

  function csvPath(scope, path) {
    const query = rangeQuery(scope);
    if (query === null) {
      return null;
    }
    return path + (query ? "?" + query.slice(1) : "") + userFilterQuery(scope);
  }

  function selectedDateRange(scope) {
    const preset = app.element(scope + "-range-preset");
    if (!preset) {
      return dateRangeForPreset("this_week");
    }
    if (preset.value !== "custom") {
      const range = dateRangeForPreset(preset.value);
      setRangeInputs(scope, range);
      return range;
    }
    const from = app.formValue(scope + "-range-from");
    const to = app.formValue(scope + "-range-to");
    return { from, to };
  }

  function validSelectedDateRange(scope) {
    const range = selectedDateRange(scope);
    app.clearFieldError(scope + "-range-from");
    app.clearFieldError(scope + "-range-to");
    if (!range) {
      return null;
    }
    if (!range.from || !range.to) {
      const missing = !range.from ? scope + "-range-from" : scope + "-range-to";
      app.setFieldError(missing, "Choose both From and To dates.");
      app.focusField(missing);
      app.toast("Choose both From and To dates.", "error");
      return null;
    }
    if (range.from > range.to) {
      app.setFieldError(scope + "-range-from", "From date must be on or before To date.");
      app.focusField(scope + "-range-from");
      app.toast("From date must be on or before To date.", "error");
      return null;
    }
    return range;
  }

  function setRangeInputs(scope, range) {
    const from = app.element(scope + "-range-from");
    const to = app.element(scope + "-range-to");
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
    return window.MileageDateHelpers.dateRangeForPreset(preset, app.timezoneFromClaims());
  }

  function userFilterQuery(scope) {
    const selectId = scope === "team" ? "team-user-filter" : scope === "conversion" ? "conversion-user-filter" : "";
    if (!selectId) {
      return "";
    }
    const userId = app.formValue(selectId);
    return userId ? "&userId=" + encodeURIComponent(userId) : "";
  }

  function reportPath(scope, userId) {
    return printablePath("/iframe/report", scope, userId);
  }

  function packetPath(scope, userId) {
    return printablePath("/iframe/reimbursement-packet", scope, userId);
  }

  function printablePath(basePath, scope, userId) {
    const range = validSelectedDateRange(scope);
    if (!range) {
      return null;
    }
    let path = basePath + "?from=" + encodeURIComponent(range.from) + "&to=" + encodeURIComponent(range.to);
    path += "&scope=" + encodeURIComponent(scope); // "mine" pins to the requester; "team" = admin all-users/filter
    if (userId) {
      path += "&userId=" + encodeURIComponent(userId);
    }
    if (app.authToken) {
      path += "&auth_token=" + encodeURIComponent(app.authToken);
    }
    return path;
  }

  Object.assign(app, {
    initDateRanges,
    reloadRangeScope,
    reloadScope,
    rangeQuery,
    pageParam,
    csvPath,
    selectedDateRange,
    validSelectedDateRange,
    setRangeInputs,
    dateRangeForPreset,
    userFilterQuery,
    reportPath,
    packetPath
  });
})();
