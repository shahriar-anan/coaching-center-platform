/**
 * Mail links carry the one-time code in the address fragment (`#code=…`). The browser never sends a fragment to a server, so
 * the code stays out of access logs. The pages read it once, then remove it from the address bar.
 */
const CODE = /^[A-Za-z0-9]{4,32}$/;

export function readHashParams(): URLSearchParams {
  if (typeof window === "undefined") {
    return new URLSearchParams();
  }
  return new URLSearchParams(window.location.hash.replace(/^#/, ""));
}

/** The code from `#code=…` or `#c=…`, or null when the link has none or it is not shaped like a code. */
export function readCodeFromHash(): string | null {
  const params = readHashParams();
  const value = (params.get("code") ?? params.get("c") ?? "").trim();
  return CODE.test(value) ? value : null;
}

/** Removes the fragment from the address bar without reloading the page. */
export function scrubHash(): void {
  if (typeof window !== "undefined" && window.location.hash) {
    window.history.replaceState(null, "", window.location.pathname + window.location.search);
  }
}

/** Copies text to the clipboard. Resolves false when the browser refuses, so the page can say so. */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    /* fall through to the older way */
  }
  try {
    const area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand("copy");
    document.body.removeChild(area);
    return ok;
  } catch {
    return false;
  }
}
