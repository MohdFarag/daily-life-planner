import { CloudCheck, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Button, ICON_STROKE, IconButton, Sheet } from "../../components";
import { useVoiceRecorder } from "../../hooks/useVoiceRecorder";
import { putBlob } from "../../lib/blobs";
import { uid } from "../../lib/ids";
import { captureFromText, splitCaption, useStore } from "../../state";
import { type CaptureMode, CaptureTypeTabs } from "./CaptureTypeTabs";
import { FilePicker } from "./FilePicker";
import { VoiceRecorder } from "./VoiceRecorder";

/** Quick capture: typed text, a voice note, a photo or a file, saved straight to the inbox. */
export function CaptureSheet() {
  const s = useStore();
  const [mode, setMode] = useState<CaptureMode>("text");
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const voice = useVoiceRecorder();
  const textRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (mode === "text") textRef.current?.focus();
  }, [mode]);

  const close = () => {
    voice.stop();
    s.setCaptureOpen(false);
  };

  const canSave = mode === "text" ? text.trim().length > 0 : mode === "voice" ? !!voice.result : !!file;

  const save = async () => {
    if (mode === "text") {
      s.addItem(captureFromText(text));
    } else {
      const blob = mode === "voice" ? voice.result!.blob : file!;
      const blobId = uid();
      await putBlob(blobId, blob).catch(() => undefined);
      const { first, rest } = splitCaption(text);
      s.addItem({
        kind: mode,
        title: first || (mode === "voice" ? s.t("voiceNote") : file!.name),
        body: rest,
        blobId,
        fileName: file?.name,
        durationSec: voice.result?.sec,
      });
    }
    s.setCaptureOpen(false);
    s.showToast(s.t("savedToInbox"));
  };

  return (
    <Sheet onClose={close} label={s.t("captureSomething")}>
      <div className="title-row">
        <h2 className="t-h2" style={{ margin: 0 }}>{s.t("captureSomething")}</h2>
        <IconButton icon={X} label={s.t("closeCapture")} onClick={close} />
      </div>
      <p className="t-small c-secondary">{s.t("captureSubtitle")}</p>
      <CaptureTypeTabs mode={mode} onChange={setMode} />

      <div className="card capture-field">
        {mode === "voice" && <VoiceRecorder voice={voice} />}
        {(mode === "photo" || mode === "file") && <FilePicker photo={mode === "photo"} file={file} onChange={setFile} />}
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
