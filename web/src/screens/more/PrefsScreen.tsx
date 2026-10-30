import { RotateCcw } from "lucide-react";
import { Button, ICON_STROKE, SubScreen } from "../../components";
import { useStore } from "../../state";

export function PrefsScreen() {
  const s = useStore();
  return (
    <SubScreen title={s.t("preferences")}>
      <span className="t-micro c-secondary">{s.t("language")}</span>
      <div className="segmented full" role="radiogroup" aria-label={s.t("language")}>
        {(["en", "ar"] as const).map((l) => (
          <button
            key={l}
            role="radio"
            aria-checked={s.lang === l}
            className={s.lang === l ? "active" : ""}
            onClick={() => s.update((d) => ({ ...d, lang: l }))}
          >
            {l === "en" ? "English" : "العربية"}
          </button>
        ))}
      </div>
      <Button variant="secondary" onClick={() => window.confirm(s.t("resetConfirm")) && s.reset()}>
        <RotateCcw size={18} strokeWidth={ICON_STROKE} /> {s.t("resetData")}
      </Button>
    </SubScreen>
  );
}
