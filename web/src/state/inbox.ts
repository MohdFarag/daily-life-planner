import type { Data, InboxItem } from "./types";

/** Items still waiting to be processed, newest first (the order they are stored in). */
export const inboxQueue = (data: Data): InboxItem[] => data.inbox.filter((i) => i.status === "inbox");

export const isLink = (item: InboxItem) => item.kind === "link";
