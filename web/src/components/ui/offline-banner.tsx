"use client";

import { useSyncExternalStore } from "react";
import { useLocale } from "@/i18n/locale-context";

function subscribe(onChange: () => void) {
  window.addEventListener("online", onChange);
  window.addEventListener("offline", onChange);
  return () => {
    window.removeEventListener("online", onChange);
    window.removeEventListener("offline", onChange);
  };
}

/** Shown while `navigator.onLine` is false. Renders nothing otherwise. */
export function OfflineBanner() {
  const { t } = useLocale();
  const online = useSyncExternalStore(
    subscribe,
    () => navigator.onLine,
    () => true,
  );
  if (online) {
    return null;
  }
  return (
    <div
      role="status"
      className="bg-warn/15 px-4 py-2 text-center text-sm text-warn"
    >
      {t("common.states.offline")}
    </div>
  );
}
