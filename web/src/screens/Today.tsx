import { CalendarPlus, Clock } from "lucide-react";
import { Button, Fab, ICON_STROKE, PageHeading, PrayerRow, TaskRow } from "../components";
import { useTicker } from "../hooks/useTicker";
import { locale } from "../i18n";
import { fromMin, nowMin, todayISO, toMin } from "../lib/time";
import { PRAYER_MIN, PRAYERS, useStore } from "../state";

export function Today() {
  useTicker(60_000);
  const s = useStore();
  const today = todayISO();
  const date = new Date();
  const dateLabel =
    s.lang === "ar"
      ? new Intl.DateTimeFormat(locale("ar"), { weekday: "long", day: "numeric", month: "long" }).format(date)
      : `${date.toLocaleDateString("en-GB", { weekday: "long" })}, ${date.toLocaleDateString("en-GB", { day: "numeric", month: "long" })}`;

  const done = s.todaysTasks.filter((x) => x.done).length;
  const total = s.todaysTasks.length;

  const scheduled = s.data.tasks
    .filter((x) => x.session?.date === today)
    .sort((a, b) => toMin(a.session!.start) - toMin(b.session!.start));
  const upNext = scheduled.find((x) => !x.done);
  const rest = scheduled.filter((x) => x !== upNext);

  // Show prayers that fall between now and the end of the last planned item (or at least the next one).
  const lastEnd = Math.max(0, ...scheduled.map((x) => toMin(x.session!.start) + x.session!.duration));
  const now = nowMin();
  let prayers = PRAYERS.filter((p) => toMin(p.start) + PRAYER_MIN > now && toMin(p.start) < lastEnd);
  if (!prayers.length) prayers = PRAYERS.filter((p) => toMin(p.start) + PRAYER_MIN > now).slice(0, 1);

  type Row = { at: number; el: JSX.Element };
  const rows: Row[] = [
    ...prayers.map((p) => ({
      at: toMin(p.start),
      el: (
        <PrayerRow
          key={p.key}
          name={s.t("prayer", { name: s.prayerName(p.key) })}
          time={s.n(`${p.start}–${fromMin(toMin(p.start) + PRAYER_MIN)}`)}
        />
      ),
    })),
    ...rest.map((x) => ({ at: toMin(x.session!.start), el: <TaskRow key={x.id} task={x} /> })),
  ].sort((a, b) => a.at - b.at);

  const free = s.freeMinutesToday();

  return (
    <div className="screen">
      <div className="content">
        <PageHeading title={s.t("today")} subtitle={dateLabel} />

        <div className="card progress-card">
          <span className="t-label c-primary">{s.t("tasksComplete", { done, total })}</span>
          <div
            className="progress-track"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={total}
            aria-valuenow={done}
          >
            <div className="progress-fill" style={{ width: `${total ? (done / total) * 100 : 0}%` }} />
          </div>
        </div>

        {upNext ? (
          <div className="up-next">
            <div className="up-next-row">
              <span className="t-micro">{s.t("upNext")}</span>
              <span className="badge badge-highlight">{s.n(s.sessionLabel(upNext.session!))}</span>
            </div>
            <button style={{ textAlign: "start", display: "flex", flexDirection: "column", gap: 12 }} onClick={() => s.go({ name: "task", id: upNext.id })}>
              <span className="t-h2">{s.taskTitle(upNext)}</span>
              <span className="t-small">
                {[s.listName(upNext.list), s.taskNotes(upNext)].filter(Boolean).join(" · ")}
              </span>
            </button>
            <Button
              variant="light"
              onClick={() => {
                if (s.data.focus?.taskId !== upNext.id) s.startFocus(upNext.id);
                s.go({ name: "focus" });
              }}
            >
              {s.data.focus?.taskId === upNext.id ? s.t("resumeFocus") : s.t("startSession")}
            </Button>
          </div>
        ) : (
          <div className="empty-card">
            <span className="t-h3">{s.t("nothingNext")}</span>
            <span className="t-small c-secondary">{s.t("nothingNextHint")}</span>
            <Button variant="secondary" onClick={() => s.tab("plan")}>
              <CalendarPlus size={20} strokeWidth={ICON_STROKE} /> {s.t("openPlan")}
            </Button>
          </div>
        )}

        <section className="section">
          <button className="section-heading" onClick={() => s.tab("plan")}>
            <span className="t-h3">{s.t("yourDay")}</span>
            <span className="t-label c-primary">{s.t("viewPlan")}</span>
          </button>
          {rows.map((r) => r.el)}
          {!rows.length && <p className="t-small c-secondary">{s.t("nothingToday")}</p>}
        </section>

        <div className="capacity">
          <Clock size={16} strokeWidth={ICON_STROKE} />
          <span className="t-micro">
            {free > 0 ? s.t("leftForUnplanned", { time: s.dur(free) }) : s.t("dayIsFull")}
          </span>
        </div>
      </div>
      <Fab />
    </div>
  );
}
