import { useEffect, useState } from "react";

/** Tracks connectivity; [onReconnect] runs each time the browser comes back online. */
export function useOnline(onReconnect: () => void) {
  const [online, setOnline] = useState(typeof navigator === "undefined" ? true : navigator.onLine);
  useEffect(() => {
    const on = () => {
      setOnline(true);
      onReconnect();
    };
    const off = () => setOnline(false);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
    // Subscribed once: onReconnect only calls state setters, which are stable.
  }, []);
  return online;
}
