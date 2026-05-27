#!/usr/bin/env node
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

const helperSource = fs.readFileSync(
  "addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings-date.js",
  "utf8"
);

const context = {
  window: {},
  Intl,
  Date
};
context.window.window = context.window;

vm.createContext(context);
vm.runInContext(helperSource, context, { filename: "settings-date.js" });

const helpers = context.window.MileageDateHelpers;
assert.ok(helpers, "MileageDateHelpers is exposed on window");

const fixedInstant = new Date("2026-05-31T01:00:00Z");

assert.equal(
  JSON.stringify(helpers.dateRangeForPreset("this_week", "America/Los_Angeles", fixedInstant)),
  JSON.stringify({ from: "2026-05-24", to: "2026-05-30" })
);

assert.equal(
  helpers.todayForTimeZone("America/Los_Angeles", fixedInstant).getDate(),
  30
);

const fallbackDate = helpers.todayForTimeZone("Not/A_Time_Zone", fixedInstant);
const expectedLocalDate = new Date(
  fixedInstant.getFullYear(),
  fixedInstant.getMonth(),
  fixedInstant.getDate()
);
assert.equal(helpers.isoDate(fallbackDate), helpers.isoDate(expectedLocalDate));
