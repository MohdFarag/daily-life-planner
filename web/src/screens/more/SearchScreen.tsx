import { Inbox as InboxIcon, Search as SearchIcon } from "lucide-react";
import { useState } from "react";
import { Empty, ICON_STROKE, KIND_ICON, SubScreen, TaskRow } from "../../components";
import { useStore } from "../../state";

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
