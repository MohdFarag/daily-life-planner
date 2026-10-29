import { CalendarPlus } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Fab, ICON_STROKE, PageHeading } from "../components";
import { useTicker } from "../hooks/useTicker";
import { locale } from "../i18n";
import { fromMin, nowMin, parseISO, todayISO, toMin, weekOf } from "../lib/time";
import { type Busy, useStore, WORK_BLOCK } from "../state";

const HOUR_PX = 74.4;
const DAY_START = 6 * 60;
const DAY_END = 22 * 60;
const px = (min: number) => ((min - DAY_START) / 60) * HOUR_PX;

type View = "day" | "week" | "agenda";

const KIND_COLOR: Record<Busy["kind"], string> = {
  task: "var(--color-primary)",
  prayer: "var(--color-highlight)",
  google: "var(--color-warningText)",
};

export function Plan() {
  useTicker(60_000);
  const s = useStore();
  const today = todayISO();
  const [selected, setSelected] = useState(today);
  const [view, setView] = useState<View>("day");
  const scroller = useRef<HTMLDivElement>(null);
  const week = weekOf(selected);
  const fmt = (opts: Intl.DateTimeFormatOptions, iso: string) => new Intl.DateTimeFormat(locale(s.lang), opts).format(parseISO(iso));

  useEffect(() => {
    if (view === "day" && scroller.current) scroller.current.scrollTop = px(toMin("09:00")) - 9;
  }, [view, selected]);

  const busy = s.busyOn(selected, { includeDone: true });
  const unplanned = s.data.tasks.filter((x) => !x.done && !x.session);
  const hasTasks = (iso: string) => s.data.tasks.some((x) => x.session?.date === iso);

  const openBusy = (b: Busy) => b.kind === "task" && s.go({ name: "task", id: b.id });

  return (
    <div className="screen">
      <div className="content">
        <PageHeading title={s.t("plan")} subtitle={fmt({ month: "long", year: "numeric" }, selected)} />

        <div className="week" role="listbox" aria-label={s.t("week")}>
          {week.map((iso) => (
            <button
              key={iso}
              role="option"
              aria-selected={iso === selected}
              className={`day${iso === selected ? " selected" : ""}${iso === today ? " today" : ""}`}
              onClick={() => setSelected(iso)}
            >
              <span className="t-micro">{fmt({ weekday: "narrow" }, iso)}</span>
              <span className="day-num">
                {fmt({ day: "numeric" }, iso)}
                {hasTasks(iso) && iso !== selected && <span className="dot" />}
              </span>
            </button>
          ))}
        </div>

        <div className="view-row">
          <div className="segmented" role="tablist">
            {(["day", "week"] as const).map((v) => (
              <button key={v} role="tab" aria-selected={view === v} className={view === v ? "active" : ""} onClick={() => setView(v)}>
                {s.t(v)}
              </button>
            ))}
          </div>
          <button className={`agenda-btn${view === "agenda" ? " active" : ""}`} aria-pressed={view === "agenda"} onClick={() => setView("agenda")}>
            {s.t("agenda")}
          </button>
        </div>

        {view === "day" && (
          <div className="timeline-scroll" ref={scroller}>
            <div className="timeline" style={{ height: px(DAY_END) + 12 }}>
              <div
                className="work-block t-micro"
                style={{ top: px(toMin(WORK_BLOCK.start)), height: px(toMin(WORK_BLOCK.end)) - px(toMin(WORK_BLOCK.start)) }}
              >
                {s.t("workBlockCaps")}
              </div>
              {Array.from({ length: (DAY_END - DAY_START) / 60 + 1 }, (_, i) => DAY_START + i * 60).map((m) => (
                <div key={m}>
                  <span className="hour-label t-micro" style={{ top: px(m) }}>{s.n(fromMin(m))}</span>
                  <div className="hour-rule" style={{ top: px(m) }} />
                </div>
              ))}
              {busy.map((b) => {
                const h = Math.max(22, px(b.end) - px(b.start) - 2);
                return (
                  <button
                    key={b.kind + b.id}
                    className={`event ${b.kind}${b.done ? " done" : ""}`}
                    style={{ top: px(b.start) + 1, height: h, cursor: b.kind === "task" ? "pointer" : "default" }}
                    onClick={() => openBusy(b)}
                    tabIndex={b.kind === "task" ? 0 : -1}
                  >
                    {b.kind === "task" && h > 44 ? (
                      <>
                        <span className="t-label truncate">{b.label}</span>
                        <span className="t-micro truncate">{s.n(`${fromMin(b.start)}–${fromMin(b.end)}`)}</span>
                      </>
                    ) : (
                      <span className="t-micro truncate">
                        {b.kind === "task" ? `${s.n(fromMin(b.start))} · ${b.label}` : `${s.n(`${fromMin(b.start)}–${fromMin(b.end)}`)} · ${b.label}`}
                      </span>
                    )}
                  </button>
                );
              })}
              {selected === today && nowMin() >= DAY_START && nowMin() <= DAY_END && (
                <div className="now-line" style={{ top: px(nowMin()) }} aria-hidden />
              )}
            </div>
          </div>
        )}

        {view === "agenda" && <AgendaList items={busy} onOpen={openBusy} />}

        {view === "week" && (
          <div className="stack-12">
            {week.map((iso) => {
              const items = s.busyOn(iso, { includeDone: true }).filter((b) => b.kind !== "prayer");
              return (
                <div key={iso} className="stack-8">
                  <button className="t-label" style={{ textAlign: "start", color: iso === today ? "var(--color-primary)" : undefined }} onClick={() => { setSelected(iso); setView("day"); }}>
                    {fmt({ weekday: "long", day: "numeric", month: "short" }, iso)}
                  </button>
                  {items.length ? <AgendaList items={items} onOpen={openBusy} /> : <p className="t-micro c-secondary">{s.t("emptyDay")}</p>}
                </div>
              );
            })}
          </div>
        )}

        <div className="legend t-micro">
          <span><i style={{ background: KIND_COLOR.task }} />{s.t("tasks")}</span>
          <span><i style={{ background: "var(--color-primary)", opacity: 0.35 }} />{s.t("prayers")}</span>
          {s.data.showGoogle && <span><i style={{ background: KIND_COLOR.google }} />{s.t("google")}</span>}
        </div>

        {unplanned.length > 0 && (
          <section className="section">
            <div className="section-heading">
              <span className="t-h3">{s.t("notPlanned")}</span>
            </div>
            {unplanned.map((x) => (
              <div key={x.id} className="task-row" style={{ minHeight: 64 }}>
                <button className="task-info" onClick={() => s.go({ name: "task", id: x.id })}>
                  <span className="t-body truncate">{s.taskTitle(x)}</span>
                  <span className="t-micro c-secondary">{s.n(x.estimate)} {s.t("min")} · {s.listName(x.list)}</span>
                </button>
                <button className="link-btn" style={{ display: "inline-flex", alignItems: "center", gap: 6, paddingInline: 8 }} onClick={() => s.setScheduleTaskId(x.id)}>
                  <CalendarPlus size={18} strokeWidth={ICON_STROKE} />
                  {s.t("planIt")}
                </button>
              </div>
            ))}
          </section>
        )}
      </div>
      <Fab />
    </div>
  );
}

function AgendaList({ items, onOpen }: { items: Busy[]; onOpen: (b: Busy) => void }) {
  const s = useStore();
  if (!items.length) return <p className="t-small c-secondary">{s.t("emptyDay")}</p>;
  return (
    <div className="card">
      {items.map((b, i) => (
        <button
          key={b.kind + b.id}
          className="agenda-item"
          style={{ borderTop: i ? "1px solid var(--color-border)" : undefined }}
          onClick={() => onOpen(b)}
        >
          <span className="agenda-time t-micro">{s.n(fromMin(b.start))}</span>
          <span className="swatch" style={{ background: b.kind === "prayer" ? "var(--color-tint)" : KIND_COLOR[b.kind] }} />
          <span style={{ display: "flex", flexDirection: "column", minWidth: 0 }}>
            <span className="t-label truncate" style={{ textDecoration: b.done ? "line-through" : undefined, color: b.done ? "var(--color-secondary)" : undefined }}>{b.label}</span>
            <span className="t-micro c-secondary">{s.n(`${fromMin(b.start)}–${fromMin(b.end)}`)}</span>
          </span>
        </button>
      ))}
    </div>
  );
}
