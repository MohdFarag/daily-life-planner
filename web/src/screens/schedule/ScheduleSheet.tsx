import { CalendarDays, X } from "lucide-react";
import { useState } from "react";
import { Button, ICON_STROKE, IconButton, Sheet } from "../../components";
import { fromMin, nowMin, todayISO, toMin } from "../../lib/time";
import { conflictsWith, inWorkBlock, prayerByKey, prayerWindow, suggestedStart, type Task, timeRange, useStore } from "../../state";
import { Availability, SessionPreview } from "./SessionPreview";
import { TimeFields } from "./TimeFields";

export type ScheduleMode = "fixed" | "prayer";

export function ScheduleSheet({ taskId }: { taskId: string }) {
  const s = useStore();
  const task = s.data.tasks.find((x) => x.id === taskId);
  if (!task) return null;
  return <ScheduleForm task={task} />;
}

/** Plans one session for a task, at a fixed time or after a prayer, warning about overlaps. */
function ScheduleForm({ task }: { task: Task }) {
  const s = useStore();
  const today = todayISO();
  const current = task.session;
  const [mode, setMode] = useState<ScheduleMode>(current?.afterPrayer ? "prayer" : "fixed");
  const [date, setDate] = useState(current?.date ?? today);
  const [start, setStart] = useState(current?.start ?? suggestedStart(nowMin()));
  const [duration, setDuration] = useState(current?.duration ?? task.estimate ?? 30);
  const [prayer, setPrayer] = useState(current?.afterPrayer ?? "dhuhr");
  const close = () => s.setScheduleTaskId(null);

  const startMin = mode === "prayer" ? prayerWindow(prayerByKey(prayer)).end : toMin(start);
  const endMin = startMin + duration;
  const conflicts = conflictsWith(s.busyOn(date), startMin, endMin, task.id, s.data.protectPrayers);
  const workBlock = inWorkBlock(startMin, endMin);
  const dateLabel =
    date === today
      ? `${s.t("today")}, ${s.date(date, { day: "numeric", month: "long" })}`
      : s.date(date, { weekday: "long", day: "numeric", month: "long" });

  const apply = () => {
    s.patchTask(task.id, {
      session: { date, start: fromMin(startMin), duration, afterPrayer: mode === "prayer" ? prayer : undefined },
    });
    close();
    s.showToast(`${s.dayLabel(date)} · ${s.n(timeRange(startMin, endMin))}`);
  };

  return (
    <Sheet onClose={close} label={s.t("planSession")}>
      <div className="title-row">
        <h2 className="t-h2" style={{ margin: 0 }}>
          {s.t("planSession")}
        </h2>
        <IconButton icon={X} label={s.t("closeSchedule")} onClick={close} />
      </div>
      <p className="t-small c-secondary">
        {s.taskTitle(task)} · {s.t("minutes", { n: task.estimate })}
      </p>

      <div className="segmented full" role="tablist">
        {(["fixed", "prayer"] as const).map((m) => (
          <button key={m} role="tab" aria-selected={mode === m} className={mode === m ? "active" : ""} onClick={() => setMode(m)}>
            {m === "fixed" ? s.t("fixedTime") : s.t("afterPrayer")}
          </button>
        ))}
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

      <TimeFields
        mode={mode}
        start={start}
        onStart={setStart}
        prayer={prayer}
        onPrayer={setPrayer}
        duration={duration}
        onDuration={setDuration}
      />
      <SessionPreview
        range={timeRange(startMin, endMin)}
        date={date}
        context={mode === "prayer" ? s.t("after", { name: s.prayerName(prayer) }) : workBlock ? s.t("workBlock") : null}
      />
      <Availability conflicts={conflicts} inWorkBlock={workBlock} />

      <Button onClick={apply}>{s.t("applySchedule")}</Button>
      <p className="t-micro c-secondary">{s.t("multiSession")}</p>
    </Sheet>
  );
}
