import { CalendarX, ChevronRight, Ellipsis, Star, Trash2 } from "lucide-react";
import { type TextareaHTMLAttributes, useLayoutEffect, useRef, useState } from "react";
import { BackIcon, Button, Empty, Gesture, ICON_STROKE, IconButton, KIND_ICON, MenuItem } from "../components";
import { useBlobUrl } from "../hooks/useBlobUrl";
import { useRelativeTime } from "../hooks/useItemLabels";
import { locale } from "../i18n";
import { parseISO, todayISO, toMin } from "../lib/time";
import { useStore, WORK_BLOCK } from "../state";

const ESTIMATES = [10, 15, 20, 30, 45, 60, 90, 120];

export function TaskDetail({ id, fresh }: { id: string; fresh?: boolean }) {
  const s = useStore();
  const rel = useRelativeTime();
  const [menuOpen, setMenuOpen] = useState(false);
  const [sourceOpen, setSourceOpen] = useState(false);
  const task = s.data.tasks.find((x) => x.id === id);
  const blobUrl = useBlobUrl(sourceOpen ? task?.source?.blobId : undefined);

  if (!task) {
    return (
      <div className="screen">
        <div className="content no-fab">
          <div className="top-bar">
            <IconButton icon={BackIcon} label={s.t("back")} onClick={s.back} flip />
          </div>
          <Empty icon={CalendarX} title={s.t("noTasks")} />
        </div>
      </div>
    );
  }

  const session = task.session;
  const inWorkBlock =
    session && toMin(session.start) >= toMin(WORK_BLOCK.start) && toMin(session.start) + session.duration <= toMin(WORK_BLOCK.end);
  const dayLabel = (iso: string) =>
    iso === todayISO()
      ? s.t("today")
      : new Intl.DateTimeFormat(locale(s.lang), { weekday: "short", day: "numeric", month: "short" }).format(parseISO(iso));
  const SourceIcon = task.source ? KIND_ICON[task.source.kind] : null;
  const isFocused = s.data.focus?.taskId === task.id;

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
              {session && (
                <MenuItem icon={CalendarX} label={s.t("removeSession")} onClick={() => { s.patchTask(task.id, { session: undefined }); setMenuOpen(false); }} />
              )}
              <MenuItem icon={Trash2} label={s.t("deleteTask")} danger onClick={() => { s.deleteTask(task.id); s.back(); }} />
            </div>
          )}

          <div className="stack-8" style={{ gap: 0 }}>
            <AutoGrow
              className="title-input"
              value={s.taskTitle(task)}
              aria-label={s.t("task")}
              onChange={(e) => s.patchTask(task.id, s.lang === "ar" && task.titleAr ? { titleAr: e.target.value } : { title: e.target.value })}
            />
            <AutoGrow
              className="notes-input"
              value={s.taskNotes(task)}
              placeholder={s.t("taskNotes")}
              aria-label={s.t("taskNotes")}
              onChange={(e) => s.patchTask(task.id, s.lang === "ar" && task.notesAr ? { notesAr: e.target.value } : { notes: e.target.value })}
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

          <div className="card details">
            <button className="detail-row" onClick={() => s.patchTask(task.id, { list: task.list === "Work" ? "Personal" : "Work" })}>
              <span className="t-small">{s.t("list")}</span>
              <span className="value">{s.listName(task.list)}</span>
            </button>
            <button
              className="detail-row"
              onClick={() => s.patchTask(task.id, { estimate: ESTIMATES[(ESTIMATES.indexOf(task.estimate) + 1) % ESTIMATES.length] })}
            >
              <span className="t-small">{s.t("estimate")}</span>
              <span className="value">{s.t("minutes", { n: task.estimate })}</span>
            </button>
            <label className="detail-row">
              <span className="t-small">{s.t("deadline")}</span>
              <span className="value" style={{ position: "relative" }}>
                {task.deadline ? dayLabel(task.deadline) : s.t("notSet")}
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

          {task.source && SourceIcon && (
            <div className="card">
              <button className="link-card" aria-expanded={sourceOpen} onClick={() => setSourceOpen((v) => !v)}>
                <SourceIcon size={24} strokeWidth={ICON_STROKE} />
                <span style={{ flex: 1, display: "flex", flexDirection: "column" }}>
                  <span className="t-label" style={{ color: "var(--color-text)" }}>{s.t("originalNote")}</span>
                  <span className="t-micro c-secondary">{s.t("openSource")}</span>
                </span>
                <ChevronRight size={18} strokeWidth={ICON_STROKE} className="flip-rtl" style={{ transform: sourceOpen ? "rotate(90deg)" : undefined }} />
              </button>
              {sourceOpen && (
                <div style={{ padding: "0 16px 16px" }} className="stack-8">
                  <p className="t-body">{task.source.title}</p>
                  {task.source.body && <p className="t-small c-secondary" style={{ whiteSpace: "pre-line" }}>{task.source.body}</p>}
                  {task.source.url && <a className="t-label c-primary" href={task.source.url} target="_blank" rel="noreferrer">{task.source.url}</a>}
                  {blobUrl && task.source.kind === "voice" && <audio controls src={blobUrl} style={{ width: "100%" }} />}
                  {blobUrl && task.source.kind === "photo" && <img src={blobUrl} alt="" style={{ width: "100%", borderRadius: 12 }} />}
                  {blobUrl && task.source.kind === "file" && <a className="t-label c-primary" href={blobUrl} download={task.source.fileName}>{task.source.fileName}</a>}
                  <p className="t-micro c-secondary">{rel(task.source.createdAt)}</p>
                </div>
              )}
            </div>
          )}

          <div className="card session-card">
            {session ? (
              <>
                <span className="t-label">{s.t("nextSession")}</span>
                <span className="t-small c-secondary">
                  {dayLabel(session.date)}, {s.n(s.sessionLabel(session))}
                  {inWorkBlock ? ` · ${s.t("workBlock")}` : session.afterPrayer ? ` · ${s.t("after", { name: s.prayerName(session.afterPrayer) })}` : ""}
                </span>
              </>
            ) : (
              <>
                <span className="t-label">{s.t("readyWhenYouAre")}</span>
                <span className="t-small c-secondary">{s.t("noTimeSet")}</span>
              </>
            )}
          </div>
        </div>
      </div>

      <div className="actions-bar">
        {fresh ? (
          <>
            <Button onClick={() => s.setScheduleTaskId(task.id)}>{s.t("planSession")}</Button>
            <Button variant="secondary" onClick={() => s.replace({ name: "process" })}>{s.t("continueProcessing")}</Button>
          </>
        ) : (
          <>
            {!task.done && (
              <Button
                onClick={() => {
                  if (!isFocused) s.startFocus(task.id);
                  s.go({ name: "focus" });
                }}
              >
                {isFocused ? s.t("resumeFocus") : s.t("startFocus")}
              </Button>
            )}
            <Button variant="secondary" onClick={() => s.setScheduleTaskId(task.id)}>
              {session ? s.t("reschedule") : s.t("schedule")}
            </Button>
          </>
        )}
      </div>
      <Gesture standalone />
    </>
  );
}

function AutoGrow(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const ref = useRef<HTMLTextAreaElement>(null);
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${el.scrollHeight}px`;
  }, [props.value]);
  return <textarea ref={ref} rows={1} {...props} />;
}
