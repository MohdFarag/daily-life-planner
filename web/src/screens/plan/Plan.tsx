import { useState } from "react";
import { Fab, PageHeading } from "../../components";
import { useTicker } from "../../hooks/useTicker";
import { todayISO } from "../../lib/time";
import { type Busy, useStore } from "../../state";
import { AgendaList, WeekAgenda } from "./Agenda";
import { DayTimeline } from "./DayTimeline";
import { KIND_COLOR } from "./kinds";
import { UnplannedTasks } from "./UnplannedTasks";
import { type View, ViewSwitch, WeekStrip } from "./WeekStrip";

export function Plan() {
  useTicker(60_000);
  const s = useStore();
  const [selected, setSelected] = useState(todayISO());
  const [view, setView] = useState<View>("day");
  const busy = s.busyOn(selected, { includeDone: true });
  const openBusy = (b: Busy) => b.kind === "task" && s.go({ name: "task", id: b.id });

  return (
    <div className="screen">
      <div className="content">
        <PageHeading title={s.t("plan")} subtitle={s.date(selected, { month: "long", year: "numeric" })} />
        <WeekStrip selected={selected} onSelect={setSelected} />
        <ViewSwitch view={view} onChange={setView} />

        {view === "day" && <DayTimeline date={selected} busy={busy} onOpen={openBusy} />}
        {view === "agenda" && <AgendaList items={busy} onOpen={openBusy} />}
        {view === "week" && (
          <WeekAgenda
            selected={selected}
            onOpen={openBusy}
            onPickDay={(iso) => {
              setSelected(iso);
              setView("day");
            }}
          />
        )}

        <div className="legend t-micro">
          <span>
            <i style={{ background: KIND_COLOR.task }} />
            {s.t("tasks")}
          </span>
          <span>
            <i style={{ background: "var(--color-primary)", opacity: 0.35 }} />
            {s.t("prayers")}
          </span>
          {s.data.showGoogle && (
            <span>
              <i style={{ background: KIND_COLOR.google }} />
              {s.t("google")}
            </span>
          )}
        </div>
        <UnplannedTasks />
      </div>
      <Fab />
    </div>
  );
}
