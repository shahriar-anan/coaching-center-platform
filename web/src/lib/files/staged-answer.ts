/**
 * Keeps the script a student has chosen but not yet sent, in IndexedDB, keyed by exam. A phone can reload the tab while the
 * file picker or a scanner app is open; without this the chosen file is lost. Every call swallows its errors: in a private
 * window or with site data blocked the file simply stays in memory, as before.
 */

const DB_NAME = "exam-answer-staging";
const STORE = "staged";

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function run<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return openDatabase().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const transaction = db.transaction(STORE, mode);
        const request = action(transaction.objectStore(STORE));
        transaction.oncomplete = () => {
          db.close();
          resolve(request.result);
        };
        transaction.onerror = () => {
          db.close();
          reject(transaction.error);
        };
        transaction.onabort = () => {
          db.close();
          reject(transaction.error);
        };
      }),
  );
}

export async function saveStagedAnswer(examId: string, file: File): Promise<void> {
  try {
    await run("readwrite", (store) => store.put(file, examId));
  } catch {
    /* No storage: the file is kept in memory only. */
  }
}

export async function loadStagedAnswer(examId: string): Promise<File | null> {
  try {
    const value: unknown = await run("readonly", (store) => store.get(examId));
    return value instanceof File ? value : null;
  } catch {
    return null;
  }
}

export async function clearStagedAnswer(examId: string): Promise<void> {
  try {
    await run("readwrite", (store) => store.delete(examId));
  } catch {
    /* Nothing to clear. */
  }
}
