#!/usr/bin/env node
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

const assetDir = "addon-expenses-rest-api/src/main/resources/static/assets/mileage";
const scriptOrder = [
  "settings-date.js",
  "settings-core.js",
  "settings-ranges.js",
  "settings-create.js",
  "settings-admin.js",
  "settings-tables.js",
  "settings.js"
];

class ClassList {
  constructor(node) {
    this.node = node;
    this.values = new Set();
  }

  add(...names) {
    names.forEach(name => this.values.add(name));
    this.sync();
  }

  remove(...names) {
    names.forEach(name => this.values.delete(name));
    this.sync();
  }

  contains(name) {
    return this.values.has(name);
  }

  toggle(name, force) {
    const enabled = force === undefined ? !this.values.has(name) : Boolean(force);
    if (enabled) {
      this.values.add(name);
    } else {
      this.values.delete(name);
    }
    this.sync();
    return enabled;
  }

  sync() {
    this.node.className = Array.from(this.values).join(" ");
  }
}

class FakeElement {
  constructor(tagName = "div", id = "") {
    this.tagName = tagName.toUpperCase();
    this.id = id;
    this.value = "";
    this.checked = false;
    this.hidden = false;
    this.disabled = false;
    this.textContent = "";
    this.className = "";
    this.children = [];
    this.options = [];
    this.files = [];
    this.dataset = {};
    this.attributes = new Map();
    this.listeners = new Map();
    this.classList = new ClassList(this);
    this.parentNode = null;
    this.type = "";
    this.download = "";
    this.href = "";
  }

  append(...nodes) {
    nodes.forEach(node => this.appendChild(node));
  }

  appendChild(node) {
    if (node == null) {
      return node;
    }
    if (typeof node === "string") {
      node = new FakeTextNode(node);
    }
    node.parentNode = this;
    this.children.push(node);
    if (node.tagName === "OPTION") {
      this.options.push(node);
    }
    return node;
  }

  replaceChildren(...nodes) {
    this.children = [];
    this.options = [];
    nodes.forEach(node => this.appendChild(node));
  }

  setAttribute(name, value) {
    this.attributes.set(name, String(value));
    if (name === "role") {
      this.role = String(value);
    }
  }

  getAttribute(name) {
    return this.attributes.get(name) || null;
  }

  removeAttribute(name) {
    this.attributes.delete(name);
  }

  addEventListener(name, handler) {
    const handlers = this.listeners.get(name) || [];
    handlers.push(handler);
    this.listeners.set(name, handlers);
  }

  insertAdjacentElement(_position, node) {
    return this.appendChild(node);
  }

  insertRow() {
    const row = new FakeRow();
    this.appendChild(row);
    return row;
  }

  focus() {
    this.focused = true;
  }

  click() {
    this.clicked = true;
  }

  remove() {
    if (this.parentNode) {
      this.parentNode.children = this.parentNode.children.filter(child => child !== this);
      this.parentNode.options = this.parentNode.options.filter(child => child !== this);
    }
  }

  closest(selector) {
    if (selector === "button" && this.tagName === "BUTTON") {
      return this;
    }
    return null;
  }
}

class FakeTextNode extends FakeElement {
  constructor(text) {
    super("#text");
    this.textContent = String(text);
  }
}

class FakeRow extends FakeElement {
  constructor() {
    super("tr");
    this.cells = [];
  }

  insertCell() {
    const cell = new FakeElement("td");
    this.cells.push(cell);
    this.appendChild(cell);
    return cell;
  }
}

class FakeHeaders {
  constructor(values = {}) {
    this.values = Object.fromEntries(
      Object.entries(values).map(([key, value]) => [key.toLowerCase(), value])
    );
  }

  get(name) {
    return this.values[String(name).toLowerCase()] || "";
  }
}

class FakeResponse {
  constructor(body = {}, options = {}) {
    this.body = body;
    this.status = options.status || 200;
    this.ok = this.status >= 200 && this.status < 300;
    this.headers = new FakeHeaders(options.headers);
  }

  async json() {
    return this.body;
  }

  async blob() {
    return this.body;
  }
}

class FakeOption extends FakeElement {
  constructor(label, value = label) {
    super("option");
    this.textContent = label;
    this.value = value;
  }
}

class FakeDocument {
  constructor() {
    this.elements = new Map();
    this.specialSelectors = new Map();
    this.body = new FakeElement("body");
    this.documentElement = new FakeElement("html");
    this.title = "Mileage";
    this.listeners = new Map();
  }

  register(node) {
    if (node.id) {
      this.elements.set(node.id, node);
    }
    return node;
  }

  reset() {
    this.elements.clear();
    this.specialSelectors.clear();
    this.body.replaceChildren();
  }

  getElementById(id) {
    return this.elements.get(id) || null;
  }

  createElement(tagName) {
    return new FakeElement(tagName);
  }

  createTextNode(text) {
    return new FakeTextNode(text);
  }

  querySelector(selector) {
    if (this.specialSelectors.has(selector)) {
      return this.specialSelectors.get(selector);
    }
    if (selector.startsWith("#") && !selector.includes(" ")) {
      return this.getElementById(selector.slice(1));
    }
    return null;
  }

  querySelectorAll() {
    return [];
  }

  addEventListener(name, handler) {
    const handlers = this.listeners.get(name) || [];
    handlers.push(handler);
    this.listeners.set(name, handlers);
  }
}

function createContext() {
  const document = new FakeDocument();
  const fetchLog = [];
  const responses = new Map();
  class HarnessURL extends URL {
    static createObjectURL() {
      return "blob:mileage-settings-test";
    }

    static revokeObjectURL() {}
  }

  const window = {
    __MILEAGE_SETTINGS_TEST__: true,
    location: {
      href: "https://addon.example/iframe/settings?auth_token=header.payload.signature",
      pathname: "/iframe/settings",
      search: "?auth_token=header.payload.signature",
      hash: ""
    },
    history: {
      replaceState(_state, _title, path) {
        window.location.replacedWith = path;
      }
    },
    document,
    URL: HarnessURL,
    Blob: class Blob {},
    FormData: class FormData {
      constructor() {
        this.entries = [];
      }

      append(key, value) {
        this.entries.push([key, value]);
      }
    },
    File: class File {},
    Option: FakeOption,
    console,
    Intl,
    Date,
    setTimeout(callback) {
      callback();
      return 0;
    },
    clearTimeout() {},
    atob(value) {
      return Buffer.from(value, "base64").toString("binary");
    },
    open(path) {
      window.openedPath = path;
      return {};
    },
    matchMedia() {
      return { matches: false };
    }
  };
  window.window = window;

  const context = {
    window,
    document,
    history: window.history,
    URL: HarnessURL,
    Blob: window.Blob,
    FormData: window.FormData,
    File: window.File,
    Option: FakeOption,
    console,
    Intl,
    Date,
    Promise,
    Array,
    Object,
    String,
    Number,
    Boolean,
    Math,
    JSON,
    Error,
    Set,
    Map,
    RegExp,
    encodeURIComponent,
    decodeURIComponent,
    setTimeout: window.setTimeout,
    clearTimeout: window.clearTimeout,
    atob: window.atob,
    fetch(path, init = {}) {
      fetchLog.push({ path, init });
      return Promise.resolve(responses.get(path) || new FakeResponse({}));
    }
  };

  vm.createContext(context);
  scriptOrder.forEach(file => {
    const source = fs.readFileSync(`${assetDir}/${file}`, "utf8");
    vm.runInContext(source, context, { filename: file });
  });

  return { context, window, document, fetchLog, responses };
}

const harness = createContext();
const app = harness.window.MileageSettingsApp;

function resetDom() {
  harness.document.reset();
  harness.fetchLog.length = 0;
  harness.responses.clear();
  harness.window.openedPath = "";
  app.state.projectIdByName = {};
  app.state.pageState = { mine: 0, team: 0, conversion: 0 };
  app.state.createContext = {
    rate: null,
    unit: "mile",
    allowUserRateOverride: false,
    complete: true
  };
}

function element(id, tagName = "input", values = {}) {
  return harness.document.register(Object.assign(new FakeElement(tagName, id), values));
}

function rangeElements(scope, from = "2026-06-01", to = "2026-06-07") {
  element(`${scope}-range-preset`, "select", { value: "custom" });
  element(`${scope}-range-from`, "input", { value: from });
  element(`${scope}-range-to`, "input", { value: to });
}

function tableElement(id) {
  return element(id, "tbody");
}

function button(id) {
  return Object.assign(new FakeElement("button", id), {
    textContent: "Button",
    closest(selector) {
      return selector === "button" ? this : null;
    }
  });
}

async function assertCreatePayloadOmitsUserIdAndTaskId() {
  resetDom();
  app.state.createContext.allowUserRateOverride = true;
  element("field-date", "input", { value: "2026-06-13" });
  element("field-project", "input", { value: "" });
  element("field-miles", "input", { value: "12.4" });
  element("field-rate", "input", { value: "0.725" });
  element("field-billable", "input", { checked: true });
  element("field-notes", "textarea", { value: "Client visit" });

  const payload = app.mileagePayload();

  assert.equal(Object.hasOwn(payload, "userId"), false);
  assert.equal(Object.hasOwn(payload, "taskId"), false);
  assert.equal(JSON.stringify(payload), JSON.stringify({
    date: "2026-06-13",
    projectId: null,
    miles: "12.4",
    rate: "0.725",
    billable: true,
    notes: "Client visit"
  }));
}

async function assertProjectNameMapsToProjectId() {
  resetDom();
  element("project-options", "datalist");
  element("field-date", "input", { value: "2026-06-13" });
  element("field-project", "input", { value: "  Apollo  " });
  element("field-miles", "input", { value: "8" });
  element("field-rate", "input", { value: "" });
  element("field-billable", "input", { checked: false });
  element("field-notes", "textarea", { value: "" });
  harness.responses.set("/api/mileage/options/projects", new FakeResponse({
    projects: [
      { id: "project-zeus", name: "Zeus" },
      { id: "project-apollo", name: "Apollo" }
    ]
  }));

  await app.loadProjects();

  assert.equal(app.resolveProjectId("apollo"), "project-apollo");
  assert.equal(app.mileagePayload().projectId, "project-apollo");
}

async function assertCsvExportsUseAuthHeaderNotQueryToken() {
  resetDom();
  rangeElements("mine");
  const exportButton = button("btn-export-mine");
  harness.responses.set("/api/mileage/mine.csv?from=2026-06-01&to=2026-06-07", new FakeResponse("csv", {
    headers: { "Content-Disposition": "attachment; filename=\"mine.csv\"" }
  }));

  app.handleCsvExport({
    target: exportButton,
    preventDefault() {}
  });
  await Promise.resolve();
  await Promise.resolve();

  assert.equal(harness.fetchLog.length, 1);
  assert.equal(harness.fetchLog[0].path, "/api/mileage/mine.csv?from=2026-06-01&to=2026-06-07");
  assert.equal(harness.fetchLog[0].path.includes("auth_token="), false);
  assert.equal(harness.fetchLog[0].init.headers.Authorization, "Bearer header.payload.signature");
}

async function assertApiFetchUsesCurrentRuntimeAuthToken() {
  resetDom();
  app.authToken = "runtime.header.signature";
  harness.responses.set("/api/mileage/create-context", new FakeResponse({ complete: true }));

  await app.apiFetch("/api/mileage/create-context");

  assert.equal(harness.fetchLog.length, 1);
  assert.equal(harness.fetchLog[0].init.headers.Authorization, "Bearer runtime.header.signature");
  app.authToken = "header.payload.signature";
}

async function assertReportOpenCarriesAuthTokenOnlyForIframeReport() {
  resetDom();
  rangeElements("mine");
  const reportButton = button("btn-report-mine");

  app.handleReportClick({
    target: reportButton,
    preventDefault() {}
  });

  assert.ok(harness.window.openedPath.startsWith("/iframe/report?"));
  assert.ok(harness.window.openedPath.includes("auth_token=header.payload.signature"));
  assert.equal(harness.fetchLog.some(entry => String(entry.path).includes("auth_token=")), false);
}

async function assertTeamReportCarriesSelectedUserName() {
  resetDom();
  rangeElements("team");
  const select = element("team-user-filter", "select", { value: "user-two", selectedIndex: 1 });
  select.appendChild(new FakeOption("All users", ""));
  select.appendChild(new FakeOption("Ada Lovelace", "user-two"));
  const reportButton = button("btn-report-team");

  app.handleReportClick({
    target: reportButton,
    preventDefault() {}
  });

  assert.ok(harness.window.openedPath.includes("userId=user-two"));
  assert.ok(harness.window.openedPath.includes("selectedUserName=Ada%20Lovelace"));
}

async function assertPaginationAddsPageAfterLockedPageSize() {
  resetDom();
  rangeElements("mine");
  tableElement("mine-rows");
  element("mine-pager", "nav");
  app.state.pageState.mine = 2;
  harness.responses.set("/api/mileage/mine?pageSize=50&from=2026-06-01&to=2026-06-07&page=2", new FakeResponse({
    conversions: [],
    page: 2,
    pageSize: 50,
    totalElements: 140,
    totalPages: 3
  }));

  await app.loadMine();

  assert.equal(harness.fetchLog[0].path, "/api/mileage/mine?pageSize=50&from=2026-06-01&to=2026-06-07&page=2");
}

async function assertSettingsSaveSendsNoteTemplateAndRate() {
  resetDom();
  const submit = new FakeElement("button");
  submit.textContent = "Save";
  harness.document.specialSelectors.set("#settings-form button[type='submit']", submit);
  element("settings-enabled", "input", { checked: true });
  element("settings-rate", "input", { value: "0.725" });
  element("settings-mileage-category", "select", { value: "cat-mileage" });
  element("settings-convert-create", "input", { checked: true });
  element("settings-convert-update", "input", { checked: false });
  element("settings-rate-override", "input", { checked: true });
  element("settings-note-template", "textarea", {
    value: "Trip {{miles}} {{unit}} at {{rate}}"
  });
  harness.responses.set("/api/mileage/settings", new FakeResponse({}));

  await app.saveSettings({ preventDefault() {} });

  assert.equal(harness.fetchLog[0].path, "/api/mileage/settings");
  assert.equal(harness.fetchLog[0].init.method, "PUT");
  const body = JSON.parse(harness.fetchLog[0].init.body);
  assert.equal(body.rate, "0.725");
  assert.equal(body.noteTemplate, "Trip {{miles}} {{unit}} at {{rate}}");
}

async function assertSettingsSaveWarningsToastAsErrors() {
  resetDom();
  const submit = new FakeElement("button");
  submit.textContent = "Save";
  harness.document.specialSelectors.set("#settings-form button[type='submit']", submit);
  const toastContainer = element("toast-container", "div");
  element("settings-enabled", "input", { checked: true });
  element("settings-rate", "input", { value: "0.725" });
  element("settings-mileage-category", "select", { value: "cat-mileage" });
  element("settings-convert-create", "input", { checked: true });
  element("settings-convert-update", "input", { checked: true });
  element("settings-rate-override", "input", { checked: false });
  element("settings-note-template", "textarea", { value: "" });
  harness.responses.set("/api/mileage/settings", new FakeResponse({ warnings: ["sync warning"] }));

  await app.saveSettings({ preventDefault() {} });

  const messages = toastContainer.children.map(node => node.children[0].textContent);
  assert.deepEqual(messages, ["sync warning"]);
  assert.equal(toastContainer.children[0].className, "toast error");
  assert.equal(toastContainer.children[0].getAttribute("role"), "alert");
}

async function assertDiagnosticsRendersChecklistAndHealth() {
  resetDom();
  element("diagnostics-list", "dl");
  element("diagnostics-warnings", "ul");
  element("diagnostics-checklist", "ul");
  element("diagnostics-health", "dl");
  harness.responses.set("/api/mileage/diagnostics", new FakeResponse({
    installationAvailable: true,
    settingsComplete: true,
    nativeConversionReady: false,
    warnings: ["native conversion not ready"],
    checklist: [
      { key: "installation", label: "Add-on installed", complete: true, action: "" },
      { key: "category", label: "Mileage category configured", complete: false, action: "Use or Repair Mileage Category" }
    ],
    operationalHealth: {
      pendingJobs: 2,
      claimedJobs: 0,
      failedJobs: 1,
      oldestPendingAgeSeconds: 301,
      lastCompletedJobAt: "2026-06-15T10:00:00Z"
    }
  }));

  await app.loadDiagnostics();

  assert.ok(harness.document.getElementById("diagnostics-checklist").children.length >= 2);
  assert.ok(harness.document.getElementById("diagnostics-health").children.length >= 8);
}

async function assertConversionsRenderSkipReasonLabels() {
  resetDom();
  rangeElements("conversion");
  const rows = tableElement("conversion-rows");
  element("conversion-pager", "nav");
  harness.responses.set("/api/mileage/conversions?pageSize=50&from=2026-06-01&to=2026-06-07&page=0", new FakeResponse({
    conversions: [{
      expenseDate: "2026-06-02",
      expenseId: "exp-skipped",
      source: "WEBHOOK_CREATED",
      status: "SKIPPED",
      skipReason: "ALREADY_CONVERTED",
      userName: "Ada Lovelace",
      miles: "12.4",
      rate: "0.725",
      calculatedAmount: "8.990",
      roundedAmount: "8.99",
      updatedAt: "2026-06-02T10:00:00Z"
    }],
    totalElements: 1,
    page: 0,
    pageSize: 50,
    totalPages: 1
  }));

  await app.loadConversions();

  assert.equal(rows.children[0].children[4].textContent, "SKIPPED — Already converted");
}

for (const test of [
  assertCreatePayloadOmitsUserIdAndTaskId,
  assertProjectNameMapsToProjectId,
  assertCsvExportsUseAuthHeaderNotQueryToken,
  assertApiFetchUsesCurrentRuntimeAuthToken,
  assertReportOpenCarriesAuthTokenOnlyForIframeReport,
  assertTeamReportCarriesSelectedUserName,
  assertPaginationAddsPageAfterLockedPageSize,
  assertSettingsSaveSendsNoteTemplateAndRate,
  assertSettingsSaveWarningsToastAsErrors,
  assertDiagnosticsRendersChecklistAndHealth,
  assertConversionsRenderSkipReasonLabels
]) {
  await test();
}

console.log("Mileage settings behavior checks passed");
