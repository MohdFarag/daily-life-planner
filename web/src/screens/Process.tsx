import { Archive, CheckCheck, Clock3, Ellipsis, ExternalLink, Scissors, Trash2, Undo2, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Button, Empty, Gesture, ICON_STROKE, IconButton, KIND_ICON, BackIcon, useKindLabel, useRelativeTime } from "../components";
import { getBlob } from "../blobs";
import { useStore, type TriageAction } from "../store";
import { formatClock } from "../time";

export function Process({ itemId }: { itemId?: string }) {
  const s = useStore();
  const kindLabel = useKindLabel();
  const rel = useRelativeTime();
  const [menuOpen, setMenuOpen] = useState(false);
  const queue = s.data.inbox.filter((i) => i.status === "inbox");
  const item = queue.find((i) => i.id === itemId) ?? queue[0];
  const index = item ? queue.indexOf(item) : -1;
  const blobUrl = useBlobUrl(item?.blobId);

  useEffect(() => setMenuOpen(false), [item?.id]);

  const decide = (action: TriageAction) => {
    if (!item) return;
    const next = queue[index + 1]?.id;
    const created = s.triage(item.id, action);
    if (action === "task") s.replace({ name: "task", id: created[0], fresh: true });
    else if (action === "now") {
      s.startFocus(created[0]);
      s.replace({ name: "focus" });
    } else {
      s.replace({ name: "process", itemId: next });
      const where: Record<string, string> = {
        reference: s.t("references"), someday: s.t("someday"), waiting: s.t("waiting"), trash: s.t("trashTitle"), split: s.t("tasks"),
      };
      s.showToast(s.t("movedTo", { where: where[action] }), undo);
    }
  };

  const undo = () => {
    const restored = s.undoTriage();
    if (restored) s.replace({ name: "process", itemId: restored });
    s.setToast(null);
  };

  const Icon = item ? KIND_ICON[item.kind] : Archive;

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

              <div className="card preview-card">
                <span className="badge badge-warning">{kindLabel(item.kind)}</span>
                <h2 className="t-h2" style={{ margin: 0, overflowWrap: "anywhere" }}>{item.title}</h2>
                {item.body && <p className="t-body c-secondary" style={{ whiteSpace: "pre-line" }}>{item.body}</p>}
                {item.url && (
                  <a className="t-label c-primary" href={item.url} target="_blank" rel="noreferrer" style={{ display: "inline-flex", gap: 6, alignItems: "center", overflowWrap: "anywhere" }}>
                    <ExternalLink size={16} /> {item.url}
                  </a>
                )}
                {item.kind === "voice" &&
                  (blobUrl ? (
                    <audio controls src={blobUrl} style={{ width: "100%" }} />
                  ) : (
                    <span className="t-micro c-secondary">{s.n(formatClock(item.durationSec ?? 0))} · {s.t("audioSaved")}</span>
                  ))}
                {item.kind === "photo" && blobUrl && <img src={blobUrl} alt="" style={{ width: "100%", borderRadius: 12 }} />}
                {item.kind === "file" && blobUrl && (
                  <a className="t-label c-primary" href={blobUrl} download={item.fileName}>{item.fileName}</a>
                )}
                <div className="source-ref">
                  <Icon size={18} strokeWidth={ICON_STROKE} />
                  <span className="t-micro">{s.t("capturedOn", { when: rel(item.createdAt).toLowerCase() })}</span>
                </div>
              </div>

              <p className="t-h3">{s.t("whatBecome")}</p>
              <div className="stack-8">
                <Button onClick={() => decide("task")}>{s.t("createTask")}</Button>
                <Button variant="secondary" onClick={() => decide("now")}>{s.t("doNow")}</Button>
                <Button variant="secondary" onClick={() => decide("reference")}>{s.t("saveReference")}</Button>
                <button className="row-between" style={{ minHeight: 48 }} aria-expanded={menuOpen} onClick={() => setMenuOpen((v) => !v)}>
                  <span className="t-small c-secondary">{s.t("otherDecisions")}</span>
                  <Ellipsis size={24} strokeWidth={ICON_STROKE} className="c-secondary" />
                </button>
                {menuOpen && (
                  <div className="card menu" role="menu">
                    <MenuItem icon={Clock3} label={s.t("someday")} onClick={() => decide("someday")} />
                    <MenuItem icon={Archive} label={s.t("waiting")} onClick={() => decide("waiting")} />
                    <MenuItem icon={Scissors} label={s.t("split")} onClick={() => decide("split")} />
                    <MenuItem icon={Trash2} label={s.t("trash")} onClick={() => decide("trash")} danger />
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>
      <Gesture standalone />
    </>
  );
}

export function MenuItem({ icon: Icon, label, onClick, danger }: { icon: typeof Trash2; label: string; onClick: () => void; danger?: boolean }) {
  return (
    <button role="menuitem" className={`menu-item${danger ? " danger" : ""}`} onClick={onClick}>
      <Icon size={20} strokeWidth={ICON_STROKE} />
      {label}
    </button>
  );
}

export function useBlobUrl(blobId?: string) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    setUrl(null);
    if (!blobId) return;
    let objectUrl: string | null = null;
    getBlob(blobId)
      .then((b) => {
        if (b) setUrl((objectUrl = URL.createObjectURL(b)));
      })
      .catch(() => undefined);
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [blobId]);
  return url;
}
