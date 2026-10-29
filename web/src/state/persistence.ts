import { seed } from "./seed";
import type { Data } from "./types";

const KEY = "daily-life-planner/v1";

/** Stored data, falling back to the sample data when nothing usable is saved. */
export function loadData(): Data {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...seed(), ...JSON.parse(raw) };
  } catch {
    /* fall back to sample data */
  }
  return seed();
}

export function saveData(data: Data) {
  try {
    localStorage.setItem(KEY, JSON.stringify(data));
  } catch {
    /* storage full or unavailable */
  }
}
