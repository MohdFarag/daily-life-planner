import { CalendarPlus } from "lucide-react";
import { ICON_STROKE } from "../../components";
import { useStore } from "../../state";

/** Open tasks with no session yet, each with a shortcut to plan it. */
export function UnplannedTasks() {
  const s = useStore();
  const unplanned = s.data.tasks.filter((x) => !x.done && !x.session);
  if (!unplanned.length) return null;
  return (
    <section className="section">
      <div className="section-heading">
        <span className="t-h3">{s.t("notPlanned")}</span>
      </div>
      {unplanned.map((x) => (
        <div key={x.id} className="task-row" style={{ minHeight: 64 }}>
          <button className="task-info" onClick={() => s.go({ name: "task", id: x.id })}>
            <span className="t-body truncate">{s.taskTitle(x)}</span>
            <span className="t-micro c-secondary">
              {s.n(x.estimate)} {s.t("min")} · {s.listName(x.list)}
            </span>
          </button>
          <button
            className="link-btn"
            style={{ display: "inline-flex", alignItems: "center", gap: 6, paddingInline: 8 }}
            onClick={() => s.setScheduleTaskId(x.id)}
          >
            <CalendarPlus size={18} strokeWidth={ICON_STROKE} />
            {s.t("planIt")}
          </button>
        </div>
      ))}
    </section>
  );
}
