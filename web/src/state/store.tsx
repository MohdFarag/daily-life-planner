import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { deleteBlob } from "../lib/blobs";
import { downloadJson } from "../lib/download";
import { uid } from "../lib/ids";
import { fromMin, toMin, todayISO } from "../lib/time";
import * as m from "./mutations";
import { loadData, saveData } from "./persistence";
import { busyOn as busyOnDate, freeMinutes } from "./schedule";
import { seed } from "./seed";
import type { Busy, Data, InboxItem, Session, Task, TriageAction } from "./types";
import { useFormatters } from "./useFormatters";
import { useNavigation } from "./useNavigation";
import { useOnline } from "./useOnline";
import { useToast } from "./useToast";

/**
 * The app's state and actions. Data rules live in `mutations` and `schedule`; this hook wires
 * them to React state alongside navigation, toasts, connectivity and language helpers.
 */
function useStoreValue() {
  const [data, setData] = useState<Data>(loadData);
  const [captureOpen, setCaptureOpen] = useState(false);
  const [scheduleTaskId, setScheduleTaskId] = useState<string | null>(null);
  const lastTriage = useRef<{ itemId: string; taskIds: string[] } | null>(null);
  const nav = useNavigation();
  const toasts = useToast();
  const online = useOnline(() => setData(m.markSynced));
  const fmt = useFormatters(data.lang);

  useEffect(() => saveData(data), [data]);

  const update = setData;

  // Actions that only transform the data; created once so their identity is stable across renders.
  const actions = useMemo(
    () => ({
      patchTask: (id: string, patch: Partial<Task>) => setData(m.patchTask(id, patch)),
      patchItem: (id: string, patch: Partial<InboxItem>) => setData(m.patchItem(id, patch)),
      toggleDone: (id: string) => setData(m.toggleDone(id)),
      deleteTask: (id: string) => setData(m.deleteTask(id)),
      addTask: (task: Omit<Task, "id">) => {
        const id = uid();
        setData((d) => ({ ...d, tasks: [...d.tasks, { ...task, id }] }));
        return id;
      },
      addItem: (item: Omit<InboxItem, "id" | "createdAt" | "status" | "pendingSync">) =>
        setData((d) => ({
          ...d,
          inbox: [{ ...item, id: uid(), createdAt: Date.now(), status: "inbox", pendingSync: !navigator.onLine }, ...d.inbox],
        })),
      startFocus: (taskId: string) => setData(m.startFocus(taskId)),
      pauseFocus: () => setData(m.pauseFocus),
      resumeFocus: () => setData(m.resumeFocus),
      extendFocus: (sec: number) => setData(m.extendFocus(sec)),
      endFocus: () => setData(m.endFocus),
    }),
    [],
  );

  const deleteItem = (id: string) => {
    const item = data.inbox.find((i) => i.id === id);
    if (item?.blobId) deleteBlob(item.blobId);
    update((d) => ({ ...d, inbox: d.inbox.filter((i) => i.id !== id) }));
  };

  /** Applies a triage decision and returns ids of tasks it created. */
  const triage = (itemId: string, action: TriageAction) => {
    const item = data.inbox.find((i) => i.id === itemId);
    if (!item) return [];
    const created = m.tasksFor(item, action);
    update(m.applyTriage(itemId, m.statusAfter(action), created));
    lastTriage.current = { itemId, taskIds: created.map((c) => c.id) };
    return created.map((c) => c.id);
  };

  /** Reverts the last triage decision; returns the item it applied to. */
  const undoTriage = () => {
    const last = lastTriage.current;
    if (!last) return null;
    update(m.revertTriage(last.itemId, last.taskIds));
    lastTriage.current = null;
    return last.itemId;
  };

  const busyOn = (date: string, opts: { includeDone?: boolean } = {}): Busy[] =>
    busyOnDate(data, date, {
      task: fmt.taskTitle,
      prayer: (key) => fmt.t("prayer", { name: fmt.prayerName(key) }),
      event: fmt.eventTitle,
    }, opts);

  const today = todayISO();
  const todaysTasks = data.tasks.filter((x) => x.session?.date === today || x.doneOn === today);

  return {
    data, update, lang: data.lang, ...fmt,
    ...nav,
    captureOpen, setCaptureOpen, scheduleTaskId, setScheduleTaskId,
    ...toasts, online,
    ...actions, deleteItem, remaining: m.remaining,
    triage, undoTriage, canUndo: () => lastTriage.current !== null,
    busyOn, todaysTasks,
    freeMinutesToday: () => freeMinutes(busyOn(today)),
    exportData: () => downloadJson(`daily-life-planner-${today}.json`, { exportedAt: new Date().toISOString(), ...data }),
    reset: () => {
      setData({ ...seed(), lang: data.lang });
      nav.home();
    },
    sessionLabel: (s: Session) => `${s.start}–${fromMin(toMin(s.start) + s.duration)}`,
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
