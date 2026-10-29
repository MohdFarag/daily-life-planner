import type { Busy } from "../../state";

/** The colour each kind of busy time is drawn in (prayers use a tint, see `swatch`). */
export const KIND_COLOR: Record<Busy["kind"], string> = {
  task: "var(--color-primary)",
  prayer: "var(--color-highlight)",
  google: "var(--color-warningText)",
};

export const swatch = (kind: Busy["kind"]) => (kind === "prayer" ? "var(--color-tint)" : KIND_COLOR[kind]);
