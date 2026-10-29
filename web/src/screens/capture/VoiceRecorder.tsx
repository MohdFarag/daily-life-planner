import { Mic, Square } from "lucide-react";
import { Button, ICON_STROKE } from "../../components";
import { useObjectUrl } from "../../hooks/useObjectUrl";
import type { useVoiceRecorder } from "../../hooks/useVoiceRecorder";
import { formatClock } from "../../lib/time";
import { useStore } from "../../state";

export function VoiceRecorder({ voice }: { voice: ReturnType<typeof useVoiceRecorder> }) {
  const s = useStore();
  const playback = useObjectUrl(voice.result?.blob);
  return (
    <div className="stack-8" style={{ alignItems: "flex-start" }}>
      <Button variant={voice.recording ? "primary" : "secondary"} onClick={voice.recording ? voice.stop : voice.start}>
        {voice.recording ? <Square size={18} strokeWidth={2} /> : <Mic size={20} strokeWidth={ICON_STROKE} />}
        {voice.recording ? s.t("stopRecording") : s.t("startRecording")}
      </Button>
      {voice.recording && <span className="t-micro c-primary">{s.t("recording", { time: s.n(formatClock(voice.elapsed)) })}</span>}
      {voice.result && !voice.recording && (
        <>
          <span className="t-micro c-primary">{s.t("recorded", { time: s.n(formatClock(voice.result.sec)) })}</span>
          <audio controls src={playback ?? undefined} style={{ width: "100%" }} />
        </>
      )}
      {voice.error && <span className="t-micro" style={{ color: "var(--color-warningText)" }}>{s.t("micUnavailable")}</span>}
    </div>
  );
}
