(function () {
  const app = window.MileageSettingsApp;
  if (!app) {
    return;
  }

  document.querySelectorAll("[data-tab-target]").forEach(button => {
    button.addEventListener("click", () => app.switchTab(button.dataset.tabTarget));
  });
  const tablist = document.querySelector("[role=\"tablist\"]");
  if (tablist) {
    tablist.addEventListener("keydown", app.onTabKeydown);
  }
  document.addEventListener("click", app.handleCsvExport);
  document.addEventListener("click", app.handleReportClick);
  app.on("btn-preview", "click", app.previewMileage);
  app.on("mileage-form", "submit", app.createMileage);
  app.on("settings-form", "submit", app.saveSettings);
  app.on("btn-setup-mileage-category", "click", app.setupMileageCategory);
  app.on("btn-refresh-mine", "click", app.refreshHandler(app.loadMine));
  app.on("btn-refresh-team", "click", app.refreshHandler(app.loadTeam));
  app.on("btn-refresh-conversions", "click", app.refreshHandler(app.loadConversions));
  app.on("btn-refresh-diagnostics", "click", app.refreshHandler(app.loadDiagnostics));
  app.on("team-user-filter", "change", () => { app.state.pageState.team = 0; app.loadTeam(); });
  app.on("conversion-user-filter", "change", () => { app.state.pageState.conversion = 0; app.loadConversions(); });
  app.on("settings-note-template", "input", app.renderNotePreview);
  app.on("settings-rate", "input", app.renderNotePreview);

  app.state.tokenClaims = app.claimsFromToken();
  app.applyTheme();
  app.state.userIsAdmin = app.applyRoleGate();
  app.initDateRanges();
  app.defaultDate();
  app.loadCreateContext();
  app.loadProjects();
  app.loadUserOptions();
  app.switchTab(app.state.userIsAdmin && window.location.pathname.endsWith("/iframe/settings") ? "admin-settings" : "mine");
  setTimeout(app.hideAuthTokenFromLocation, 0);
})();
