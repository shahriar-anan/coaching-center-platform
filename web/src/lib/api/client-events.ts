import { apiUrl } from "@/lib/config";

/**
 * Anonymous reports of things that fail only in the visitor's browser, so they show in the API log the same day instead
 * of being reconstructed from nginx logs (22 Nov 2026: PDFs never opened on older phones). No user data, file names or
 * paths: the server adds the user agent. At most a few per page load.
 */
export type ClientEvent = {
  kind: "pdf";
  /** download: the file did not arrive. open: pdf.js could not read it. render: a page failed to draw. */
  stage: "download" | "open" | "render";
  /** Which kind of file, never its id: question, answer, marked, routine, other. */
  file: string;
  message: string;
};

const MAX_PER_PAGE = 3;
let sent = 0;

export function reportClientEvent(event: ClientEvent): void {
  if (typeof window === "undefined" || sent >= MAX_PER_PAGE) {
    return;
  }
  sent += 1;
  const body = JSON.stringify({ ...event, message: event.message.slice(0, 300) });
  const url = apiUrl("/api/v1/public/client-events");
  try {
    if (typeof navigator.sendBeacon === "function" && navigator.sendBeacon(url, new Blob([body], { type: "application/json" }))) {
      return;
    }
    void fetch(url, { method: "POST", body, headers: { "Content-Type": "application/json" }, keepalive: true }).catch(() => undefined);
  } catch {
    // Reporting must never break the page.
  }
}

/** The error's own text, for the report. */
export function errorText(error: unknown): string {
  if (error instanceof Error) {
    return `${error.name}: ${error.message}`;
  }
  return String(error);
}
