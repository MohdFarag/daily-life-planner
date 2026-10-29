import { useCallback } from "react";
import { dictionaries, localizeDigits, translate, type Lang, type StringKey } from "../i18n";
import { formatDuration } from "../lib/time";
import type { CalendarEvent, ListName, Task } from "./types";

/** Language-aware text helpers for the current [lang]. */
export function useFormatters(lang: Lang) {
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
    if (lang !== "ar") return formatDuration(min);
    const h = Math.floor(min / 60);
    const m = min % 60;
    return localizeDigits([h ? `${h} س` : "", m ? `${m} د` : ""].filter(Boolean).join(" "), lang);
  };
  const taskTitle = (task: Task) => (lang === "ar" && task.titleAr) || task.title;
  const taskNotes = (task: Task) => (lang === "ar" && task.notesAr) || task.notes;
  const eventTitle = (event: CalendarEvent) => (lang === "ar" && event.titleAr) || event.title;
  const listName = (l: ListName) => t(l === "Work" ? "work" : "personal");
  const prayerName = (key: string) => dictionaries[lang].prayerNames[key];
  return { t, n, dur, taskTitle, taskNotes, eventTitle, listName, prayerName };
}
