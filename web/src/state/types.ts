import type { Lang } from "../i18n";

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

export interface Data {
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

/** Something occupying time on a day, in minutes since midnight. */
export interface Busy {
  kind: "task" | "prayer" | "google";
  id: string;
  label: string;
  start: number;
  end: number;
  done?: boolean;
}
