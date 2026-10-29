import { FolderOpen } from "lucide-react";
import { Empty, SubScreen } from "../../components";
import { type ItemStatus, useStore } from "../../state";
import { ItemCard } from "./ItemCard";

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
            <span className="t-micro c-secondary">
              {label} · {s.n(items.length)}
            </span>
            {items.map((i) => (
              <ItemCard
                key={i.id}
                item={i}
                actions={[{ label: s.t("returnToInbox"), onClick: () => s.patchItem(i.id, { status: "inbox" }) }]}
              />
            ))}
          </section>
        );
      })}
    </SubScreen>
  );
}
