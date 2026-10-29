import { ar } from "./ar";
import { en, type Dict } from "./en";

export type Lang = "en" | "ar";

export const dictionaries: Record<Lang, Dict> = { en, ar };
export type StringKey = Exclude<keyof Dict, "prayerNames">;

const AR_DIGITS = "٠١٢٣٤٥٦٧٨٩";

export const localizeDigits = (s: string, lang: Lang) =>
  lang === "ar" ? s.replace(/\d/g, (d) => AR_DIGITS[Number(d)]) : s;

export function translate(lang: Lang, key: StringKey, vars?: Record<string, string | number>) {
  let s = dictionaries[lang][key] as string;
  if (vars) for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, String(v));
  return localizeDigits(s, lang);
}

export const locale = (lang: Lang) => (lang === "ar" ? "ar-EG" : "en-GB");
