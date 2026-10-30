/** The phone frame around every screen: status bar, gesture handle, bottom navigation, capture button. */
import { BatteryFull, CalendarDays, Ellipsis, Inbox, type LucideIcon, Plus, SignalHigh, Sun, Wifi } from "lucide-react";
import { useTicker } from "../hooks/useTicker";
import { type Tab, useStore } from "../state";
import { ICON_STROKE } from "./icons";

export function StatusBar() {
  useTicker(30_000);
  const { n } = useStore();
  const now = new Date();
  return (
    <div className="statusbar" aria-hidden>
      <span className="t-micro">{n(`${now.getHours()}:${String(now.getMinutes()).padStart(2, "0")}`)}</span>
      <span className="statusbar-icons">
        <SignalHigh size={14} strokeWidth={2.2} />
        <Wifi size={14} strokeWidth={2.2} />
        <BatteryFull size={18} strokeWidth={1.8} />
      </span>
    </div>
  );
}

export function Gesture({ standalone }: { standalone?: boolean }) {
  return (
    <div className={`gesture${standalone ? " standalone" : ""}`} aria-hidden>
      <span />
    </div>
  );
}

const NAV: { tab: Tab; icon: LucideIcon; key: "today" | "inbox" | "plan" | "more" }[] = [
  { tab: "today", icon: Sun, key: "today" },
  { tab: "inbox", icon: Inbox, key: "inbox" },
  { tab: "plan", icon: CalendarDays, key: "plan" },
  { tab: "more", icon: Ellipsis, key: "more" },
];

export function BottomNav({ active }: { active: Tab }) {
  const s = useStore();
  return (
    <nav className="bottom-nav" aria-label="Main">
      <div className="nav-row">
        {NAV.map(({ tab, icon: Icon, key }) => (
          <button
            key={tab}
            className={`nav-item${active === tab ? " active" : ""}`}
            aria-current={active === tab ? "page" : undefined}
            onClick={() => s.tab(tab)}
          >
            <span className="nav-ind">
              <Icon size={22} strokeWidth={ICON_STROKE} />
            </span>
            {s.t(key)}
          </button>
        ))}
      </div>
      <Gesture />
    </nav>
  );
}

export function Fab() {
  const s = useStore();
  return (
    <button className="fab" aria-label={s.t("captureSomething")} onClick={() => s.setCaptureOpen(true)}>
      <Plus size={24} strokeWidth={2} />
    </button>
  );
}
