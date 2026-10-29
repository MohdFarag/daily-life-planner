// Tiny IndexedDB wrapper for captured audio, photos and files (too large for localStorage).
const DB = "daily-life-planner";
const STORE = "blobs";

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function run<T>(mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const req = fn(db.transaction(STORE, mode).objectStore(STORE));
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export const putBlob = (id: string, blob: Blob) => run("readwrite", (s) => s.put(blob, id));
export const getBlob = (id: string) => run<Blob | undefined>("readonly", (s) => s.get(id));
export const deleteBlob = (id: string) => run("readwrite", (s) => s.delete(id)).catch(() => undefined);
