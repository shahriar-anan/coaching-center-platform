import { useEffect } from "react";

/**
 * Warns before the tab closes or reloads while the form is dirty.
 * In-app link confirmation (the ConfirmDialog from F2) is not wired here yet.
 */
export function useUnsavedChangesGuard(dirty: boolean, message?: string): void {
  useEffect(() => {
    if (!dirty) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      // Browsers show their own text; the message is kept for older engines.
      event.returnValue = message ?? "";
      return event.returnValue;
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [dirty, message]);
}
