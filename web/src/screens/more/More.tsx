import { CalendarDays, ChevronRight, FolderOpen, ListChecks, type LucideIcon, Moon, Settings2 } from "lucide-react";
import { Fab, ICON_STROKE, PageHeading } from "../../components";
import { useStore } from "../../state";

function Row({ icon: Icon, title, subtitle, onClick }: { icon: LucideIcon; title: string; subtitle: string; onClick: () => void }) {
  return (
    <button className="card list-row" onClick={onClick}>
      <span className="type-icon">
        <Icon size={22} strokeWidth={ICON_STROKE} />
      </span>
      <span style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
        <span className="t-body">{title}</span>
        <span className="t-micro c-secondary truncate">{subtitle}</span>
      </span>
      <ChevronRight size={20} strokeWidth={ICON_STROKE} className="chev flip-rtl" />
    </button>
  );
}

export function More() {
  const s = useStore();
  const open = s.data.tasks.filter((x) => !x.done).length;
  return (
    <div className="screen">
      <div className="content">
        <PageHeading title={s.t("more")} subtitle={s.t("moreSubtitle")} search={false} />
        <section className="stack-8">
          <span className="t-micro c-secondary section-label">{s.t("yourSpace")}</span>
          <Row icon={ListChecks} title={s.t("tasks")} subtitle={s.t("openTasksSub", { n: open })} onClick={() => s.go({ name: "tasks" })} />
          <Row icon={FolderOpen} title={s.t("collections")} subtitle={s.t("collectionsSub")} onClick={() => s.go({ name: "collections" })} />
        </section>
        <section className="stack-8">
          <span className="t-micro c-secondary section-label">{s.t("settings")}</span>
          <Row icon={Moon} title={s.t("prayerTimes")} subtitle={s.t("prayerSub", { state: s.data.protectPrayers ? s.t("on") : s.t("off") })} onClick={() => s.go({ name: "prayers" })} />
          <Row icon={CalendarDays} title={s.t("googleCalendar")} subtitle={s.t("googleSub", { state: s.data.showGoogle ? s.t("shown") : s.t("hidden") })} onClick={() => s.go({ name: "google" })} />
          <Row icon={Settings2} title={s.t("preferences")} subtitle={s.t("preferencesSub")} onClick={() => s.go({ name: "prefs" })} />
        </section>
        <div className="data-controls">
          <button className="link-btn" onClick={s.exportData}>{s.t("exportData")}</button>
          <button className="link-btn" onClick={() => s.go({ name: "trash" })}>{s.t("trashTitle")}</button>
        </div>
      </div>
      <Fab />
    </div>
  );
}
