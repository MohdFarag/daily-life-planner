import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { deleteBlob } from "./blobs";
import { dictionaries, localizeDigits, translate, type Lang, type StringKey } from "./i18n";
import { addDays, formatDuration, fromMin, nowMin, toMin, todayISO } from "./time";

export type ListName = "Work" | "Personal";
export type ItemKind = "text" | "link" | "voice" | "photo" | "file";
export type ItemStatus = "inbox" | "reference" | "someday" | "waiting" | "trash" | "processed";

export interface Session {
  date: string;
  start: string;
  duration: number;
  afterPrayer?: string;
}

export interface Source {
  kind: ItemKind;
  title: string;
  body: string;
  url?: string;
  createdAt: number;
  blobId?: string;
  fileName?: string;
}

export interface Task {
  id: string;
  title: string;
  titleAr?: string;
  notes: string;
  notesAr?: string;
  list: ListName;
  estimate: number;
  important: boolean;
  done: boolean;
  doneOn?: string;
  deadline?: string;
  session?: Session;
  source?: Source;
}

export interface InboxItem {
  id: string;
  kind: ItemKind;
  title: string;
  body: string;
  url?: string;
  origin?: string;
  createdAt: number;
  status: ItemStatus;
  pendingSync?: boolean;
  blobId?: string;
  fileName?: string;
  durationSec?: number;
}

export interface Focus {
  taskId: string;
  totalSec: number;
  remainingSec: number;
  runningSince: number | null;
}

export interface CalendarEvent {
  id: string;
  title: string;
  titleAr?: string;
  date: string;
  start: string;
  duration: number;
}

interface Data {
  tasks: Task[];
  inbox: InboxItem[];
  events: CalendarEvent[];
  focus: Focus | null;
  lang: Lang;
  protectPrayers: boolean;
  showGoogle: boolean;
}

export type Route =
  | { name: "today" }
  | { name: "inbox" }
  | { name: "plan" }
  | { name: "more" }
  | { name: "process"; itemId?: string }
  | { name: "task"; id: string; fresh?: boolean }
  | { name: "focus" }
  | { name: "search" }
  | { name: "tasks" }
  | { name: "collections" }
  | { name: "prayers" }
  | { name: "google" }
  | { name: "prefs" }
  | { name: "trash" };

export type Tab = "today" | "inbox" | "plan" | "more";
export type TriageAction = "task" | "now" | "reference" | "someday" | "waiting" | "trash" | "split";

// Illustrative prayer times, each protected for 30 minutes.
export const PRAYERS = [
  { key: "fajr", start: "05:12" },
  { key: "dhuhr", start: "12:10" },
  { key: "asr", start: "15:21" },
  { key: "maghrib", start: "17:48" },
  { key: "isha", start: "19:05" },
];
export const PRAYER_MIN = 30;
export const WORK_BLOCK = { start: "09:00", end: "12:00" };
export const WORKDAY_END = "17:00";

const KEY = "daily-life-planner/v1";
export const uid = () =>
  typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : Math.random().toString(36).slice(2);

function seed(): Data {
  const today = todayISO();
  const now = Date.now();
  const H = 3600_000;
  return {
    lang: "en",
    protectPrayers: true,
    showGoogle: true,
    focus: null,
    tasks: [
      {
        id: uid(), title: "Review project brief", titleAr: "مراجعة متطلبات المشروع",
        notes: "Outline the key design decisions.", notesAr: "تلخيص القرارات الأساسية",
        list: "Work", estimate: 45, important: true, done: false,
        session: { date: today, start: "10:00", duration: 45 },
        source: { kind: "text", title: "Review project brief", body: "Review the new project brief and outline the key decisions before the design review.", createdAt: now - 2 * H },
      },
      {
        id: uid(), title: "Read 10 pages", titleAr: "قراءة ١٠ صفحات", notes: "Pick up where you left off.", notesAr: "تابع من حيث توقفت",
        list: "Personal", estimate: 20, important: false, done: false,
        session: { date: today, start: "11:15", duration: 20 },
      },
      {
        id: uid(), title: "Send design update", titleAr: "إرسال تحديث التصميم", notes: "Share the latest screens with the team.",
        list: "Work", estimate: 15, important: false, done: false,
        session: { date: today, start: "13:30", duration: 15 },
      },
      { id: uid(), title: "Morning walk", titleAr: "نزهة الصباح", notes: "", list: "Personal", estimate: 30, important: false, done: true, doneOn: today },
      { id: uid(), title: "Plan the week", titleAr: "تخطيط الأسبوع", notes: "", list: "Work", estimate: 15, important: false, done: true, doneOn: today },
      { id: uid(), title: "Book dentist appointment", titleAr: "حجز موعد طبيب الأسنان", notes: "", list: "Personal", estimate: 10, important: false, done: false },
      {
        id: uid(), title: "Prepare review slides", titleAr: "تحضير شرائح المراجعة", notes: "Summarize decisions from the brief.",
        list: "Work", estimate: 60, important: false, done: false,
        session: { date: addDays(today, 1), start: "09:30", duration: 60 },
      },
    ],
    inbox: [
      { id: uid(), kind: "text", title: "Review project brief", body: "Outline the key design decisions.", createdAt: now - 20_000, status: "inbox" },
      { id: uid(), kind: "link", title: "A reading list for winter", body: "Books, articles, and things to explore.", origin: "Keep", url: "https://example.com/winter-reading", createdAt: now - 2 * H, status: "inbox" },
      { id: uid(), kind: "voice", title: "Ideas from the walk", body: "", createdAt: now - 26 * H, status: "inbox", durationSec: 138 },
      { id: uid(), kind: "text", title: "Call the plumber about the kitchen tap", body: "Ask about Thursday morning.\nGet a quote first.", createdAt: now - 50 * H, status: "inbox" },
      { id: uid(), kind: "link", title: "Lentil soup recipe", body: "Try it this weekend.", url: "https://example.com/lentil-soup", createdAt: now - 74 * H, status: "inbox" },
    ],
    events: [
      { id: uid(), title: "Design review", titleAr: "مراجعة التصميم", date: today, start: "13:00", duration: 30 },
      { id: uid(), title: "Team sync", titleAr: "اجتماع الفريق", date: addDays(today, 1), start: "11:00", duration: 30 },
    ],
  };
}

function load(): Data {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return { ...seed(), ...JSON.parse(raw) };
  } catch {
    /* fall back to sample data */
  }
  return seed();
}

export interface Busy {
  kind: "task" | "prayer" | "google";
  id: string;
  label: string;
  start: number;
  end: number;
  done?: boolean;
}

function useStoreValue() {
  const [data, setData] = useState<Data>(load);
  const [stack, setStack] = useState<Route[]>([{ name: "today" }]);
  const [captureOpen, setCaptureOpen] = useState(false);
  const [scheduleTaskId, setScheduleTaskId] = useState<string | null>(null);
  const [toast, setToast] = useState<{ msg: string; undo?: () => void } | null>(null);
  const [online, setOnline] = useState(typeof navigator === "undefined" ? true : navigator.onLine);
  const lastTriage = useRef<{ itemId: string; taskIds: string[] } | null>(null);
  const toastTimer = useRef<number>();

  useEffect(() => {
    try {
      localStorage.setItem(KEY, JSON.stringify(data));
    } catch {
      /* storage full or unavailable */
    }
  }, [data]);

  useEffect(() => {
    const on = () => {
      setOnline(true);
      setData((d) => ({ ...d, inbox: d.inbox.map((i) => (i.pendingSync ? { ...i, pendingSync: false } : i)) }));
    };
    const off = () => setOnline(false);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
  }, []);

  const lang = data.lang;
  const t = useCallback((key: StringKey, vars?: Record<string, string | number>) => translate(lang, key, vars), [lang]);
  // Times like "10:00–10:45" are isolated left-to-right so they don't flip inside Arabic text.
  const n = useCallback(
    (s: string | number) => {
      const out = localizeDigits(String(s), lang);
      return lang === "ar" && /\d:\d|:/.test(String(s)) ? `\u2066${out}\u2069` : out;
    },
    [lang],
  );
  const dur = (min: number) => {
    const h = Math.floor(min / 60);
    const m = min % 60;
    if (lang !== "ar") return formatDuration(min);
    return localizeDigits([h ? `${h} س` : "", m ? `${m} د` : ""].filter(Boolean).join(" "), lang);
  };
  const taskTitle = (task: Task) => (lang === "ar" && task.titleAr) || task.title;
  const taskNotes = (task: Task) => (lang === "ar" && task.notesAr) || task.notes;
  const listName = (l: ListName) => t(l === "Work" ? "work" : "personal");
  const prayerName = (key: string) => dictionaries[lang].prayerNames[key];

  const route = stack[stack.length - 1];
  const go = (r: Route) => setStack((s) => [...s, r]);
  const replace = (r: Route) => setStack((s) => [...s.slice(0, -1), r]);
  const back = () => setStack((s) => (s.length > 1 ? s.slice(0, -1) : s));
  const tab = (name: Tab) => setStack([{ name }]);

  const showToast = (msg: string, undo?: () => void) => {
    window.clearTimeout(toastTimer.current);
    setToast({ msg, undo });
    toastTimer.current = window.setTimeout(() => setToast(null), 4000);
  };

  const update = (fn: (d: Data) => Data) => setData(fn);
  const patchTask = (id: string, patch: Partial<Task>) =>
    update((d) => ({ ...d, tasks: d.tasks.map((x) => (x.id === id ? { ...x, ...patch } : x)) }));
  const patchItem = (id: string, patch: Partial<InboxItem>) =>
    update((d) => ({ ...d, inbox: d.inbox.map((x) => (x.id === id ? { ...x, ...patch } : x)) }));

  const addTask = (task: Omit<Task, "id">) => {
    const id = uid();
    update((d) => ({ ...d, tasks: [...d.tasks, { ...task, id }] }));
    return id;
  };

  const toggleDone = (id: string) =>
    update((d) => ({
      ...d,
      tasks: d.tasks.map((x) => (x.id === id ? { ...x, done: !x.done, doneOn: !x.done ? todayISO() : undefined } : x)),
      focus: d.focus?.taskId === id ? null : d.focus,
    }));

  const deleteTask = (id: string) =>
    update((d) => ({ ...d, tasks: d.tasks.filter((x) => x.id !== id), focus: d.focus?.taskId === id ? null : d.focus }));

  const addItem = (item: Omit<InboxItem, "id" | "createdAt" | "status" | "pendingSync">) =>
    update((d) => ({
      ...d,
      inbox: [{ ...item, id: uid(), createdAt: Date.now(), status: "inbox", pendingSync: !navigator.onLine }, ...d.inbox],
    }));

  const deleteItem = (id: string) => {
    const item = data.inbox.find((i) => i.id === id);
    if (item?.blobId) deleteBlob(item.blobId);
    update((d) => ({ ...d, inbox: d.inbox.filter((i) => i.id !== id) }));
  };

  const startFocus = (taskId: string) => {
    const task = data.tasks.find((x) => x.id === taskId);
    const totalSec = (task?.session?.duration ?? task?.estimate ?? 25) * 60;
    update((d) => ({ ...d, focus: { taskId, totalSec, remainingSec: totalSec, runningSince: Date.now() } }));
  };
  const remaining = (f: Focus) => (f.runningSince ? f.remainingSec - (Date.now() - f.runningSince) / 1000 : f.remainingSec);
  const pauseFocus = () =>
    update((d) => (d.focus ? { ...d, focus: { ...d.focus, remainingSec: Math.max(0, remaining(d.focus)), runningSince: null } } : d));
  const resumeFocus = () => update((d) => (d.focus ? { ...d, focus: { ...d.focus, runningSince: Date.now() } } : d));
  const extendFocus = (sec: number) =>
    update((d) =>
      d.focus
        ? { ...d, focus: { ...d.focus, totalSec: d.focus.totalSec + sec, remainingSec: Math.max(0, remaining(d.focus)) + sec, runningSince: d.focus.runningSince ? Date.now() : null } }
        : d,
    );
  const endFocus = () => update((d) => ({ ...d, focus: null }));

  const sourceOf = (item: InboxItem): Source => ({
    kind: item.kind, title: item.title, body: item.body, url: item.url, createdAt: item.createdAt, blobId: item.blobId, fileName: item.fileName,
  });
  const taskFromItem = (item: InboxItem, title = item.title): Task => ({
    id: uid(), title, notes: item.kind === "link" ? item.url ?? "" : item.body.split("\n")[0] ?? "",
    list: "Work", estimate: 30, important: false, done: false, source: sourceOf(item),
  });

  /** Applies a triage decision and returns ids of tasks it created. */
  const triage = (itemId: string, action: TriageAction) => {
    const item = data.inbox.find((i) => i.id === itemId);
    if (!item) return [];
    let created: Task[] = [];
    let status: ItemStatus = "processed";
    if (action === "task" || action === "now") created = [taskFromItem(item)];
    else if (action === "split") {
      const parts = [item.title, ...item.body.split("\n")].map((s) => s.trim()).filter(Boolean);
      created = parts.map((p) => taskFromItem(item, p));
    } else status = action;
    update((d) => ({
      ...d,
      tasks: [...d.tasks, ...created],
      inbox: d.inbox.map((i) => (i.id === itemId ? { ...i, status } : i)),
    }));
    lastTriage.current = { itemId, taskIds: created.map((c) => c.id) };
    return created.map((c) => c.id);
  };

  const undoTriage = () => {
    const last = lastTriage.current;
    if (!last) return null;
    update((d) => ({
      ...d,
      tasks: d.tasks.filter((x) => !last.taskIds.includes(x.id)),
      inbox: d.inbox.map((i) => (i.id === last.itemId ? { ...i, status: "inbox" } : i)),
      focus: d.focus && last.taskIds.includes(d.focus.taskId) ? null : d.focus,
    }));
    lastTriage.current = null;
    return last.itemId;
  };

  /** Everything occupying time on a date, sorted by start. */
  const busyOn = (date: string, opts: { includeDone?: boolean } = {}): Busy[] => {
    const out: Busy[] = [];
    for (const task of data.tasks) {
      if (task.session?.date !== date || (task.done && !opts.includeDone)) continue;
      const start = toMin(task.session.start);
      out.push({ kind: "task", id: task.id, label: taskTitle(task), start, end: start + task.session.duration, done: task.done });
    }
    for (const p of PRAYERS) {
      const start = toMin(p.start);
      out.push({ kind: "prayer", id: p.key, label: t("prayer", { name: prayerName(p.key) }), start, end: start + PRAYER_MIN });
    }
    if (data.showGoogle)
      for (const e of data.events) {
        if (e.date !== date) continue;
        const start = toMin(e.start);
        out.push({ kind: "google", id: e.id, label: (lang === "ar" && e.titleAr) || e.title, start, end: start + e.duration });
      }
    return out.sort((a, b) => a.start - b.start);
  };

  const today = todayISO();
  const todaysTasks = data.tasks.filter((x) => x.session?.date === today || x.doneOn === today);

  /** Minutes left in the workday that aren't already committed. */
  const freeMinutesToday = () => {
    const from = Math.max(nowMin(), toMin("09:00"));
    const to = toMin(WORKDAY_END);
    if (from >= to) return 0;
    const committed = busyOn(today)
      .map((b) => Math.max(0, Math.min(b.end, to) - Math.max(b.start, from)))
      .reduce((a, b) => a + b, 0);
    return Math.max(0, to - from - committed);
  };

  const exportData = () => {
    const blob = new Blob([JSON.stringify({ exportedAt: new Date().toISOString(), ...data }, null, 2)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = `daily-life-planner-${today}.json`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  };

  const reset = () => {
    setData({ ...seed(), lang: data.lang });
    setStack([{ name: "today" }]);
  };

  const sessionLabel = (s: Session) => `${s.start}–${fromMin(toMin(s.start) + s.duration)}`;

  return {
    data, update, lang, t, n, dur, taskTitle, taskNotes, listName, prayerName,
    route, stack, go, replace, back, tab,
    captureOpen, setCaptureOpen, scheduleTaskId, setScheduleTaskId,
    toast, showToast, setToast, online,
    patchTask, patchItem, addTask, toggleDone, deleteTask, addItem, deleteItem,
    startFocus, pauseFocus, resumeFocus, extendFocus, endFocus, remaining,
    triage, undoTriage, canUndo: () => lastTriage.current !== null,
    busyOn, todaysTasks, freeMinutesToday, exportData, reset, sessionLabel,
  };
}

export type Store = ReturnType<typeof useStoreValue>;
const Ctx = createContext<Store | null>(null);

export function StoreProvider({ children }: { children: ReactNode }) {
  const value = useStoreValue();
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useStore() {
  const s = useContext(Ctx);
  if (!s) throw new Error("useStore outside provider");
  return s;
}

/** Re-renders every `ms` while `active`. */
export function useTicker(ms: number, active = true) {
  const [, set] = useState(0);
  useEffect(() => {
    if (!active) return;
    const id = window.setInterval(() => set((x) => x + 1), ms);
    return () => window.clearInterval(id);
  }, [ms, active]);
}

