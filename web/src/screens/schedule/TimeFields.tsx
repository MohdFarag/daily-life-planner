import { PRAYERS, useStore } from "../../state";
import type { ScheduleMode } from "./ScheduleSheet";

const DURATIONS = [10, 15, 20, 30, 45, 60, 90, 120];

interface Props {
  mode: ScheduleMode;
  start: string;
  onStart: (start: string) => void;
  prayer: string;
  onPrayer: (key: string) => void;
  duration: number;
  onDuration: (minutes: number) => void;
}

/** Start (a time, or which prayer to follow) and duration. */
export function TimeFields({ mode, start, onStart, prayer, onPrayer, duration, onDuration }: Props) {
  const s = useStore();
  // Keep a non-standard current duration selectable.
  const durations = [...new Set([...DURATIONS, duration])].sort((a, b) => a - b);
  return (
    <div className="time-fields">
      <label className="card time-field">
        <span className="t-micro c-secondary">{mode === "prayer" ? s.t("afterPrayer") : s.t("startTime")}</span>
        {mode === "prayer" ? (
          <select value={prayer} onChange={(e) => onPrayer(e.target.value)}>
            {PRAYERS.map((p) => (
              <option key={p.key} value={p.key}>{s.prayerName(p.key)}</option>
            ))}
          </select>
        ) : (
          <input type="time" value={start} step={300} onChange={(e) => e.target.value && onStart(e.target.value)} />
        )}
      </label>
      <label className="card time-field">
        <span className="t-micro c-secondary">{s.t("duration")}</span>
        <select value={duration} onChange={(e) => onDuration(Number(e.target.value))}>
          {durations.map((d) => (
            <option key={d} value={d}>{s.t("minutes", { n: d })}</option>
          ))}
        </select>
      </label>
    </div>
  );
}
