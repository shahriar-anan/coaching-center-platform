// Stand-ins for @/components/ui/* (WS0 contracts). Delete after the WS0 merge.
"use client";

import Link from "next/link";
import { clsx } from "./clsx";
import {
  useEffect,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type ReactNode,
} from "react";
import { serverNow } from "./clock";
import { toBnDigits } from "./format";
import { useLocale } from "./locale";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  loading?: boolean;
  fullWidth?: boolean;
  href?: string;
};

const buttonTone = {
  primary: "bg-accent text-on-accent font-bold",
  secondary: "border border-border bg-transparent text-text font-semibold",
  ghost: "bg-transparent text-accent font-semibold",
  danger: "bg-transparent text-danger font-semibold",
};

export function Button({
  variant = "primary",
  loading,
  fullWidth,
  href,
  className,
  children,
  disabled,
  ...rest
}: ButtonProps) {
  const classes = clsx(
    "inline-flex min-h-11 items-center justify-center gap-2 rounded-control px-4 py-2.5 text-center disabled:opacity-60",
    buttonTone[variant],
    fullWidth && "w-full",
    className,
  );
  if (href) {
    return (
      <Link href={href} className={classes}>
        {children}
      </Link>
    );
  }
  return (
    <button
      type="button"
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {children}
    </button>
  );
}

export function Card({
  href,
  className,
  padding = "md",
  children,
}: {
  href?: string;
  className?: string;
  padding?: "md" | "lg";
  children: ReactNode;
}) {
  const classes = clsx(
    "block rounded-card border border-border bg-card text-text",
    padding === "lg" ? "p-6" : "p-4",
    href && "transition hover:-translate-y-0.5 hover:border-accent",
    className,
  );
  if (href) {
    return (
      <Link href={href} className={clsx(classes, "flex items-center justify-between gap-3")}>
        <div className="min-w-0 flex-1">{children}</div>
        <span aria-hidden className="text-xl text-accent">
          ›
        </span>
      </Link>
    );
  }
  return <div className={classes}>{children}</div>;
}

const pillTone = {
  muted: "bg-muted text-card",
  accent: "bg-accent text-on-accent",
  warn: "bg-warn text-white",
  ok: "bg-ok text-white",
  danger: "bg-danger text-white",
};

export function Pill({
  tone,
  pulse,
  children,
}: {
  tone: "muted" | "accent" | "warn" | "ok" | "danger";
  pulse?: boolean;
  children: ReactNode;
}) {
  return (
    <span
      className={clsx(
        "inline-block whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-bold",
        pillTone[tone],
        pulse && "motion-safe:animate-pulse",
      )}
    >
      {children}
    </span>
  );
}

export function Skeleton({ variant, count = 1 }: { variant: "text" | "card" | "rows"; count?: number }) {
  const height = variant === "card" ? "h-20" : variant === "rows" ? "h-10" : "h-4";
  return (
    <div aria-hidden data-testid="skeleton" className="grid gap-3">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className={clsx("animate-pulse rounded-card bg-border", height)} />
      ))}
    </div>
  );
}

export function EmptyState({
  title,
  body,
  action,
  icon,
}: {
  title: string;
  body?: string;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="rounded-card border border-border bg-card p-6 text-center">
      {icon ? <div className="text-3xl">{icon}</div> : null}
      <p className="font-semibold">{title}</p>
      {body ? <p className="mt-1 text-muted">{body}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}

export function ErrorState({ onRetry, message }: { onRetry: () => void; message?: string }) {
  const { t } = useLocale();
  return (
    <div role="alert" className="rounded-card border border-border bg-card p-6 text-center">
      <p>{message ?? t("catalog.loadFailed")}</p>
      <Button variant="secondary" className="mt-3" onClick={onRetry}>
        {t("catalog.retry")}
      </Button>
    </div>
  );
}

export function PageHeader({
  title,
  breadcrumbs,
  actions,
}: {
  title: ReactNode;
  breadcrumbs?: { label: string; href?: string }[];
  actions?: ReactNode;
}) {
  return (
    <div className="mb-4">
      {breadcrumbs && breadcrumbs.length ? (
        <nav aria-label="breadcrumb" className="mb-3 flex flex-wrap gap-1.5 text-sm text-muted">
          {breadcrumbs.map((crumb, i) => (
            <span key={`${crumb.label}-${i}`} className="flex gap-1.5">
              {crumb.href ? (
                <Link className="text-accent" href={crumb.href}>
                  {crumb.label}
                </Link>
              ) : (
                <span aria-current="page">{crumb.label}</span>
              )}
              {i < breadcrumbs.length - 1 ? <span aria-hidden>›</span> : null}
            </span>
          ))}
        </nav>
      ) : null}
      <div className="flex items-start justify-between gap-3">
        <h1 className="text-xl font-bold">{title}</h1>
        {actions}
      </div>
    </div>
  );
}

export function Sheet({
  open,
  onOpenChange,
  title,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    ref.current?.focus();
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onOpenChange(false);
    }
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      previous?.focus?.();
    };
  }, [open, onOpenChange]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center">
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        className="w-full max-w-lg rounded-t-card bg-card p-5 text-text sm:rounded-card"
      >
        <h2 className="mb-3 text-lg font-bold">{title}</h2>
        {children}
      </div>
    </div>
  );
}

function remaining(ms: number, locale: "bn" | "en"): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const text = `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  return locale === "bn" ? toBnDigits(text) : text;
}

export function Countdown({
  deadline,
  onEnd,
}: {
  deadline: string;
  onEnd?: () => void;
  variant?: "inline" | "bar";
  warnAt?: number[];
}) {
  const { locale } = useLocale();
  const [now, setNow] = useState(() => serverNow());
  const end = Date.parse(deadline);
  const ended = useRef(false);
  useEffect(() => {
    const id = window.setInterval(() => setNow(serverNow()), 1000);
    return () => window.clearInterval(id);
  }, []);
  useEffect(() => {
    if (now >= end && !ended.current) {
      ended.current = true;
      onEnd?.();
    }
  }, [now, end, onEnd]);
  return <span className="tabular-nums">{remaining(end - now, locale)}</span>;
}
