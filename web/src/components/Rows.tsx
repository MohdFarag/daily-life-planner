import { Moon } from "lucide-react";
import { type Task, useStore } from "../state";
import { Checkbox } from "./Controls";
import { ICON_STROKE } from "./icons";

export function TaskRow({ task, meta }: { task: Task; meta?: string }) {
  const s = useStore();
  const metadata =
    meta ??
    [task.session ? s.n(task.session.start) : null, `${s.n(task.estimate)} ${s.t("min")}`, s.listName(task.list)]
      .filter(Boolean)
      .join(" · ");
  return (
    <div className={`task-row${task.done ? " done" : ""}`}>
      <button
        className="complete-hit"
        role="checkbox"
        aria-checked={task.done}
        aria-label={task.done ? s.t("markOpen") : s.t("completeTask")}
        onClick={() => s.toggleDone(task.id)}
      >
        <Checkbox checked={task.done} />
      </button>
      <button className="task-info" onClick={() => s.go({ name: "task", id: task.id })}>
        <span className="t-body task-title truncate" style={{ maxWidth: "100%" }}>
          {s.taskTitle(task)}
        </span>
        <span className="t-micro c-secondary">{metadata}</span>
      </button>
    </div>
  );
}

export function PrayerRow({ name, time }: { name: string; time: string }) {
  const { t } = useStore();
  return (
    <div className="prayer-row">
      <Moon size={24} strokeWidth={ICON_STROKE} />
      <div style={{ display: "flex", flexDirection: "column" }}>
        <span className="t-label">{name}</span>
        <span className="t-micro c-secondary">
          {time} · {t("protectedTime")}
        </span>
      </div>
    </div>
  );
}
