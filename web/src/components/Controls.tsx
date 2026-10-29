import { Check, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { ICON_STROKE } from "./icons";

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

export function Toggle({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return <button className={`toggle${on ? " on" : ""}`} role="switch" aria-checked={on} aria-label={label} onClick={() => onChange(!on)} />;
}

export function MenuItem({ icon: Icon, label, onClick, danger }: { icon: LucideIcon; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button role="menuitem" className={`menu-item${danger ? " danger" : ""}`} onClick={onClick}>
      <Icon size={20} strokeWidth={ICON_STROKE} />
      {label}
    </button>
  );
}
