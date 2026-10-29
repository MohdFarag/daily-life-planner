import { ChevronDown, Ellipsis, Plus, Timer } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { BackIcon, Button, Empty, Gesture, IconButton, MenuItem } from "../components";
import { useTicker } from "../hooks/useTicker";
import { formatClock, fromMin, nowMin } from "../lib/time";
import { useStore } from "../state";

const SIZE = 272;
const R = 130;
const CIRC = 2 * Math.PI * R;

export function Focus() {
  const s = useStore();
  const focus = s.data.focus;
  const running = !!focus?.runningSince;
  useTicker(250, running);
  const [menuOpen, setMenuOpen] = useState(false);
  const task = focus && s.data.tasks.find((x) => x.id === focus.taskId);
  const remaining = focus ? Math.max(0, s.remaining(focus)) : 0;
  const finished = !!focus && remaining <= 0;
  const notified = useRef(false);

  useEffect(() => {
    if (finished && running && !notified.current) {
      notified.current = true;
      s.pauseFocus();
      navigator.vibrate?.(200);
    }
  }, [finished, running, s]);

  if (!focus || !task) {
    return (
      <div className="screen">
        <div className="content no-fab">
          <div className="focus-controls">
            <IconButton icon={BackIcon} label={s.t("back")} onClick={s.back} flip />
          </div>
          <Empty icon={Timer} title={s.t("noFocus")} />
        </div>
      </div>
    );
  }

  const progress = 1 - remaining / focus.totalSec;
  const endsAt = fromMin(nowMin() + Math.ceil(remaining / 60));

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
              <MenuItem icon={Plus} label={s.t("addFive")} onClick={() => { s.extendFocus(300); notified.current = false; setMenuOpen(false); }} />
            </div>
          )}

          <div className="stack-8" style={{ gap: 8 }}>
            <button className="t-h2" style={{ textAlign: "start" }} onClick={() => s.go({ name: "task", id: task.id })}>
              {s.taskTitle(task)}
            </button>
            {s.taskNotes(task) && <p className="t-small c-secondary">{s.taskNotes(task)}</p>}
          </div>

          <div className="timer">
            <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} aria-hidden>
              <circle cx={SIZE / 2} cy={SIZE / 2} r={R} fill="none" stroke="var(--color-subtle)" strokeWidth={10} />
              <circle
                className="ring-progress"
                cx={SIZE / 2}
                cy={SIZE / 2}
                r={R}
                fill="none"
                stroke={running ? "var(--color-primary)" : "var(--color-secondary)"}
                strokeWidth={10}
                strokeLinecap="round"
                strokeDasharray={CIRC}
                strokeDashoffset={CIRC * (1 - progress)}
                transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
              />
            </svg>
            <div className="timer-text" role="timer" aria-live="off">
              <span className="timer-digits">{s.n(formatClock(remaining))}</span>
              <span className="t-small c-secondary">
                {finished ? s.t("timeUp") : running ? s.t("remainingOf", { n: Math.round(focus.totalSec / 60) }) : s.t("pausedNote")}
              </span>
              {running && <span className="t-micro c-secondary">{s.t("endsAt", { time: s.n(endsAt) })}</span>}
            </div>
          </div>

          <div className="stack-8">
            {finished ? (
              <Button onClick={() => { s.extendFocus(300); notified.current = false; s.resumeFocus(); }}>{s.t("addFive")}</Button>
            ) : (
              <Button onClick={running ? s.pauseFocus : s.resumeFocus}>{running ? s.t("pauseFocus") : s.t("resumeFocus")}</Button>
            )}
            <Button variant="secondary" onClick={() => { s.endFocus(); s.back(); }}>{s.t("endSession")}</Button>
            <Button
              variant="secondary"
              onClick={() => {
                s.toggleDone(task.id);
                s.tab("today");
              }}
            >
              {s.t("completeTask")}
            </Button>
          </div>
          <p className="t-micro c-secondary">{s.t("endingKeepsOpen")}</p>
        </div>
      </div>
      <Gesture standalone />
    </>
  );
}
