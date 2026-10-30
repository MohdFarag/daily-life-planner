// Illustrative prayer times, each protected for 30 minutes.
export interface Prayer {
  key: string;
  /** Adhan time, "HH:MM". */
  start: string;
}

export const PRAYERS: Prayer[] = [
  { key: "fajr", start: "05:12" },
  { key: "dhuhr", start: "12:10" },
  { key: "asr", start: "15:21" },
  { key: "maghrib", start: "17:48" },
  { key: "isha", start: "19:05" },
];
export const PRAYER_MIN = 30;
export const WORK_BLOCK = { start: "09:00", end: "12:00" };
export const WORKDAY_END = "17:00";
