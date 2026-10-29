import { type LucideIcon, Search } from "lucide-react";
import { type ReactNode, useEffect } from "react";
import { useStore } from "../state";
import { IconButton } from "./Controls";
import { BackIcon } from "./icons";

export function PageHeading({ title, subtitle, search = true }: { title: string; subtitle: string; search?: boolean }) {
  const { go, t } = useStore();
  return (
    <div className="page-heading">
      <div className="title-row">
        <h1 className="t-h1" style={{ margin: 0 }}>
          {title}
        </h1>
        {search && <IconButton icon={Search} label={t("search")} onClick={() => go({ name: "search" })} />}
      </div>
      <p className="t-small c-secondary">{subtitle}</p>
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

export function Empty({ icon: Icon, title, hint }: { icon: LucideIcon; title: string; hint?: string }) {
  return (
    <div className="empty">
      <Icon size={32} strokeWidth={1.5} />
      <p className="t-label" style={{ color: "var(--color-text)" }}>
        {title}
      </p>
      {hint && <p className="t-small">{hint}</p>}
    </div>
  );
}

/** A full screen shown when what it was opened for no longer exists. */
export function MissingScreen({ icon, title }: { icon: LucideIcon; title: string }) {
  const s = useStore();
  return (
    <div className="screen">
      <div className="content no-fab">
        <div className="top-bar">
          <IconButton icon={BackIcon} label={s.t("back")} onClick={s.back} flip />
        </div>
        <Empty icon={icon} title={title} />
      </div>
    </div>
  );
}
