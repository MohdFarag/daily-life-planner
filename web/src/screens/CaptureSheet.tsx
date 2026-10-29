import { CloudCheck, Mic, Square, Upload, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Button, ICON_STROKE, IconButton, KIND_ICON, Sheet } from "../components";
import { putBlob } from "../lib/blobs";
import { uid } from "../lib/ids";
import { formatClock } from "../lib/time";
import { type ItemKind, useStore } from "../state";

type Mode = Exclude<ItemKind, "link">;

const URL_RE = /^(https?:\/\/|www\.)\S+$/i;

export function CaptureSheet() {
  const s = useStore();
  const [mode, setMode] = useState<Mode>("text");
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [audio, setAudio] = useState<{ blob: Blob; sec: number } | null>(null);
  const [recording, setRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [micError, setMicError] = useState(false);
  const recorder = useRef<MediaRecorder | null>(null);
  const textRef = useRef<HTMLTextAreaElement>(null);
  const preview = useMemo(() => {
    const blob = audio?.blob ?? (mode === "photo" ? file : null);
    return blob ? URL.createObjectURL(blob) : null;
  }, [audio, file, mode]);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);
  const close = () => {
    recorder.current?.state === "recording" && recorder.current.stop();
    s.setCaptureOpen(false);
  };

  useEffect(() => {
    if (mode === "text") textRef.current?.focus();
  }, [mode]);

  useEffect(() => {
    if (!recording) return;
    const started = Date.now();
    const id = window.setInterval(() => setElapsed((Date.now() - started) / 1000), 250);
    return () => window.clearInterval(id);
  }, [recording]);

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const rec = new MediaRecorder(stream);
      const chunks: Blob[] = [];
      const started = Date.now();
      rec.ondataavailable = (e) => chunks.push(e.data);
      rec.onstop = () => {
        stream.getTracks().forEach((tr) => tr.stop());
        setAudio({ blob: new Blob(chunks, { type: rec.mimeType }), sec: Math.round((Date.now() - started) / 1000) });
        setRecording(false);
      };
      recorder.current = rec;
      rec.start();
      setAudio(null);
      setElapsed(0);
      setRecording(true);
    } catch {
      setMicError(true);
    }
  };

  const canSave = mode === "text" ? text.trim().length > 0 : mode === "voice" ? !!audio : !!file;

  const save = async () => {
    const trimmed = text.trim();
    const [first, ...restLines] = trimmed.split("\n");
    if (mode === "text") {
      if (URL_RE.test(first) && restLines.length === 0) {
        const url = first.startsWith("http") ? first : `https://${first}`;
        let title = url;
        try {
          title = new URL(url).hostname.replace(/^www\./, "");
        } catch {
          /* keep raw url */
        }
        s.addItem({ kind: "link", title, body: "", url });
      } else {
        s.addItem({ kind: "text", title: first.slice(0, 80), body: restLines.join("\n").trim() || (first.length > 80 ? first : "") });
      }
    } else {
      const blobId = uid();
      const blob = mode === "voice" ? audio!.blob : file!;
      await putBlob(blobId, blob).catch(() => undefined);
      s.addItem({
        kind: mode,
        title: first || (mode === "voice" ? s.t("voiceNote") : file!.name),
        body: restLines.join("\n"),
        blobId,
        fileName: file?.name,
        durationSec: audio?.sec,
      });
    }
    s.setCaptureOpen(false);
    s.showToast(s.t("savedToInbox"));
  };

  const types: [Mode, string][] = [
    ["text", s.t("text")],
    ["voice", s.t("voice")],
    ["photo", s.t("photo")],
    ["file", s.t("file")],
  ];

  return (
    <Sheet onClose={close} label={s.t("captureSomething")}>
      <div className="title-row">
        <h2 className="t-h2" style={{ margin: 0 }}>{s.t("captureSomething")}</h2>
        <IconButton icon={X} label={s.t("closeCapture")} onClick={close} />
      </div>
      <p className="t-small c-secondary">{s.t("captureSubtitle")}</p>
      <div className="capture-types" role="tablist">
        {types.map(([key, label]) => {
          const Icon = KIND_ICON[key];
          return (
            <button
              key={key}
              role="tab"
              aria-selected={mode === key}
              className={`capture-type${mode === key ? " active" : ""}`}
              onClick={() => setMode(key)}
            >
              <Icon size={18} strokeWidth={ICON_STROKE} />
              {label}
            </button>
          );
        })}
      </div>

      <div className="card capture-field">
        {mode === "voice" && (
          <div className="stack-8" style={{ alignItems: "flex-start" }}>
            <Button variant={recording ? "primary" : "secondary"} onClick={() => (recording ? recorder.current?.stop() : startRecording())}>
              {recording ? <Square size={18} strokeWidth={2} /> : <Mic size={20} strokeWidth={ICON_STROKE} />}
              {recording ? s.t("stopRecording") : s.t("startRecording")}
            </Button>
            {recording && <span className="t-micro c-primary">{s.t("recording", { time: s.n(formatClock(elapsed)) })}</span>}
            {audio && !recording && (
              <>
                <span className="t-micro c-primary">{s.t("recorded", { time: s.n(formatClock(audio.sec)) })}</span>
                <audio controls src={preview ?? undefined} style={{ width: "100%" }} />
              </>
            )}
            {micError && <span className="t-micro" style={{ color: "var(--color-warningText)" }}>{s.t("micUnavailable")}</span>}
          </div>
        )}
        {(mode === "photo" || mode === "file") && (
          <label className="btn btn-secondary" style={{ cursor: "pointer" }}>
            <Upload size={20} strokeWidth={ICON_STROKE} />
            <span className="truncate">{file ? file.name : mode === "photo" ? s.t("choosePhoto") : s.t("chooseFile")}</span>
            <input
              type="file"
              hidden
              accept={mode === "photo" ? "image/*" : undefined}
              capture={mode === "photo" ? "environment" : undefined}
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </label>
        )}
        {mode === "photo" && file && preview && (
          <img src={preview} alt="" style={{ maxHeight: 120, borderRadius: 12, objectFit: "cover" }} />
        )}
        <textarea
          ref={textRef}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={mode === "text" ? s.t("capturePlaceholder") : s.t("addCaption")}
          aria-label={s.t("captureSomething")}
          style={mode === "text" ? undefined : { minHeight: 48 }}
          onKeyDown={(e) => {
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey) && canSave) save();
          }}
        />
      </div>

      <div className="helper">
        <CloudCheck size={18} strokeWidth={ICON_STROKE} />
        <span className="t-micro">{s.t("worksOffline")}</span>
      </div>
      <Button onClick={save} disabled={!canSave}>{s.t("saveToInbox")}</Button>
      <p className="t-micro c-secondary">{s.t("captureFooter")}</p>
    </Sheet>
  );
}
