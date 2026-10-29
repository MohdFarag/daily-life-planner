import { ListChecks } from "lucide-react";
import { useState } from "react";
import { Empty, SubScreen, TaskRow } from "../../components";
import { useStore } from "../../state";

export function TasksScreen() {
  const s = useStore();
  const [filter, setFilter] = useState<"all" | "Work" | "Personal">("all");
  const tasks = s.data.tasks.filter((x) => filter === "all" || x.list === filter);
  const open = tasks.filter((x) => !x.done);
  const done = tasks.filter((x) => x.done);
  return (
    <SubScreen title={s.t("allTasks")}>
      <div className="filters">
        {(["all", "Work", "Personal"] as const).map((f) => (
          <button key={f} className={`filter${filter === f ? " active" : ""}`} onClick={() => setFilter(f)}>
            {f === "all" ? s.t("all") : s.listName(f)}
          </button>
        ))}
      </div>
      <span className="t-micro c-secondary">
        {s.t("open")} · {s.n(open.length)}
      </span>
      {open.map((x) => (
        <TaskRow key={x.id} task={x} />
      ))}
      {!tasks.length && <Empty icon={ListChecks} title={s.t("noTasks")} />}
      {done.length > 0 && (
        <span className="t-micro c-secondary">
          {s.t("completed")} · {s.n(done.length)}
        </span>
      )}
      {done.map((x) => (
        <TaskRow key={x.id} task={x} />
      ))}
    </SubScreen>
  );
}
