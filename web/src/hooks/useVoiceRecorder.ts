import { useEffect, useRef, useState } from "react";

export interface Recording {
  blob: Blob;
  sec: number;
}

/** Microphone recording with a live elapsed counter. [error] is set when the mic is unavailable. */
export function useVoiceRecorder() {
  const [recording, setRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [result, setResult] = useState<Recording | null>(null);
  const [error, setError] = useState(false);
  const recorder = useRef<MediaRecorder | null>(null);

  useEffect(() => {
    if (!recording) return;
    const started = Date.now();
    const id = window.setInterval(() => setElapsed((Date.now() - started) / 1000), 250);
    return () => window.clearInterval(id);
  }, [recording]);

  const start = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const rec = new MediaRecorder(stream);
      const chunks: Blob[] = [];
      const started = Date.now();
      rec.ondataavailable = (e) => chunks.push(e.data);
      rec.onstop = () => {
        stream.getTracks().forEach((track) => track.stop());
        setResult({ blob: new Blob(chunks, { type: rec.mimeType }), sec: Math.round((Date.now() - started) / 1000) });
        setRecording(false);
      };
      recorder.current = rec;
      rec.start();
      setResult(null);
      setElapsed(0);
      setRecording(true);
    } catch {
      setError(true);
    }
  };

  const stop = () => {
    if (recorder.current?.state === "recording") recorder.current.stop();
  };

  return { recording, elapsed, result, error, start, stop };
}
