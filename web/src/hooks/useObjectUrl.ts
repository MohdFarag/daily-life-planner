import { useEffect, useMemo } from "react";

/** An object URL for an in-memory blob, revoked when the blob changes or the component unmounts. */
export function useObjectUrl(blob: Blob | null | undefined) {
  const url = useMemo(() => (blob ? URL.createObjectURL(blob) : null), [blob]);
  useEffect(() => () => {
    if (url) URL.revokeObjectURL(url);
  }, [url]);
  return url;
}
