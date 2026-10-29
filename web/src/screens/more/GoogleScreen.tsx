import { SubScreen, Toggle } from "../../components";
import { useStore } from "../../state";

export function GoogleScreen() {
  const s = useStore();
  return (
    <SubScreen title={s.t("googleCalendar")}>
      <div className="card" style={{ padding: 16, display: "flex", alignItems: "center", gap: 12 }}>
        <span className="t-body" style={{ flex: 1 }}>{s.t("showGoogle")}</span>
        <Toggle on={s.data.showGoogle} label={s.t("showGoogle")} onChange={(v) => s.update((d) => ({ ...d, showGoogle: v }))} />
      </div>
      <p className="t-micro c-secondary">{s.t("googleDisclaimer")}</p>
    </SubScreen>
  );
}
