"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { askBeforeLeaving } from "@/lib/hooks/unsaved-changes-store";

/**
 * Stops a dirty form from being left by accident:
 * - closing or reloading the tab (the browser's own prompt);
 * - a same-site link click (captured before the router sees it), shown in the shell's dialog;
 * - browser back (the page is put back and the same dialog is shown).
 * Clean forms are left alone.
 */
export function useUnsavedChangesGuard(dirty: boolean, message?: string): void {
  const router = useRouter();
  const leavingRef = useRef(false);
  const ignorePopRef = useRef(false);

  useEffect(() => {
    if (!dirty) return;

    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = message ?? "";
      return event.returnValue;
    };

    const onClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0) return;
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const target = event.target instanceof Element ? event.target.closest("a[href]") : null;
      if (!(target instanceof HTMLAnchorElement)) return;
      if ((target.target && target.target !== "_self") || target.hasAttribute("download")) return;

      const url = new URL(target.href);
      if (url.origin !== window.location.origin) return;
      // A link to a part of this same page does not leave it.
      if (url.pathname === window.location.pathname && url.search === window.location.search) return;

      event.preventDefault();
      event.stopPropagation();
      const href = `${url.pathname}${url.search}${url.hash}`;
      askBeforeLeaving({ message, onConfirm: () => router.push(href) });
    };

    const onPop = () => {
      if (leavingRef.current) {
        leavingRef.current = false;
        return;
      }
      if (ignorePopRef.current) {
        ignorePopRef.current = false;
        return;
      }
      // Undo the back step so the form stays on screen while the user decides.
      ignorePopRef.current = true;
      window.history.go(1);
      askBeforeLeaving({
        message,
        onConfirm: () => {
          leavingRef.current = true;
          window.history.go(-1);
        },
      });
    };

    window.addEventListener("beforeunload", onBeforeUnload);
    document.addEventListener("click", onClick, true);
    window.addEventListener("popstate", onPop);
    return () => {
      window.removeEventListener("beforeunload", onBeforeUnload);
      document.removeEventListener("click", onClick, true);
      window.removeEventListener("popstate", onPop);
    };
  }, [dirty, message, router]);
}
