import { ChevronRight } from "lucide-react";
import { useState } from "react";
import { ICON_STROKE, KIND_ICON } from "../../components";
import { useBlobUrl } from "../../hooks/useBlobUrl";
import { useRelativeTime } from "../../hooks/useItemLabels";
import { type Source, useStore } from "../../state";

/** The inbox capture a task came from, expandable to show its text and attachment. */
export function SourceCard({ source }: { source: Source }) {
  const s = useStore();
  const rel = useRelativeTime();
  const [open, setOpen] = useState(false);
  const blobUrl = useBlobUrl(open ? source.blobId : undefined);
  const Icon = KIND_ICON[source.kind];
  return (
    <div className="card">
      <button className="link-card" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <Icon size={24} strokeWidth={ICON_STROKE} />
        <span style={{ flex: 1, display: "flex", flexDirection: "column" }}>
          <span className="t-label" style={{ color: "var(--color-text)" }}>{s.t("originalNote")}</span>
          <span className="t-micro c-secondary">{s.t("openSource")}</span>
        </span>
        <ChevronRight size={18} strokeWidth={ICON_STROKE} className="flip-rtl" style={{ transform: open ? "rotate(90deg)" : undefined }} />
      </button>
      {open && (
        <div style={{ padding: "0 16px 16px" }} className="stack-8">
          <p className="t-body">{source.title}</p>
          {source.body && <p className="t-small c-secondary" style={{ whiteSpace: "pre-line" }}>{source.body}</p>}
          {source.url && <a className="t-label c-primary" href={source.url} target="_blank" rel="noreferrer">{source.url}</a>}
          {blobUrl && source.kind === "voice" && <audio controls src={blobUrl} style={{ width: "100%" }} />}
          {blobUrl && source.kind === "photo" && <img src={blobUrl} alt="" style={{ width: "100%", borderRadius: 12 }} />}
          {blobUrl && source.kind === "file" && <a className="t-label c-primary" href={blobUrl} download={source.fileName}>{source.fileName}</a>}
          <p className="t-micro c-secondary">{rel(source.createdAt)}</p>
        </div>
      )}
    </div>
  );
}
