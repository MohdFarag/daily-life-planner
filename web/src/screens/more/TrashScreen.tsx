import { Trash2 } from "lucide-react";
import { Button, Empty, SubScreen } from "../../components";
import { useStore } from "../../state";
import { ItemCard } from "./ItemCard";

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
