import { AlertTriangle, CalendarDays, Check, X } from "lucide-react";
import { useState } from "react";
import { Button, ICON_STROKE, IconButton, Sheet } from "../components";
import { locale } from "../i18n";
import { PRAYERS, PRAYER_MIN, WORK_BLOCK, useStore } from "../store";
import { fromMin, nowMin, overlaps, parseISO, toMin, todayISO } from "../time";

const DURATIONS = [10, 15, 20, 30, 45, 60, 90, 120];

export function ScheduleSheet({ taskId }: { taskId: string }) {
  const s = useStore();
  const task = s.data.tasks.find((x) => x.id === taskId);
  const today = todayISO();
  const nextQuarter = fromMin(Math.min(Math.ceil((nowMin() + 5) / 15) * 15, 22 * 60));
  const [mode, setMode] = useState<"fixed" | "prayer">(task?.session?.afterPrayer ? "prayer" : "fixed");
  const [date, setDate] = useState(task?.session?.date ?? today);
  const [start, setStart] = useState(task?.session?.start ?? (toMin(nextQuarter) < toMin("09:00") ? "09:00" : nextQuarter));
  const [duration, setDuration] = useState(task?.session?.duration ?? task?.estimate ?? 30);
  const [prayer, setPrayer] = useState(task?.session?.afterPrayer ?? "dhuhr");
  const close = () => s.setScheduleTaskId(null);
  if (!task) return null;

  const startMin = mode === "prayer" ? toMin(PRAYERS.find((p) => p.key === prayer)!.start) + PRAYER_MIN : toMin(start);
  const endMin = startMin + duration;
  const conflicts = s
    .busyOn(date)
    .filter((b) => !(b.kind === "task" && b.id === task.id))
    .filter((b) => b.kind !== "prayer" || s.data.protectPrayers)
    .filter((b) => overlaps(startMin, endMin, b.start, b.end));
  const inWorkBlock = startMin >= toMin(WORK_BLOCK.start) && endMin <= toMin(WORK_BLOCK.end);
  const dateLabel =
    date === today
      ? `${s.t("today")}, ${new Intl.DateTimeFormat(locale(s.lang), { day: "numeric", month: "long" }).format(parseISO(date))}`
      : new Intl.DateTimeFormat(locale(s.lang), { weekday: "long", day: "numeric", month: "long" }).format(parseISO(date));
  const dayShort = date === today ? s.t("today") : new Intl.DateTimeFormat(locale(s.lang), { weekday: "short", day: "numeric", month: "short" }).format(parseISO(date));
  const context = mode === "prayer" ? s.t("after", { name: s.prayerName(prayer) }) : inWorkBlock ? s.t("workBlock") : null;

  const apply = () => {
    s.patchTask(task.id, {
      session: { date, start: fromMin(startMin), duration, afterPrayer: mode === "prayer" ? prayer : undefined },
    });
    close();
    s.showToast(`${dayShort} · ${s.n(`${fromMin(startMin)}–${fromMin(endMin)}`)}`);
  };

  return (
    <Sheet onClose={close} label={s.t("planSession")}>
      <div className="title-row">
        <h2 className="t-h2" style={{ margin: 0 }}>{s.t("planSession")}</h2>
        <IconButton icon={X} label={s.t("closeSchedule")} onClick={close} />
      </div>
      <p className="t-small c-secondary">
        {s.taskTitle(task)} · {s.t("minutes", { n: task.estimate })}
      </p>

      <div className="segmented full" role="tablist">
        <button role="tab" aria-selected={mode === "fixed"} className={mode === "fixed" ? "active" : ""} onClick={() => setMode("fixed")}>
          {s.t("fixedTime")}
        </button>
        <button role="tab" aria-selected={mode === "prayer"} className={mode === "prayer" ? "active" : ""} onClick={() => setMode("prayer")}>
          {s.t("afterPrayer")}
        </button>
      </div>

      <label className="card field-row">
        <CalendarDays size={20} strokeWidth={ICON_STROKE} />
        <span className="t-label">{dateLabel}</span>
        <input
          type="date"
          className="native-overlay"
          value={date}
          min={today}
          onChange={(e) => e.target.value && setDate(e.target.value)}
          aria-label={s.t("day")}
        />
      </label>

      <div className="time-fields">
        <label className="card time-field">
          <span className="t-micro c-secondary">{mode === "prayer" ? s.t("afterPrayer") : s.t("startTime")}</span>
          {mode === "prayer" ? (
            <select value={prayer} onChange={(e) => setPrayer(e.target.value)}>
              {PRAYERS.map((p) => (
                <option key={p.key} value={p.key}>{s.prayerName(p.key)}</option>
              ))}
            </select>
          ) : (
            <input type="time" value={start} step={300} onChange={(e) => e.target.value && setStart(e.target.value)} />
          )}
        </label>
        <label className="card time-field">
          <span className="t-micro c-secondary">{s.t("duration")}</span>
          <select value={duration} onChange={(e) => setDuration(Number(e.target.value))}>
            {[...new Set([...DURATIONS, duration])].sort((a, b) => a - b).map((d) => (
              <option key={d} value={d}>{s.t("minutes", { n: d })}</option>
            ))}
          </select>
        </label>
      </div>

      <div className="preview-box">
        <p className="t-h2">{s.n(`${fromMin(startMin)}–${fromMin(endMin)}`)}</p>
        <p className="t-micro">{[dayShort, context, s.t("calendarReminder")].filter(Boolean).join(" · ")}</p>
      </div>

      <div className={`availability ${conflicts.length ? "warn" : "ok"}`} role="status">
        {conflicts.length ? <AlertTriangle size={20} strokeWidth={ICON_STROKE} /> : <Check size={20} strokeWidth={2} />}
        <span className="t-label">
          {conflicts.length
            ? s.t("conflictWith", { what: conflicts.map((c) => c.label).join(", ") })
            : inWorkBlock ? s.t("fitsWorkBlock") : s.t("fitsDay")}
        </span>
      </div>

      <Button onClick={apply}>{s.t("applySchedule")}</Button>
      <p className="t-micro c-secondary">{s.t("multiSession")}</p>
    </Sheet>
  );
}
