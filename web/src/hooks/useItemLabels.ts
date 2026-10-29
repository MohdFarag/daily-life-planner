/** Labels shown for inbox items: kind names, relative capture times, subtitles. */
import { locale } from "../i18n";
import { type InboxItem, type ItemKind, useStore } from "../state";

export function useKindLabel() {
  const { t } = useStore();
  return (kind: ItemKind) =>
    ({ text: t("quickNote"), link: t("link"), voice: t("voiceNote"), photo: t("photo"), file: t("file") })[kind];
}

export function useRelativeTime() {
  const { t, lang } = useStore();
  return (ts: number) => {
    const diff = Date.now() - ts;
    const min = Math.floor(diff / 60_000);
    if (min < 1) return t("justNow");
    if (min < 60) return t("minutesAgo", { n: min });
    const h = Math.floor(min / 60);
    if (h === 1) return t("hourAgo");
    if (h < 24) return t("hoursAgo", { n: h });
    const d = Math.floor(h / 24);
    if (d === 1) return t("yesterday");
    if (d < 7) return t("daysAgo", { n: d });
    return new Intl.DateTimeFormat(locale(lang), { day: "numeric", month: "short" }).format(ts);
  };
}

export function itemSubtitle(item: InboxItem, kindLabel: string, when: string, sharedFrom: (o: string) => string) {
  return item.origin ? `${sharedFrom(item.origin)} · ${when}` : `${kindLabel} · ${when}`;
}
