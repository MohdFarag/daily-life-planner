import { useEffect, useState } from "react";
import { getBlob } from "../lib/blobs";

/** An object URL for a stored blob, revoked when the id changes or the component unmounts. */
export function useBlobUrl(blobId?: string) {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    setUrl(null);
    if (!blobId) return;
    let objectUrl: string | null = null;
    getBlob(blobId)
      .then((b) => {
        if (b) setUrl((objectUrl = URL.createObjectURL(b)));
      })
      .catch(() => undefined);
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [blobId]);
  return url;
}
