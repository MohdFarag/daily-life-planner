import { ChevronRight, FolderOpen, Inbox as InboxIcon, ListChecks, Moon, RotateCcw, Search as SearchIcon, Settings2, Trash2, CalendarDays, type LucideIcon } from "lucide-react";
import { useState } from "react";
import { Button, Empty, Fab, ICON_STROKE, KIND_ICON, PageHeading, SubScreen, TaskRow, Toggle } from "../components";
import { PRAYERS, PRAYER_MIN, useStore, type InboxItem, type ItemStatus } from "../store";
import { fromMin, toMin } from "../time";

function Row({ icon: Icon, title, subtitle, onClick }: { icon: LucideIcon; title: string; subtitle: string; onClick: () => void }) {
  return (
    <button className="card list-row" onClick={onClick}>
      <span className="type-icon">
        <Icon size={22} strokeWidth={ICON_STROKE} />
      </span>
      <span style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
        <span className="t-body">{title}</span>
        <span className="t-micro c-secondary truncate">{subtitle}</span>
      </span>
      <ChevronRight size={20} strokeWidth={ICON_STROKE} className="chev flip-rtl" />
    </button>
  );
}

export function More() {
  const s = useStore();
  const open = s.data.tasks.filter((x) => !x.done).length;
  return (
    <div className="screen">
      <div className="content">
        <PageHeading title={s.t("more")} subtitle={s.t("moreSubtitle")} search={false} />
        <section className="stack-8">
          <span className="t-micro c-secondary section-label">{s.t("yourSpace")}</span>
          <Row icon={ListChecks} title={s.t("tasks")} subtitle={s.t("openTasksSub", { n: open })} onClick={() => s.go({ name: "tasks" })} />
          <Row icon={FolderOpen} title={s.t("collections")} subtitle={s.t("collectionsSub")} onClick={() => s.go({ name: "collections" })} />
        </section>
        <section className="stack-8">
          <span className="t-micro c-secondary section-label">{s.t("settings")}</span>
          <Row icon={Moon} title={s.t("prayerTimes")} subtitle={s.t("prayerSub", { state: s.data.protectPrayers ? s.t("on") : s.t("off") })} onClick={() => s.go({ name: "prayers" })} />
          <Row icon={CalendarDays} title={s.t("googleCalendar")} subtitle={s.t("googleSub", { state: s.data.showGoogle ? s.t("shown") : s.t("hidden") })} onClick={() => s.go({ name: "google" })} />
          <Row icon={Settings2} title={s.t("preferences")} subtitle={s.t("preferencesSub")} onClick={() => s.go({ name: "prefs" })} />
        </section>
        <div className="data-controls">
          <button className="link-btn" onClick={s.exportData}>{s.t("exportData")}</button>
          <button className="link-btn" onClick={() => s.go({ name: "trash" })}>{s.t("trashTitle")}</button>
        </div>
      </div>
      <Fab />
    </div>
  );
}

export function TasksScreen() {
  const s = useStore();
  const [filter, setFilter] = useState<"all" | "Work" | "Personal">("all");
  const tasks = s.data.tasks.filter((x) => filter === "all" || x.list === filter);
  const open = tasks.filter((x) => !x.done);
  const done = tasks.filter((x) => x.done);
  return (
    <SubScreen title={s.t("allTasks")}>
      <div className="filters">
        {(["all", "Work", "Personal"] as const).map((f) => (
          <button key={f} className={`filter${filter === f ? " active" : ""}`} onClick={() => setFilter(f)}>
            {f === "all" ? s.t("all") : s.listName(f)}
          </button>
        ))}
      </div>
      <span className="t-micro c-secondary">{s.t("open")} · {s.n(open.length)}</span>
      {open.map((x) => <TaskRow key={x.id} task={x} />)}
      {!tasks.length && <Empty icon={ListChecks} title={s.t("noTasks")} />}
      {done.length > 0 && <span className="t-micro c-secondary">{s.t("completed")} · {s.n(done.length)}</span>}
      {done.map((x) => <TaskRow key={x.id} task={x} />)}
    </SubScreen>
  );
}

function ItemCard({ item, actions }: { item: InboxItem; actions: { label: string; onClick: () => void; danger?: boolean }[] }) {
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

export function CollectionsScreen() {
  const s = useStore();
  const groups: [ItemStatus, string][] = [
    ["reference", s.t("references")],
    ["someday", s.t("someday")],
    ["waiting", s.t("waiting")],
  ];
  const any = s.data.inbox.some((i) => groups.some(([g]) => g === i.status));
  return (
    <SubScreen title={s.t("collections")}>
      {!any && <Empty icon={FolderOpen} title={s.t("emptyCollection")} />}
      {groups.map(([status, label]) => {
        const items = s.data.inbox.filter((i) => i.status === status);
        if (!items.length) return null;
        return (
          <section key={status} className="stack-8">
            <span className="t-micro c-secondary">{label} · {s.n(items.length)}</span>
            {items.map((i) => (
              <ItemCard key={i.id} item={i} actions={[{ label: s.t("returnToInbox"), onClick: () => s.patchItem(i.id, { status: "inbox" }) }]} />
            ))}
          </section>
        );
      })}
    </SubScreen>
  );
}

export function TrashScreen() {
  const s = useStore();
  const items = s.data.inbox.filter((i) => i.status === "trash");
  return (
    <SubScreen title={s.t("trashTitle")}>
      {!items.length && <Empty icon={Trash2} title={s.t("trashEmpty")} />}
      {items.map((i) => (
        <ItemCard
          key={i.id}
          item={i}
          actions={[
            { label: s.t("restore"), onClick: () => s.patchItem(i.id, { status: "inbox" }) },
            { label: s.t("deleteForever"), onClick: () => s.deleteItem(i.id), danger: true },
          ]}
        />
      ))}
      {items.length > 0 && <Button variant="secondary" onClick={() => items.forEach((i) => s.deleteItem(i.id))}>{s.t("emptyTrash")}</Button>}
    </SubScreen>
  );
}

export function PrayersScreen() {
  const s = useStore();
  return (
    <SubScreen title={s.t("prayerTimes")}>
      <div className="card" style={{ padding: 16, display: "flex", alignItems: "center", gap: 12 }}>
        <span style={{ flex: 1 }}>
          <span className="t-body" style={{ display: "block" }}>{s.t("protectPrayers")}</span>
          <span className="t-micro c-secondary">{s.t("protectPrayersHint")}</span>
        </span>
        <Toggle on={s.data.protectPrayers} label={s.t("protectPrayers")} onChange={(v) => s.update((d) => ({ ...d, protectPrayers: v }))} />
      </div>
      <div className="stack-8">
        {PRAYERS.map((p) => (
          <div key={p.key} className="prayer-row">
            <Moon size={24} strokeWidth={ICON_STROKE} />
            <span className="t-label" style={{ flex: 1 }}>{s.prayerName(p.key)}</span>
            <span className="t-micro c-secondary">{s.n(`${p.start}–${fromMin(toMin(p.start) + PRAYER_MIN)}`)}</span>
          </div>
        ))}
      </div>
      <p className="t-micro c-secondary">{s.t("prayerDisclaimer")}</p>
    </SubScreen>
  );
}

export function GoogleScreen() {
  const s = useStore();
  return (
    <SubScreen title={s.t("googleCalendar")}>
      <div className="card" style={{ padding: 16, display: "flex", alignItems: "center", gap: 12 }}>
        <span className="t-body" style={{ flex: 1 }}>{s.t("showGoogle")}</span>
        <Toggle on={s.data.showGoogle} label={s.t("showGoogle")} onChange={(v) => s.update((d) => ({ ...d, showGoogle: v }))} />
      </div>
      <p className="t-micro c-secondary">{s.t("googleDisclaimer")}</p>
    </SubScreen>
  );
}

export function PrefsScreen() {
  const s = useStore();
  return (
    <SubScreen title={s.t("preferences")}>
      <span className="t-micro c-secondary">{s.t("language")}</span>
      <div className="segmented full" role="radiogroup" aria-label={s.t("language")}>
        {(["en", "ar"] as const).map((l) => (
          <button
            key={l}
            role="radio"
            aria-checked={s.lang === l}
            className={s.lang === l ? "active" : ""}
            onClick={() => s.update((d) => ({ ...d, lang: l }))}
          >
            {l === "en" ? "English" : "العربية"}
          </button>
        ))}
      </div>
      <Button variant="secondary" onClick={() => window.confirm(s.t("resetConfirm")) && s.reset()}>
        <RotateCcw size={18} strokeWidth={ICON_STROKE} /> {s.t("resetData")}
      </Button>
    </SubScreen>
  );
}

export function SearchScreen() {
  const s = useStore();
  const [q, setQ] = useState("");
  const query = q.trim().toLowerCase();
  const match = (...fields: (string | undefined)[]) => fields.some((f) => f?.toLowerCase().includes(query));
  const tasks = query ? s.data.tasks.filter((x) => match(x.title, x.titleAr, x.notes, x.notesAr)) : [];
  const items = query ? s.data.inbox.filter((i) => i.status !== "processed" && i.status !== "trash" && match(i.title, i.body, i.url)) : [];
  return (
    <SubScreen title={s.t("search")}>
      <input
        className="search-input"
        autoFocus
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder={s.t("searchPlaceholder")}
        aria-label={s.t("search")}
      />
      {tasks.map((x) => <TaskRow key={x.id} task={x} />)}
      {items.map((i) => {
        const Icon = KIND_ICON[i.kind];
        return (
          <button
            key={i.id}
            className="card inbox-item"
            onClick={() => (i.status === "inbox" ? s.go({ name: "process", itemId: i.id }) : s.go({ name: "collections" }))}
          >
            <span className="type-icon"><Icon size={22} strokeWidth={ICON_STROKE} /></span>
            <span className="item-content">
              <span className="t-body truncate">{i.title}</span>
              <span className="t-micro c-secondary">{i.status === "inbox" ? s.t("inbox") : s.t("collections")}</span>
            </span>
          </button>
        );
      })}
      {query && !tasks.length && !items.length && <Empty icon={SearchIcon} title={s.t("noResults")} />}
      {!query && <Empty icon={InboxIcon} title={s.t("searchPlaceholder")} />}
    </SubScreen>
  );
}
