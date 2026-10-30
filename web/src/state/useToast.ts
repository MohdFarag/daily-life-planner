import { useRef, useState } from "react";

const TOAST_MS = 4000;

export interface Toast {
  msg: string;
  undo?: () => void;
}

/** One transient message at a time, optionally with an undo action. */
export function useToast() {
  const [toast, setToast] = useState<Toast | null>(null);
  const timer = useRef<number>();
  const showToast = (msg: string, undo?: () => void) => {
    window.clearTimeout(timer.current);
    setToast({ msg, undo });
    timer.current = window.setTimeout(() => setToast(null), TOAST_MS);
  };
  return { toast, showToast, setToast };
}
