"use client";

import { useEffect, useSyncExternalStore, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useLocale } from "@/i18n/locale-context";
import { paperClass, paperVars } from "./paper-theme";

/** The student shell's root. While a paper is open the shell is inert, so its header, menus and footer cannot be reached. */
const SHELL_ROOT = '[data-surface="site"]';

function noSubscribe() {
  return () => undefined;
}

/**
 * The full-screen layer an MCQ paper is printed on. It covers the window above the site header, ignores the
 * app theme (paper-theme.ts) and scrolls on its own. While it is open the shell behind it is made inert and
 * hidden from assistive technology, so Tab and screen readers stay on the paper; the header is out of reach as
 * the old site had it. The layer is portalled to the body, so no transformed or clipped ancestor can move it.
 * It never touches the history, so the browser Back button works as before.
 */
export function PaperLayer({ children }: { children: ReactNode }) {
  const { t } = useLocale();
  // A portal needs the document, so the layer appears after the first client render.
  const mounted = useSyncExternalStore(noSubscribe, () => true, () => false);

  useEffect(() => {
    if (!mounted) {
      return;
    }
    const shell = document.querySelector<HTMLElement>(SHELL_ROOT);
    if (!shell) {
      return;
    }
    const previousHidden = shell.getAttribute("aria-hidden");
    shell.setAttribute("inert", "");
    shell.setAttribute("aria-hidden", "true");
    return () => {
      shell.removeAttribute("inert");
      if (previousHidden === null) {
        shell.removeAttribute("aria-hidden");
      } else {
        shell.setAttribute("aria-hidden", previousHidden);
      }
    };
  }, [mounted]);

  if (!mounted) {
    return null;
  }

  return createPortal(
    <main
      data-paper-layer
      className={`fixed inset-0 z-30 overflow-y-auto overscroll-contain ${paperClass.paper} ${paperClass.ink}`}
      style={{ ...paperVars, colorScheme: "light" }}
    >
      {/* The shell's skip link is inert here, so the paper has its own: it goes straight to the questions. */}
      <a
        href="#paper-questions"
        className="sr-only focus:not-sr-only focus:fixed focus:start-3 focus:top-3 focus:z-50 focus:border focus:border-black focus:bg-white focus:px-4 focus:py-2 focus:text-black"
      >
        {t("sitting.paper.skipToQuestions")}
      </a>
      {children}
    </main>,
    document.body,
  );
}
