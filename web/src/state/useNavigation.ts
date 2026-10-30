import { useState } from "react";
import type { Route, Tab } from "./types";

const HOME: Route[] = [{ name: "today" }];

/** A back stack of screens; switching tabs starts a fresh stack. */
export function useNavigation() {
  const [stack, setStack] = useState<Route[]>(HOME);
  return {
    stack,
    route: stack[stack.length - 1],
    go: (r: Route) => setStack((s) => [...s, r]),
    replace: (r: Route) => setStack((s) => [...s.slice(0, -1), r]),
    back: () => setStack((s) => (s.length > 1 ? s.slice(0, -1) : s)),
    tab: (name: Tab) => setStack([{ name }]),
    home: () => setStack(HOME),
  };
}
