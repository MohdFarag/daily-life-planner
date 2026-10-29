import { Moon } from "lucide-react";
import { ICON_STROKE, SubScreen, Toggle } from "../../components";
import { PRAYERS, prayerWindow, timeRange, useStore } from "../../state";

export function PrayersScreen() {
  const s = useStore();
  return (
    <SubScreen title={s.t("prayerTimes")}>
      <div className="card" style={{ padding: 16, display: "flex", alignItems: "center", gap: 12 }}>
        <span style={{ flex: 1 }}>
          <span className="t-body" style={{ display: "block" }}>{s.t("protectPrayers")}</span>
          <span className="t-micro c-secondary">{s.t("protectPrayersHint")}</span>
        </span>
        <Toggle on={s.data.protectPrayers} label={s.t("protectPrayers")} onChange={(v) => s.update((d) => ({ ...d, protectPrayers: v }))} />
      </div>
      <div className="stack-8">
        {PRAYERS.map((p) => (
          <div key={p.key} className="prayer-row">
            <Moon size={24} strokeWidth={ICON_STROKE} />
            <span className="t-label" style={{ flex: 1 }}>{s.prayerName(p.key)}</span>
            <span className="t-micro c-secondary">{s.n(timeRange(prayerWindow(p).start, prayerWindow(p).end))}</span>
          </div>
        ))}
      </div>
      <p className="t-micro c-secondary">{s.t("prayerDisclaimer")}</p>
    </SubScreen>
  );
}
