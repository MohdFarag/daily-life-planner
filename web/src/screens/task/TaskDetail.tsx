import { CalendarX, Ellipsis, Trash2 } from "lucide-react";
import { useState } from "react";
import { BackIcon, Gesture, IconButton, MenuItem, MissingScreen } from "../../components";
import { useStore } from "../../state";
import { SessionCard, TaskActions } from "./TaskSession";
import { SourceCard } from "./SourceCard";
import { TaskFields, TaskProperties } from "./TaskFields";

export function TaskDetail({ id, fresh }: { id: string; fresh?: boolean }) {
  const s = useStore();
  const [menuOpen, setMenuOpen] = useState(false);
  const task = s.data.tasks.find((x) => x.id === id);
  if (!task) return <MissingScreen icon={CalendarX} title={s.t("noTasks")} />;

  return (
    <>
      <div className="screen">
        <div className="content no-fab">
          <div className="top-bar">
            <IconButton icon={BackIcon} label={s.t("back")} onClick={s.back} flip />
            <span className="t-label c-secondary">{fresh ? s.t("newTask") : s.t("task")}</span>
            <IconButton icon={Ellipsis} label={s.t("moreOptions")} onClick={() => setMenuOpen((v) => !v)} />
          </div>
          {menuOpen && (
            <div className="card menu" role="menu">
              {task.session && (
                <MenuItem
                  icon={CalendarX}
                  label={s.t("removeSession")}
                  onClick={() => {
                    s.patchTask(task.id, { session: undefined });
                    setMenuOpen(false);
                  }}
                />
              )}
              <MenuItem
                icon={Trash2}
                label={s.t("deleteTask")}
                danger
                onClick={() => {
                  s.deleteTask(task.id);
                  s.back();
                }}
              />
            </div>
          )}
          <TaskFields task={task} />
          <TaskProperties task={task} />
          {task.source && <SourceCard source={task.source} />}
          <SessionCard session={task.session} />
        </div>
      </div>
      <TaskActions task={task} fresh={fresh} />
      <Gesture standalone />
    </>
  );
}
