/** In-memory access token only. Never persist tokens in browser storage. */

import { clearPdfCache } from "@/components/pdf/pdf-blob-cache";

let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
  if (token === null) {
    // Signed out or session lost: downloaded papers and scripts must not outlive the session.
    clearPdfCache();
  }
}
