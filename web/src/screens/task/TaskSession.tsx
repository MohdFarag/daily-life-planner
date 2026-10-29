import { Button } from "../../components";
import { toMin } from "../../lib/time";
import { inWorkBlock, type Session, type Task, useStore } from "../../state";

export function SessionCard({ session }: { session?: Session }) {
  const s = useStore();
  if (!session) {
    return (
      <div className="card session-card">
        <span className="t-label">{s.t("readyWhenYouAre")}</span>
        <span className="t-small c-secondary">{s.t("noTimeSet")}</span>
      </div>
    );
  }
  const start = toMin(session.start);
  const context = inWorkBlock(start, start + session.duration)
    ? ` · ${s.t("workBlock")}`
    : session.afterPrayer
      ? ` · ${s.t("after", { name: s.prayerName(session.afterPrayer) })}`
      : "";
  return (
    <div className="card session-card">
      <span className="t-label">{s.t("nextSession")}</span>
      <span className="t-small c-secondary">
        {s.dayLabel(session.date)}, {s.n(s.sessionLabel(session))}
        {context}
      </span>
    </div>
  );
}

/** A task fresh from triage offers planning or continuing; otherwise focus and (re)scheduling. */
export function TaskActions({ task, fresh }: { task: Task; fresh?: boolean }) {
  const s = useStore();
  const focused = s.data.focus?.taskId === task.id;
  if (fresh) {
    return (
      <div className="actions-bar">
        <Button onClick={() => s.setScheduleTaskId(task.id)}>{s.t("planSession")}</Button>
        <Button variant="secondary" onClick={() => s.replace({ name: "process" })}>
          {s.t("continueProcessing")}
        </Button>
      </div>
    );
  }
  return (
    <div className="actions-bar">
      {!task.done && (
        <Button
          onClick={() => {
            if (!focused) s.startFocus(task.id);
            s.go({ name: "focus" });
          }}
        >
          {focused ? s.t("resumeFocus") : s.t("startFocus")}
        </Button>
      )}
      <Button variant="secondary" onClick={() => s.setScheduleTaskId(task.id)}>
        {task.session ? s.t("reschedule") : s.t("schedule")}
      </Button>
    </div>
  );
}
