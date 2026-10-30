import { useEffect, useState } from "react";

/** Re-renders every `ms` while `active`. */
export function useTicker(ms: number, active = true) {
  const [, set] = useState(0);
  useEffect(() => {
    if (!active) return;
    const id = window.setInterval(() => set((x) => x + 1), ms);
    return () => window.clearInterval(id);
  }, [ms, active]);
}
