import { ICON_STROKE, KIND_ICON } from "../../components";
import { type ItemKind, useStore } from "../../state";

/** Links are detected from typed text, so they have no tab of their own. */
export type CaptureMode = Exclude<ItemKind, "link">;

const MODES: CaptureMode[] = ["text", "voice", "photo", "file"];

export function CaptureTypeTabs({ mode, onChange }: { mode: CaptureMode; onChange: (mode: CaptureMode) => void }) {
  const s = useStore();
  return (
    <div className="capture-types" role="tablist">
      {MODES.map((key) => {
        const Icon = KIND_ICON[key];
        return (
          <button
            key={key}
            role="tab"
            aria-selected={mode === key}
            className={`capture-type${mode === key ? " active" : ""}`}
            onClick={() => onChange(key)}
          >
            <Icon size={18} strokeWidth={ICON_STROKE} />
            {s.t(key)}
          </button>
        );
      })}
    </div>
  );
}
