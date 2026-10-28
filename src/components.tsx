import {
  BatteryFull, BookOpen, CalendarDays, Camera, Check, ChevronLeft, Ellipsis, FileText, Inbox, Link2, Mic, Moon, Plus, Search, SignalHigh, Sun, Type, Wifi,
  type LucideIcon,
} from "lucide-react";
import { useEffect, type ReactNode } from "react";
import { useStore, useTicker, type InboxItem, type ItemKind, type Tab, type Task } from "./store";
import { locale } from "./i18n";

export const ICON_STROKE = 1.75;

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

export function IconButton({ icon: Icon, label, onClick, flip }: { icon: LucideIcon; label: string; onClick?: () => void; flip?: boolean }) {
  return (
    <button className="icon-btn" aria-label={label} title={label} onClick={onClick}>
      <Icon size={24} strokeWidth={ICON_STROKE} className={flip ? "flip-rtl" : undefined} />
    </button>
  );
}

export function Button({
  children, variant = "primary", onClick, disabled, type = "button",
}: { children: ReactNode; variant?: "primary" | "secondary" | "quiet" | "light"; onClick?: () => void; disabled?: boolean; type?: "button" | "submit" }) {
  return (
    <button type={type} className={`btn btn-${variant}`} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  );
}

export function Checkbox({ checked }: { checked: boolean }) {
  return <span className={`checkbox${checked ? " checked" : ""}`}>{checked && <Check size={16} strokeWidth={2.5} />}</span>;
}

export function PageHeading({ title, subtitle, search = true }: { title: string; subtitle: string; search?: boolean }) {
  const { go, t } = useStore();
  return (
    <div className="page-heading">
      <div className="title-row">
        <h1 className="t-h1" style={{ margin: 0 }}>{title}</h1>
        {search && <IconButton icon={Search} label={t("search")} onClick={() => go({ name: "search" })} />}
      </div>
      <p className="t-small c-secondary">{subtitle}</p>
    </div>
  );
}

export function TaskRow({ task, meta }: { task: Task; meta?: string }) {
  const s = useStore();
  const metadata =
    meta ??
    [task.session ? s.n(task.session.start) : null, `${s.n(task.estimate)} ${s.t("min")}`, s.listName(task.list)].filter(Boolean).join(" · ");
  return (
    <div className={`task-row${task.done ? " done" : ""}`}>
      <button
        className="complete-hit"
        role="checkbox"
        aria-checked={task.done}
        aria-label={task.done ? s.t("markOpen") : s.t("completeTask")}
        onClick={() => s.toggleDone(task.id)}
      >
        <Checkbox checked={task.done} />
      </button>
      <button className="task-info" onClick={() => s.go({ name: "task", id: task.id })}>
        <span className="t-body task-title truncate" style={{ maxWidth: "100%" }}>{s.taskTitle(task)}</span>
        <span className="t-micro c-secondary">{metadata}</span>
      </button>
    </div>
  );
}

export function PrayerRow({ name, time }: { name: string; time: string }) {
  const { t } = useStore();
  return (
    <div className="prayer-row">
      <Moon size={24} strokeWidth={ICON_STROKE} />
      <div style={{ display: "flex", flexDirection: "column" }}>
        <span className="t-label">{name}</span>
        <span className="t-micro c-secondary">
          {time} · {t("protectedTime")}
        </span>
      </div>
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

export const KIND_ICON: Record<ItemKind, LucideIcon> = {
  text: Type,
  link: BookOpen,
  voice: Mic,
  photo: Camera,
  file: FileText,
};
export const LinkIcon = Link2;

export function useKindLabel() {
  const { t } = useStore();
  return (kind: ItemKind) =>
    ({ text: t("quickNote"), link: t("link"), voice: t("voiceNote"), photo: t("photo"), file: t("file") })[kind];
}

export function useRelativeTime() {
  const { t, lang } = useStore();
  return (ts: number) => {
    const diff = Date.now() - ts;
    const min = Math.floor(diff / 60_000);
    if (min < 1) return t("justNow");
    if (min < 60) return t("minutesAgo", { n: min });
    const h = Math.floor(min / 60);
    if (h === 1) return t("hourAgo");
    if (h < 24) return t("hoursAgo", { n: h });
    const d = Math.floor(h / 24);
    if (d === 1) return t("yesterday");
    if (d < 7) return t("daysAgo", { n: d });
    return new Intl.DateTimeFormat(locale(lang), { day: "numeric", month: "short" }).format(ts);
  };
}

export function itemSubtitle(item: InboxItem, kindLabel: string, when: string, sharedFrom: (o: string) => string) {
  return item.origin ? `${sharedFrom(item.origin)} · ${when}` : `${kindLabel} · ${when}`;
}

export function Sheet({ children, onClose, label }: { children: ReactNode; onClose: () => void; label: string }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <>
      <div className="scrim" onClick={onClose} />
      <div className="sheet" role="dialog" aria-modal="true" aria-label={label}>
        <div className="drag-handle">
          <span />
        </div>
        {children}
      </div>
    </>
  );
}

export function Toggle({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return <button className={`toggle${on ? " on" : ""}`} role="switch" aria-checked={on} aria-label={label} onClick={() => onChange(!on)} />;
}

export function Empty({ icon: Icon, title, hint }: { icon: LucideIcon; title: string; hint?: string }) {
  return (
    <div className="empty">
      <Icon size={32} strokeWidth={1.5} />
      <p className="t-label" style={{ color: "var(--color-text)" }}>{title}</p>
      {hint && <p className="t-small">{hint}</p>}
    </div>
  );
}

export function SubScreen({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  const s = useStore();
  return (
    <div className="screen">
      <div className="content no-fab">
        <div className="top-bar">
          <IconButton icon={BackIcon} label={s.t("back")} onClick={s.back} flip />
          <span className="t-label c-secondary">{title}</span>
          {action ?? <span style={{ width: 48 }} />}
        </div>
        {children}
      </div>
    </div>
  );
}

export const BackIcon = ChevronLeft;
