import { todayISO, weekOf } from "../../lib/time";
import { useStore } from "../../state";

export type View = "day" | "week" | "agenda";

/** The Monday–Sunday week around [selected]; a dot marks days with planned tasks. */
export function WeekStrip({ selected, onSelect }: { selected: string; onSelect: (iso: string) => void }) {
  const s = useStore();
  const today = todayISO();
  const hasTasks = (iso: string) => s.data.tasks.some((x) => x.session?.date === iso);
  return (
    <div className="week" role="listbox" aria-label={s.t("week")}>
      {weekOf(selected).map((iso) => (
        <button
          key={iso}
          role="option"
          aria-selected={iso === selected}
          className={`day${iso === selected ? " selected" : ""}${iso === today ? " today" : ""}`}
          onClick={() => onSelect(iso)}
        >
          <span className="t-micro">{s.date(iso, { weekday: "narrow" })}</span>
          <span className="day-num">
            {s.date(iso, { day: "numeric" })}
            {hasTasks(iso) && iso !== selected && <span className="dot" />}
          </span>
        </button>
      ))}
    </div>
  );
}

export function ViewSwitch({ view, onChange }: { view: View; onChange: (view: View) => void }) {
  const s = useStore();
  return (
    <div className="view-row">
      <div className="segmented" role="tablist">
        {(["day", "week"] as const).map((v) => (
          <button key={v} role="tab" aria-selected={view === v} className={view === v ? "active" : ""} onClick={() => onChange(v)}>
            {s.t(v)}
          </button>
        ))}
      </div>
      <button className={`agenda-btn${view === "agenda" ? " active" : ""}`} aria-pressed={view === "agenda"} onClick={() => onChange("agenda")}>
        {s.t("agenda")}
      </button>
    </div>
  );
}
