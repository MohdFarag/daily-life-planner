import { AlertTriangle, Check } from "lucide-react";
import { ICON_STROKE } from "../../components";
import { type Busy, useStore } from "../../state";

export function SessionPreview({ range, date, context }: { range: string; date: string; context: string | null }) {
  const s = useStore();
  return (
    <div className="preview-box">
      <p className="t-h2">{s.n(range)}</p>
      <p className="t-micro">{[s.dayLabel(date), context, s.t("calendarReminder")].filter(Boolean).join(" · ")}</p>
    </div>
  );
}

/** Whether the session fits, or what it collides with. */
export function Availability({ conflicts, inWorkBlock }: { conflicts: Busy[]; inWorkBlock: boolean }) {
  const s = useStore();
  const clash = conflicts.length > 0;
  return (
    <div className={`availability ${clash ? "warn" : "ok"}`} role="status">
      {clash ? <AlertTriangle size={20} strokeWidth={ICON_STROKE} /> : <Check size={20} strokeWidth={2} />}
      <span className="t-label">
        {clash
          ? s.t("conflictWith", { what: conflicts.map((c) => c.label).join(", ") })
          : inWorkBlock
            ? s.t("fitsWorkBlock")
            : s.t("fitsDay")}
      </span>
    </div>
  );
}
