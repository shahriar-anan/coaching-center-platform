"use client";

import { useEffect } from "react";

/**
 * Warns before the tab is closed or reloaded while a form has changes. In-app
 * links use `window.confirm` through `confirmLeave` on the screen itself.
 */
export function useUnsavedWarning(dirty: boolean) {
  useEffect(() => {
    if (!dirty) {
      return;
    }
    function onBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault();
      event.returnValue = "";
    }
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [dirty]);
}
