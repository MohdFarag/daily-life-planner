import { formatClock, fromMin, nowMin } from "../../lib/time";
import { type Focus, useStore } from "../../state";

const SIZE = 272;
const R = 130;
const CIRC = 2 * Math.PI * R;

/** The countdown: a ring that fills as time passes, with the time left and when it ends. */
export function FocusRing({ focus, remaining }: { focus: Focus; remaining: number }) {
  const s = useStore();
  const running = !!focus.runningSince;
  const progress = 1 - remaining / focus.totalSec;
  const status = remaining <= 0 ? s.t("timeUp") : running ? s.t("remainingOf", { n: Math.round(focus.totalSec / 60) }) : s.t("pausedNote");
  return (
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
        <span className="t-small c-secondary">{status}</span>
        {running && (
          <span className="t-micro c-secondary">{s.t("endsAt", { time: s.n(fromMin(nowMin() + Math.ceil(remaining / 60))) })}</span>
        )}
      </div>
    </div>
  );
}
