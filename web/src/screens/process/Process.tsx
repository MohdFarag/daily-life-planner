import { CheckCheck, Undo2, X } from "lucide-react";
import { BackIcon, Button, Empty, Gesture, ICON_STROKE, IconButton } from "../../components";
import type { StringKey } from "../../i18n";
import { inboxQueue, type TriageAction, useStore } from "../../state";
import { CapturePreview } from "./CapturePreview";
import { Decisions } from "./Decisions";

/** Where a filed item went, for the confirmation toast. */
const DESTINATION: Partial<Record<TriageAction, StringKey>> = {
  reference: "references", someday: "someday", waiting: "waiting", trash: "trashTitle", split: "tasks",
};

/** Walks the inbox one item at a time, asking what each should become. */
export function Process({ itemId }: { itemId?: string }) {
  const s = useStore();
  const queue = inboxQueue(s.data);
  const item = queue.find((i) => i.id === itemId) ?? queue[0];
  const index = item ? queue.indexOf(item) : -1;

  const undo = () => {
    const restored = s.undoTriage();
    if (restored) s.replace({ name: "process", itemId: restored });
    s.setToast(null);
  };

  const decide = (action: TriageAction) => {
    if (!item) return;
    const next = queue[index + 1]?.id;
    const created = s.triage(item.id, action);
    if (action === "task") {
      s.replace({ name: "task", id: created[0], fresh: true });
    } else if (action === "now") {
      s.startFocus(created[0]);
      s.replace({ name: "focus" });
    } else {
      s.replace({ name: "process", itemId: next });
      s.showToast(s.t("movedTo", { where: s.t(DESTINATION[action]!) }), undo);
    }
  };

  return (
    <>
      <div className="screen">
        <div className="content gap-20 no-fab">
          <div className="top-bar">
            <IconButton icon={BackIcon} label={s.t("back")} onClick={s.back} flip />
            <span className="t-label c-secondary">{s.t("inbox")}</span>
            <IconButton icon={X} label={s.t("close")} onClick={() => s.tab("inbox")} />
          </div>

          {!item ? (
            <>
              <Empty icon={CheckCheck} title={s.t("inboxZero")} hint={s.t("inboxZeroHint")} />
              <Button onClick={() => s.tab("inbox")}>{s.t("backToInbox")}</Button>
            </>
          ) : (
            <>
              <div className="row-between" style={{ minHeight: 28 }}>
                <span className="t-micro c-secondary">{s.t("itemOf", { i: index + 1, n: queue.length })}</span>
                <button
                  className="row-between t-label c-secondary"
                  style={{ gap: 8, opacity: s.canUndo() ? 1 : 0.4 }}
                  disabled={!s.canUndo()}
                  onClick={undo}
                >
                  <Undo2 size={18} strokeWidth={ICON_STROKE} />
                  {s.t("undo")}
                </button>
              </div>
              <CapturePreview item={item} />
              <Decisions key={item.id} onDecide={decide} />
            </>
          )}
        </div>
      </div>
      <Gesture standalone />
    </>
  );
}
