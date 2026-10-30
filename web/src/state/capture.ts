import type { InboxItem } from "./types";

/** The capture fields a new inbox item is created from. */
export type CaptureFields = Pick<InboxItem, "kind" | "title" | "body" | "url">;

const URL_RE = /^(https?:\/\/|www\.)\S+$/i;
const TITLE_MAX = 80;

/** First line and the rest of a capture's text. */
export function splitCaption(text: string): { first: string; rest: string } {
  const [first = "", ...rest] = text.trim().split("\n");
  return { first, rest: rest.join("\n") };
}

/**
 * Turns typed text into an inbox item: a lone URL becomes a link titled by its host; anything else
 * becomes a note titled by its first line (a long single line keeps its full text as the body).
 */
export function captureFromText(text: string): CaptureFields {
  const { first, rest } = splitCaption(text);
  if (URL_RE.test(first) && !rest) {
    const url = first.startsWith("http") ? first : `https://${first}`;
    return { kind: "link", title: hostOf(url) ?? url, body: "", url };
  }
  return { kind: "text", title: first.slice(0, TITLE_MAX), body: rest.trim() || (first.length > TITLE_MAX ? first : "") };
}

function hostOf(url: string): string | null {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return null;
  }
}
