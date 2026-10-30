import { CalendarPlus } from "lucide-react";
import { Button, ICON_STROKE } from "../../components";
import { type Task, useStore } from "../../state";

export function ProgressCard({ done, total }: { done: number; total: number }) {
  const s = useStore();
  return (
    <div className="card progress-card">
      <span className="t-label c-primary">{s.t("tasksComplete", { done, total })}</span>
      <div className="progress-track" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={done}>
        <div className="progress-fill" style={{ width: `${total ? (done / total) * 100 : 0}%` }} />
      </div>
    </div>
  );
}

/** The next scheduled task, with a button to start (or resume) focusing on it. */
export function UpNextCard({ task }: { task: Task }) {
  const s = useStore();
  const focused = s.data.focus?.taskId === task.id;
  return (
    <div className="up-next">
      <div className="up-next-row">
        <span className="t-micro">{s.t("upNext")}</span>
        {task.session && <span className="badge badge-highlight">{s.n(s.sessionLabel(task.session))}</span>}
      </div>
      <button
        style={{ textAlign: "start", display: "flex", flexDirection: "column", gap: 12 }}
        onClick={() => s.go({ name: "task", id: task.id })}
      >
        <span className="t-h2">{s.taskTitle(task)}</span>
        <span className="t-small">{[s.listName(task.list), s.taskNotes(task)].filter(Boolean).join(" · ")}</span>
      </button>
      <Button
        variant="light"
        onClick={() => {
          if (!focused) s.startFocus(task.id);
          s.go({ name: "focus" });
        }}
      >
        {focused ? s.t("resumeFocus") : s.t("startSession")}
      </Button>
    </div>
  );
}

export function NothingNextCard() {
  const s = useStore();
  return (
    <div className="empty-card">
      <span className="t-h3">{s.t("nothingNext")}</span>
      <span className="t-small c-secondary">{s.t("nothingNextHint")}</span>
      <Button variant="secondary" onClick={() => s.tab("plan")}>
        <CalendarPlus size={20} strokeWidth={ICON_STROKE} /> {s.t("openPlan")}
      </Button>
    </div>
  );
}
