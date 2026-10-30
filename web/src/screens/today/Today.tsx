import { Clock } from "lucide-react";
import { Fab, ICON_STROKE, PageHeading, PrayerRow, TaskRow } from "../../components";
import { useTicker } from "../../hooks/useTicker";
import { nowMin, todayISO } from "../../lib/time";
import { dayPlan, prayerWindow, timeRange, useStore } from "../../state";
import { NothingNextCard, ProgressCard, UpNextCard } from "./TodayCards";

export function Today() {
  useTicker(60_000);
  const s = useStore();
  const today = todayISO();
  const { upNext, rows } = dayPlan(s.data.tasks, today, nowMin());
  const free = s.freeMinutesToday();
  // The design reads "Thursday, 29 October"; Arabic uses its locale's own order.
  const dateLabel =
    s.lang === "ar"
      ? s.date(today, { weekday: "long", day: "numeric", month: "long" })
      : `${s.date(today, { weekday: "long" })}, ${s.date(today, { day: "numeric", month: "long" })}`;

  return (
    <div className="screen">
      <div className="content">
        <PageHeading title={s.t("today")} subtitle={dateLabel} />
        <ProgressCard done={s.todaysTasks.filter((x) => x.done).length} total={s.todaysTasks.length} />
        {upNext ? <UpNextCard task={upNext} /> : <NothingNextCard />}

        <section className="section">
          <button className="section-heading" onClick={() => s.tab("plan")}>
            <span className="t-h3">{s.t("yourDay")}</span>
            <span className="t-label c-primary">{s.t("viewPlan")}</span>
          </button>
          {rows.map((row) => {
            if (row.kind === "task") return <TaskRow key={row.task.id} task={row.task} />;
            const { start, end } = prayerWindow(row.prayer);
            return (
              <PrayerRow
                key={row.prayer.key}
                name={s.t("prayer", { name: s.prayerName(row.prayer.key) })}
                time={s.n(timeRange(start, end))}
              />
            );
          })}
          {!rows.length && <p className="t-small c-secondary">{s.t("nothingToday")}</p>}
        </section>

        <div className="capacity">
          <Clock size={16} strokeWidth={ICON_STROKE} />
          <span className="t-micro">{free > 0 ? s.t("leftForUnplanned", { time: s.dur(free) }) : s.t("dayIsFull")}</span>
        </div>
      </div>
      <Fab />
    </div>
  );
}
