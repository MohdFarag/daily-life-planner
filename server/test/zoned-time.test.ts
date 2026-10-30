import { describe, expect, it } from "vitest";
import { isTimeZone, localDate, offsetMinutes, startOfDay } from "../src/shared/zoned-time.js";

describe("zoned time", () => {
  it("finds the start of a local day, including daylight-saving changes", () => {
    expect(startOfDay("2026-03-08", "America/New_York").toISOString()).toBe("2026-03-08T05:00:00.000Z");
    expect(startOfDay("2026-03-09", "America/New_York").toISOString()).toBe("2026-03-09T04:00:00.000Z");
    expect(startOfDay("2026-11-01", "America/New_York").toISOString()).toBe("2026-11-01T04:00:00.000Z");
    expect(startOfDay("2026-10-30", "Asia/Kolkata").toISOString()).toBe("2026-10-29T18:30:00.000Z");
    expect(startOfDay("2026-10-30", "UTC").toISOString()).toBe("2026-10-30T00:00:00.000Z");
  });

  it("reads local dates and offsets", () => {
    const instant = new Date("2026-10-29T22:30:00Z");
    expect(localDate(instant, "Asia/Riyadh")).toBe("2026-10-30");
    expect(localDate(instant, "UTC")).toBe("2026-10-29");
    expect(offsetMinutes(instant, "Asia/Riyadh")).toBe(180);
  });

  it("validates time zone names", () => {
    expect(isTimeZone("Africa/Cairo")).toBe(true);
    expect(isTimeZone("Mars/Olympus")).toBe(false);
  });
});
