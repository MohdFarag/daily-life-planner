import { fromMin, todayISO, weekOf } from "../../lib/time";
import { type Busy, timeRange, useStore } from "../../state";
import { swatch } from "./kinds";

export function AgendaList({ items, onOpen }: { items: Busy[]; onOpen: (b: Busy) => void }) {
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
          <span className="swatch" style={{ background: swatch(b.kind) }} />
          <span style={{ display: "flex", flexDirection: "column", minWidth: 0 }}>
            <span
              className="t-label truncate"
              style={{ textDecoration: b.done ? "line-through" : undefined, color: b.done ? "var(--color-secondary)" : undefined }}
            >
              {b.label}
            </span>
            <span className="t-micro c-secondary">{s.n(timeRange(b.start, b.end))}</span>
          </span>
        </button>
      ))}
    </div>
  );
}

/** Each day of the week with its tasks and events (prayers are left out to keep it short). */
export function WeekAgenda({ selected, onOpen, onPickDay }: { selected: string; onOpen: (b: Busy) => void; onPickDay: (iso: string) => void }) {
  const s = useStore();
  const today = todayISO();
  return (
    <div className="stack-12">
      {weekOf(selected).map((iso) => {
        const items = s.busyOn(iso, { includeDone: true }).filter((b) => b.kind !== "prayer");
        return (
          <div key={iso} className="stack-8">
            <button
              className="t-label"
              style={{ textAlign: "start", color: iso === today ? "var(--color-primary)" : undefined }}
              onClick={() => onPickDay(iso)}
            >
              {s.date(iso, { weekday: "long", day: "numeric", month: "short" })}
            </button>
            {items.length ? <AgendaList items={items} onOpen={onOpen} /> : <p className="t-micro c-secondary">{s.t("emptyDay")}</p>}
          </div>
        );
      })}
    </div>
  );
}
