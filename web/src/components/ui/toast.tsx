"use client";

import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { AlertCircle, CheckCircle2, Info, X, type LucideIcon } from "lucide-react";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import { IconButton } from "@/components/ui/icon-button";

export type ToastTone = "ok" | "danger" | "info";

export type ToastInput = { title: string; tone?: ToastTone };

type ToastItem = ToastInput & { id: number };

type ToastContextValue = { toast: (input: ToastInput) => void };

const ToastContext = createContext<ToastContextValue | null>(null);

const LIFETIME_MS = 1000;
const ERROR_LIFETIME_MS = 4000;

const toneIcon: Record<ToastTone, LucideIcon> = {
  ok: CheckCircle2,
  danger: AlertCircle,
  info: Info,
};

const toneIconClass: Record<ToastTone, string> = {
  ok: "text-ok",
  danger: "text-danger",
  info: "text-muted",
};

/** Shows one short message at a time, centred on screen, in a polite live region. Mount once, near the root. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useLocale();
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setItems((list) => list.filter((item) => item.id !== id));
  }, []);

  const toast = useCallback(
    (input: ToastInput) => {
      const id = nextId.current++;
      // A new message replaces the one on screen, so two never cover each other.
      setItems([{ ...input, id }]);
      // Success messages leave after a second so they do not block the next save; errors stay longer.
      window.setTimeout(() => dismiss(id), input.tone === "danger" ? ERROR_LIFETIME_MS : LIFETIME_MS);
    },
    [dismiss],
  );

  const value = useMemo(() => ({ toast }), [toast]);
  const current = items[items.length - 1];

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        role="status"
        aria-live="polite"
        aria-label={t("common.toast.region")}
        className="pointer-events-none fixed end-4 top-4 z-50 flex w-[min(24rem,calc(100vw-2rem))] justify-end"
      >
        {current ? <ToastCard key={current.id} item={current} onDismiss={() => dismiss(current.id)} /> : null}
      </div>
    </ToastContext.Provider>
  );
}

function ToastCard({ item, onDismiss }: { item: ToastItem; onDismiss: () => void }) {
  const { t } = useLocale();
  const tone = item.tone ?? "info";
  const Icon = toneIcon[tone];
  return (
    <div className="pointer-events-auto grid w-full max-w-sm grid-cols-[auto_1fr_auto] items-start gap-3 rounded-card border border-border bg-card p-4 text-text shadow-xl motion-safe:animate-in motion-safe:fade-in motion-safe:zoom-in-95">
      <Icon aria-hidden="true" className={cn("mt-0.5 size-5 shrink-0", toneIconClass[tone])} />
      <p className="text-label font-medium">{item.title}</p>
      <IconButton
        label={t("common.toast.dismiss")}
        icon={<X aria-hidden="true" className="size-4" />}
        onClick={onDismiss}
      />
    </div>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return ctx;
}
