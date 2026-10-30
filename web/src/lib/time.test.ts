import { expect, it } from "vitest";
import { addDays, formatClock, formatDuration, fromMin, overlaps, toMin, weekOf } from "./time";

it("converts between HH:MM and minutes, wrapping past midnight", () => {
  expect(toMin("09:30")).toBe(570);
  expect(fromMin(570)).toBe("09:30");
  expect(fromMin(1440 + 15)).toBe("00:15");
  expect(fromMin(-15)).toBe("23:45");
});

it("does date arithmetic across month ends", () => {
  expect(addDays("2026-10-31", 1)).toBe("2026-11-01");
  expect(weekOf("2026-10-29")).toEqual(["2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31", "2026-11-01"]);
});

it("formats durations and clocks", () => {
  expect(formatDuration(90)).toBe("1h 30m");
  expect(formatDuration(120)).toBe("2h");
  expect(formatDuration(5)).toBe("5m");
  expect(formatClock(61.2)).toBe("01:02");
});

it("treats touching spans as not overlapping", () => {
  expect(overlaps(0, 10, 10, 20)).toBe(false);
  expect(overlaps(0, 11, 10, 20)).toBe(true);
});
