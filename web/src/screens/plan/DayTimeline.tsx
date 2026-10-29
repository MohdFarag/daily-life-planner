import { useEffect, useRef } from "react";
import { fromMin, nowMin, todayISO, toMin } from "../../lib/time";
import { type Busy, timeRange, useStore, WORK_BLOCK } from "../../state";

const HOUR_PX = 74.4;
const DAY_START = 6 * 60;
const DAY_END = 22 * 60;
/** Taller blocks show the title and time on separate lines. */
const TWO_LINE_PX = 44;

/** Vertical position of a time of day, in pixels from the top of the timeline. */
const px = (min: number) => ((min - DAY_START) / 60) * HOUR_PX;

const HOURS = Array.from({ length: (DAY_END - DAY_START) / 60 + 1 }, (_, i) => DAY_START + i * 60);

/** One day as an hour grid with the work block, busy items and a now line; opens scrolled to 09:00. */
export function DayTimeline({ date, busy, onOpen }: { date: string; busy: Busy[]; onOpen: (b: Busy) => void }) {
  const s = useStore();
  const scroller = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (scroller.current) scroller.current.scrollTop = px(toMin("09:00")) - 9;
  }, [date]);
  const now = nowMin();

  return (
    <div className="timeline-scroll" ref={scroller}>
      <div className="timeline" style={{ height: px(DAY_END) + 12 }}>
        <div
          className="work-block t-micro"
          style={{ top: px(toMin(WORK_BLOCK.start)), height: px(toMin(WORK_BLOCK.end)) - px(toMin(WORK_BLOCK.start)) }}
        >
          {s.t("workBlockCaps")}
        </div>
        {HOURS.map((m) => (
          <div key={m}>
            <span className="hour-label t-micro" style={{ top: px(m) }}>
              {s.n(fromMin(m))}
            </span>
            <div className="hour-rule" style={{ top: px(m) }} />
          </div>
        ))}
        {busy.map((b) => (
          <TimelineBlock key={b.kind + b.id} item={b} onOpen={onOpen} />
        ))}
        {date === todayISO() && now >= DAY_START && now <= DAY_END && <div className="now-line" style={{ top: px(now) }} aria-hidden />}
      </div>
    </div>
  );
}

function TimelineBlock({ item: b, onOpen }: { item: Busy; onOpen: (b: Busy) => void }) {
  const s = useStore();
  const height = Math.max(22, px(b.end) - px(b.start) - 2);
  const isTask = b.kind === "task";
  return (
    <button
      className={`event ${b.kind}${b.done ? " done" : ""}`}
      style={{ top: px(b.start) + 1, height, cursor: isTask ? "pointer" : "default" }}
      onClick={() => onOpen(b)}
      tabIndex={isTask ? 0 : -1}
    >
      {isTask && height > TWO_LINE_PX ? (
        <>
          <span className="t-label truncate">{b.label}</span>
          <span className="t-micro truncate">{s.n(timeRange(b.start, b.end))}</span>
        </>
      ) : (
        <span className="t-micro truncate">
          {isTask ? s.n(fromMin(b.start)) : s.n(timeRange(b.start, b.end))} · {b.label}
        </span>
      )}
    </button>
  );
}
