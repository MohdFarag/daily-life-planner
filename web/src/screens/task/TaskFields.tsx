import { Star } from "lucide-react";
import { AutoGrow } from "../../components";
import { type Task, useStore } from "../../state";

const ESTIMATES = [10, 15, 20, 30, 45, 60, 90, 120];

const nextEstimate = (current: number) => ESTIMATES[(ESTIMATES.indexOf(current) + 1) % ESTIMATES.length];

/** Title, notes and the importance toggle. Edits go to the Arabic text when that is what is shown. */
export function TaskFields({ task }: { task: Task }) {
  const s = useStore();
  const arabic = s.lang === "ar";
  return (
    <>
      <div className="stack-8" style={{ gap: 0 }}>
        <AutoGrow
          className="title-input"
          value={s.taskTitle(task)}
          aria-label={s.t("task")}
          onChange={(e) => s.patchTask(task.id, arabic && task.titleAr ? { titleAr: e.target.value } : { title: e.target.value })}
        />
        <AutoGrow
          className="notes-input"
          value={s.taskNotes(task)}
          placeholder={s.t("taskNotes")}
          aria-label={s.t("taskNotes")}
          onChange={(e) => s.patchTask(task.id, arabic && task.notesAr ? { notesAr: e.target.value } : { notes: e.target.value })}
        />
      </div>
      <button
        className={`badge ${task.important ? "badge-warning" : "badge-outline"}`}
        aria-pressed={task.important}
        onClick={() => s.patchTask(task.id, { important: !task.important })}
      >
        {task.important && <Star size={14} strokeWidth={2} fill="currentColor" />}
        {task.important ? s.t("important") : s.t("markImportant")}
      </button>
      {task.done && <span className="badge badge-tint">{s.t("completed")}</span>}
    </>
  );
}

/** List, estimate and deadline; tapping list or estimate cycles through the choices. */
export function TaskProperties({ task }: { task: Task }) {
  const s = useStore();
  return (
    <div className="card details">
      <button className="detail-row" onClick={() => s.patchTask(task.id, { list: task.list === "Work" ? "Personal" : "Work" })}>
        <span className="t-small">{s.t("list")}</span>
        <span className="value">{s.listName(task.list)}</span>
      </button>
      <button className="detail-row" onClick={() => s.patchTask(task.id, { estimate: nextEstimate(task.estimate) })}>
        <span className="t-small">{s.t("estimate")}</span>
        <span className="value">{s.t("minutes", { n: task.estimate })}</span>
      </button>
      <label className="detail-row">
        <span className="t-small">{s.t("deadline")}</span>
        <span className="value" style={{ position: "relative" }}>
          {task.deadline ? s.dayLabel(task.deadline) : s.t("notSet")}
          <input
            type="date"
            className="native-overlay"
            value={task.deadline ?? ""}
            onChange={(e) => s.patchTask(task.id, { deadline: e.target.value || undefined })}
            aria-label={s.t("deadline")}
          />
        </span>
      </label>
    </div>
  );
}
