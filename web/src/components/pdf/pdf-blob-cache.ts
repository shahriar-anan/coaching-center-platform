/**
 * Downloaded PDFs kept in memory for the life of the page, so switching tabs (answer / question / marked) or reopening a
 * dialog does not download a 20 MB script again (22 Nov 2026). Only finished downloads are kept: at most three files and
 * 80 MB, the least recently used going first. Nothing is written to disk or browser storage, and the cache empties when
 * the session ends (see `clearPdfCache`, called when the access token is cleared).
 */

const MAX_ENTRIES = 3;
const MAX_BYTES = 80 * 1024 * 1024;

const entries = new Map<string, Blob>();

export function cachedPdf(key: string): Blob | undefined {
  const blob = entries.get(key);
  if (blob) {
    // Most recently used goes last.
    entries.delete(key);
    entries.set(key, blob);
  }
  return blob;
}

export function rememberPdf(key: string, blob: Blob): void {
  if (blob.size > MAX_BYTES) {
    return;
  }
  entries.delete(key);
  entries.set(key, blob);
  let total = 0;
  for (const value of entries.values()) {
    total += value.size;
  }
  for (const [oldest, value] of entries) {
    if (entries.size <= MAX_ENTRIES && total <= MAX_BYTES) {
      break;
    }
    entries.delete(oldest);
    total -= value.size;
  }
}

export function clearPdfCache(): void {
  entries.clear();
}
