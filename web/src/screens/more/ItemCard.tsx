import { ICON_STROKE, KIND_ICON } from "../../components";
import type { InboxItem } from "../../state";

/** An inbox item in a collection, with the actions that apply to it there. */
export function ItemCard({ item, actions }: { item: InboxItem; actions: { label: string; onClick: () => void; danger?: boolean }[] }) {
  const Icon = KIND_ICON[item.kind];
  return (
    <div className="card" style={{ padding: 16, display: "flex", flexDirection: "column", gap: 8 }}>
      <div style={{ display: "flex", gap: 12, alignItems: "flex-start" }}>
        <span className="type-icon"><Icon size={22} strokeWidth={ICON_STROKE} /></span>
        <span className="item-content">
          <span className="t-body">{item.title}</span>
          {item.url && <a className="t-micro c-primary truncate" href={item.url} target="_blank" rel="noreferrer">{item.url}</a>}
          {item.body && <span className="t-small c-secondary">{item.body.split("\n")[0]}</span>}
        </span>
      </div>
      <div style={{ display: "flex", gap: 16, justifyContent: "flex-end" }}>
        {actions.map((a) => (
          <button key={a.label} className="link-btn" style={a.danger ? { color: "#9a3b2a" } : undefined} onClick={a.onClick}>{a.label}</button>
        ))}
      </div>
    </div>
  );
}
