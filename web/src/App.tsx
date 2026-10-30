import { useEffect } from "react";
import { BottomNav, StatusBar } from "./components";
import { CaptureSheet } from "./screens/capture/CaptureSheet";
import { Focus } from "./screens/focus/Focus";
import { Inbox } from "./screens/inbox/Inbox";
import { CollectionsScreen } from "./screens/more/CollectionsScreen";
import { GoogleScreen } from "./screens/more/GoogleScreen";
import { More } from "./screens/more/More";
import { PrayersScreen } from "./screens/more/PrayersScreen";
import { PrefsScreen } from "./screens/more/PrefsScreen";
import { SearchScreen } from "./screens/more/SearchScreen";
import { TasksScreen } from "./screens/more/TasksScreen";
import { TrashScreen } from "./screens/more/TrashScreen";
import { Plan } from "./screens/plan/Plan";
import { Process } from "./screens/process/Process";
import { ScheduleSheet } from "./screens/schedule/ScheduleSheet";
import { TaskDetail } from "./screens/task/TaskDetail";
import { Today } from "./screens/today/Today";
import { type Tab, useStore } from "./state";

const TABS: Tab[] = ["today", "inbox", "plan", "more"];

/** The phone frame: status bar, the current screen, bottom navigation, sheets and toasts. */
export function App() {
  const s = useStore();
  const { route } = s;

  useEffect(() => {
    document.documentElement.lang = s.lang;
    document.documentElement.dir = s.lang === "ar" ? "rtl" : "ltr";
  }, [s.lang]);

  const isTab = (TABS as string[]).includes(route.name);
  const screen = (() => {
    switch (route.name) {
      case "today":
        return <Today />;
      case "inbox":
        return <Inbox />;
      case "plan":
        return <Plan />;
      case "more":
        return <More />;
      case "process":
        return <Process key={route.itemId ?? "first"} itemId={route.itemId} />;
      case "task":
        return <TaskDetail key={route.id} id={route.id} fresh={route.fresh} />;
      case "focus":
        return <Focus />;
      case "search":
        return <SearchScreen />;
      case "tasks":
        return <TasksScreen />;
      case "collections":
        return <CollectionsScreen />;
      case "prayers":
        return <PrayersScreen />;
      case "google":
        return <GoogleScreen />;
      case "prefs":
        return <PrefsScreen />;
      case "trash":
        return <TrashScreen />;
    }
  })();

  // Sub-screens opened from a tab keep that tab's nav visible.
  const owningTab = (s.stack[0].name as Tab) ?? "today";
  const showNav = isTab || ["search", "tasks", "collections", "prayers", "google", "prefs", "trash"].includes(route.name);

  return (
    <div className="shell">
      <main className="device" dir={s.lang === "ar" ? "rtl" : "ltr"}>
        <StatusBar />
        {screen}
        {showNav && <BottomNav active={owningTab} />}
        {s.captureOpen && <CaptureSheet />}
        {s.scheduleTaskId && <ScheduleSheet key={s.scheduleTaskId} taskId={s.scheduleTaskId} />}
        {s.toast && (
          <div className="toast" role="status" style={showNav ? undefined : { bottom: 140 }}>
            <span className="t-label">{s.toast.msg}</span>
            {s.toast.undo && <button onClick={s.toast.undo}>{s.t("undo")}</button>}
          </div>
        )}
      </main>
    </div>
  );
}
