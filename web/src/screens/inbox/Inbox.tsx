import { CloudCheck, CloudOff, Inbox as InboxIcon } from "lucide-react";
import { useState } from "react";
import { Button, Empty, Fab, ICON_STROKE, KIND_ICON, PageHeading } from "../../components";
import { itemSubtitle, useKindLabel, useRelativeTime } from "../../hooks/useItemLabels";
import { formatClock } from "../../lib/time";
import { type InboxItem, inboxQueue, isLink, useStore } from "../../state";

type Filter = "all" | "notes" | "links";

export function Inbox() {
  const s = useStore();
  const [filter, setFilter] = useState<Filter>("all");
  const kindLabel = useKindLabel();
  const rel = useRelativeTime();
  const items = inboxQueue(s.data);
  const links = items.filter(isLink);
  const notes = items.filter((i) => !isLink(i));
  const visible = filter === "all" ? items : filter === "links" ? links : notes;
  const pending = items.filter((i) => i.pendingSync).length;

  const filters: [Filter, string, number][] = [
    ["all", s.t("all"), items.length],
    ["notes", s.t("notes"), notes.length],
    ["links", s.t("links"), links.length],
  ];

  return (
    <div className="screen">
      <div className="content">
        <PageHeading
          title={s.t("inbox")}
          subtitle={items.length === 1 ? s.t("inboxSubtitleOne") : s.t("inboxSubtitle", { n: items.length })}
        />
        <div className="filters" role="tablist">
          {filters.map(([key, label, count]) => (
            <button
              key={key}
              role="tab"
              aria-selected={filter === key}
              className={`filter${filter === key ? " active" : ""}`}
              onClick={() => setFilter(key)}
            >
              {label} {s.n(count)}
            </button>
          ))}
        </div>
        {items.length > 0 && <Button onClick={() => s.go({ name: "process" })}>{s.t("processInbox")}</Button>}

        <div className="stack-12">
          {visible.map((item) => (
            <InboxCard
              key={item.id}
              item={item}
              subtitle={itemSubtitle(item, kindLabel(item.kind), rel(item.createdAt), (app) => s.t("sharedFrom", { app }))}
            />
          ))}
          {!items.length && <Empty icon={InboxIcon} title={s.t("inboxEmpty")} hint={s.t("inboxEmptyHint")} />}
        </div>

        <div className="sync-state">
          {pending || !s.online ? <CloudOff size={20} strokeWidth={ICON_STROKE} /> : <CloudCheck size={20} strokeWidth={ICON_STROKE} />}
          <div className="t-micro">
            <p>
              {pending
                ? s.t(pending === 1 ? "waitingToSync" : "waitingToSyncMany", { n: pending })
                : s.t("allSynced")}
            </p>
            <p>{s.t("savedOnDevice")}</p>
          </div>
        </div>
      </div>
      <Fab />
    </div>
  );
}

function InboxCard({ item, subtitle }: { item: InboxItem; subtitle: string }) {
  const s = useStore();
  const Icon = KIND_ICON[item.kind];
  const detail =
    item.kind === "voice"
      ? `${s.n(formatClock(item.durationSec ?? 0))} · ${s.t("audioSaved")}`
      : item.kind === "file" || item.kind === "photo"
        ? item.body || item.fileName || ""
        : item.body.split("\n")[0];
  return (
    <button className="card inbox-item" onClick={() => s.go({ name: "process", itemId: item.id })}>
      <span className="type-icon">
        <Icon size={24} strokeWidth={ICON_STROKE} />
      </span>
      <span className="item-content">
        <span className="t-body truncate">{item.title}</span>
        <span className="t-micro c-secondary">{subtitle}</span>
        {detail && <span className="t-small c-secondary truncate">{detail}</span>}
      </span>
    </button>
  );
}
