import { ChevronDown, Ellipsis, Plus, Timer } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Gesture, IconButton, MenuItem, MissingScreen } from "../../components";
import { useTicker } from "../../hooks/useTicker";
import { type Focus as FocusState, type Task, useStore } from "../../state";
import { FocusActions } from "./FocusActions";
import { FocusRing } from "./FocusRing";

const EXTEND_SEC = 5 * 60;

export function Focus() {
  const s = useStore();
  const focus = s.data.focus;
  const task = focus && s.data.tasks.find((x) => x.id === focus.taskId);
  if (!focus || !task) return <MissingScreen icon={Timer} title={s.t("noFocus")} />;
  return <FocusSession focus={focus} task={task} />;
}

/** A running or paused session; when time is up it pauses once and buzzes. */
function FocusSession({ focus, task }: { focus: FocusState; task: Task }) {
  const s = useStore();
  const running = !!focus.runningSince;
  useTicker(250, running);
  const [menuOpen, setMenuOpen] = useState(false);
  const remaining = Math.max(0, s.remaining(focus));
  const finished = remaining <= 0;
  const notified = useRef(false);
  const { pauseFocus } = s;

  useEffect(() => {
    if (finished && running && !notified.current) {
      notified.current = true;
      pauseFocus();
      navigator.vibrate?.(200);
    }
  }, [finished, running, pauseFocus]);

  const extend = () => {
    s.extendFocus(EXTEND_SEC);
    notified.current = false;
  };

  return (
    <>
      <div className="screen">
        <div className="content gap-20 no-fab">
          <div className="focus-controls">
            <IconButton icon={ChevronDown} label={s.t("leaveFocus")} onClick={s.back} />
            <span className="t-micro c-secondary">{running ? s.t("focusSession") : s.t("sessionPaused")}</span>
            <IconButton icon={Ellipsis} label={s.t("moreOptions")} onClick={() => setMenuOpen((v) => !v)} />
          </div>
          {menuOpen && (
            <div className="card menu" role="menu">
              <MenuItem
                icon={Plus}
                label={s.t("addFive")}
                onClick={() => {
                  extend();
                  setMenuOpen(false);
                }}
              />
            </div>
          )}

          <div className="stack-8" style={{ gap: 8 }}>
            <button className="t-h2" style={{ textAlign: "start" }} onClick={() => s.go({ name: "task", id: task.id })}>
              {s.taskTitle(task)}
            </button>
            {s.taskNotes(task) && <p className="t-small c-secondary">{s.taskNotes(task)}</p>}
          </div>

          <FocusRing focus={focus} remaining={remaining} />

          <FocusActions task={task} running={running} finished={finished} onExtend={extend} />
          <p className="t-micro c-secondary">{s.t("endingKeepsOpen")}</p>
        </div>
      </div>
      <Gesture standalone />
    </>
  );
}
