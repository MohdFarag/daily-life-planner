import { StrictMode, useEffect } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource/manrope/400.css";
import "@fontsource/manrope/500.css";
import "@fontsource/manrope/600.css";
import "@fontsource/manrope/700.css";
import "@fontsource/noto-sans-arabic/400.css";
import "@fontsource/noto-sans-arabic/500.css";
import "@fontsource/noto-sans-arabic/600.css";
import "@fontsource/noto-sans-arabic/700.css";
import "./styles.css";
import { BottomNav, StatusBar } from "./components";
import { StoreProvider, useStore, type Tab } from "./store";
import { Today } from "./screens/Today";
import { Inbox } from "./screens/Inbox";
import { CaptureSheet } from "./screens/CaptureSheet";
import { Process } from "./screens/Process";
import { TaskDetail } from "./screens/TaskDetail";
import { Plan } from "./screens/Plan";
import { ScheduleSheet } from "./screens/ScheduleSheet";
import { Focus } from "./screens/Focus";
import { CollectionsScreen, GoogleScreen, More, PrayersScreen, PrefsScreen, SearchScreen, TasksScreen, TrashScreen } from "./screens/More";

const TABS: Tab[] = ["today", "inbox", "plan", "more"];

function App() {
  const s = useStore();
  const { route } = s;

  useEffect(() => {
    document.documentElement.lang = s.lang;
    document.documentElement.dir = s.lang === "ar" ? "rtl" : "ltr";
  }, [s.lang]);

  const isTab = (TABS as string[]).includes(route.name);
  const screen = (() => {
    switch (route.name) {
      case "today": return <Today />;
      case "inbox": return <Inbox />;
      case "plan": return <Plan />;
      case "more": return <More />;
      case "process": return <Process key={route.itemId ?? "first"} itemId={route.itemId} />;
      case "task": return <TaskDetail key={route.id} id={route.id} fresh={route.fresh} />;
      case "focus": return <Focus />;
      case "search": return <SearchScreen />;
      case "tasks": return <TasksScreen />;
      case "collections": return <CollectionsScreen />;
      case "prayers": return <PrayersScreen />;
      case "google": return <GoogleScreen />;
      case "prefs": return <PrefsScreen />;
      case "trash": return <TrashScreen />;
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

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <StoreProvider>
      <App />
    </StoreProvider>
  </StrictMode>,
);
