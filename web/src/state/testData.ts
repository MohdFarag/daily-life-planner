import type { Data, InboxItem, Task } from "./types";

// Small builders for tests; each fills required fields with neutral defaults.

export const task = (over: Partial<Task> = {}): Task => ({
  id: "t1",
  title: "Task",
  notes: "",
  list: "Work",
  estimate: 30,
  important: false,
  done: false,
  ...over,
});

export const item = (over: Partial<InboxItem> = {}): InboxItem => ({
  id: "i1",
  kind: "text",
  title: "Item",
  body: "",
  createdAt: 0,
  status: "inbox",
  ...over,
});

export const data = (over: Partial<Data> = {}): Data => ({
  tasks: [],
  inbox: [],
  events: [],
  focus: null,
  lang: "en",
  protectPrayers: true,
  showGoogle: true,
  ...over,
});
