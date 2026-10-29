import { ExternalLink } from "lucide-react";
import { ICON_STROKE, KIND_ICON } from "../../components";
import { useBlobUrl } from "../../hooks/useBlobUrl";
import { useKindLabel, useRelativeTime } from "../../hooks/useItemLabels";
import { formatClock } from "../../lib/time";
import { type InboxItem, useStore } from "../../state";

/** The captured item in full: text, link, and any recording, photo or file. */
export function CapturePreview({ item }: { item: InboxItem }) {
  const s = useStore();
  const kindLabel = useKindLabel();
  const rel = useRelativeTime();
  const blobUrl = useBlobUrl(item.blobId);
  const Icon = KIND_ICON[item.kind];
  return (
    <div className="card preview-card">
      <span className="badge badge-warning">{kindLabel(item.kind)}</span>
      <h2 className="t-h2" style={{ margin: 0, overflowWrap: "anywhere" }}>{item.title}</h2>
      {item.body && <p className="t-body c-secondary" style={{ whiteSpace: "pre-line" }}>{item.body}</p>}
      {item.url && (
        <a
          className="t-label c-primary"
          href={item.url}
          target="_blank"
          rel="noreferrer"
          style={{ display: "inline-flex", gap: 6, alignItems: "center", overflowWrap: "anywhere" }}
        >
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
      {item.kind === "file" && blobUrl && <a className="t-label c-primary" href={blobUrl} download={item.fileName}>{item.fileName}</a>}
      <div className="source-ref">
        <Icon size={18} strokeWidth={ICON_STROKE} />
        <span className="t-micro">{s.t("capturedOn", { when: rel(item.createdAt).toLowerCase() })}</span>
      </div>
    </div>
  );
}
