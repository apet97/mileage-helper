(function (root) {
  const validPresets = new Set([
    "this_week",
    "custom",
    "this_month",
    "last_week",
    "last_month",
    "this_year",
    "last_year"
  ]);

  function todayForTimeZone(timeZone, now) {
    const date = now || new Date();
    const parts = timeZone ? datePartsForTimeZone(timeZone, date) : null;
    if (parts) {
      return new Date(parts.year, parts.month - 1, parts.day);
    }
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
  }

  function datePartsForTimeZone(timeZone, date) {
    try {
      const parts = new Intl.DateTimeFormat("en-CA", {
        timeZone,
        year: "numeric",
        month: "2-digit",
        day: "2-digit"
      }).formatToParts(date);
      const values = {};
      parts.forEach(part => {
        if (part.type === "year" || part.type === "month" || part.type === "day") {
          values[part.type] = Number(part.value);
        }
      });
      if (values.year && values.month && values.day) {
        return values;
      }
    } catch (e) {
      return null;
    }
    return null;
  }

  function dateRangeForPreset(preset, timeZone, now) {
    const normalizedPreset = validPresets.has(preset) ? preset : "this_week";
    const today = todayForTimeZone(timeZone, now);
    const weekStart = startOfWeek(today);
    if (normalizedPreset === "this_month") {
      return rangeForDates(new Date(today.getFullYear(), today.getMonth(), 1),
        new Date(today.getFullYear(), today.getMonth() + 1, 0));
    }
    if (normalizedPreset === "last_week") {
      return rangeForDates(addDays(weekStart, -7), addDays(weekStart, -1));
    }
    if (normalizedPreset === "last_month") {
      return rangeForDates(new Date(today.getFullYear(), today.getMonth() - 1, 1),
        new Date(today.getFullYear(), today.getMonth(), 0));
    }
    if (normalizedPreset === "this_year") {
      return rangeForDates(new Date(today.getFullYear(), 0, 1), new Date(today.getFullYear(), 11, 31));
    }
    if (normalizedPreset === "last_year") {
      return rangeForDates(new Date(today.getFullYear() - 1, 0, 1), new Date(today.getFullYear() - 1, 11, 31));
    }
    return rangeForDates(weekStart, addDays(weekStart, 6));
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

  root.MileageDateHelpers = {
    dateRangeForPreset,
    isoDate,
    todayForTimeZone
  };
})(window);
