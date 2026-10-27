"use client";

import { useEffect, useRef, useState } from "react";
import { cn } from "@/components/ui/cn";
import { controlBase, controlBorder } from "@/components/ui/control";
import { useLocale } from "@/i18n/locale-context";
import { TimeClock } from "@/components/ui/time-clock";
import { useFormat } from "@/lib/format/use-format";
import { dhakaParts, fromDhakaParts } from "@/lib/format";

type Period = "AM" | "PM";

/** What the user has typed: the date as dd/mm/yyyy, the time as hh:mm, and AM or PM. */
type Draft = { date: string; time: string; period: Period };

type DateError = "incomplete" | "invalidDate" | "invalidTime";

const EMPTY: Draft = { date: "", time: "", period: "AM" };
const pad = (n: number) => String(n).padStart(2, "0");

/** The draft fields for an instant, in Dhaka time, with the time on a 12-hour clock. An empty value gives empty fields. */
function draftFor(iso: string | null): Draft {
  if (!iso) {
    return EMPTY;
  }
  const p = dhakaParts(iso);
  return {
    date: `${pad(p.day)}/${pad(p.month + 1)}/${p.year}`,
    time: `${pad(p.hour % 12 || 12)}:${pad(p.minute)}`,
    period: p.hour >= 12 ? "PM" : "AM",
  };
}

/** The hour (0 to 23, as typed) and the minute of a typed time, or nulls while it is empty or not a time yet. */
function clockParts(text: string): { hour: number | null; minute: number | null } {
  const match = /^(\d{1,2})[:.](\d{2})/.exec(text.trim());
  if (!match) {
    return { hour: null, minute: null };
  }
  return { hour: Number(match[1]), minute: Number(match[2]) };
}

function isEmpty(draft: Draft): boolean {
  return !draft.date.trim() && !draft.time.trim();
}

/**
 * Reads a typed time. `10:30` uses the AM or PM button. `10:30 pm` or `10.30PM` sets PM.
 * `22:30` is 24-hour time and sets PM by itself. Returns `null` when it is not a time.
 */
function parseTime(text: string, fallback: Period): { hour24: number; minute: number; period: Period } | null {
  const match = /^(\d{1,2})[:.](\d{2})\s*(a\.?m\.?|p\.?m\.?)?$/i.exec(text.trim());
  if (!match) {
    return null;
  }
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (minute > 59) {
    return null;
  }
  const marker = match[3]?.toLowerCase();
  if (marker) {
    if (hour < 1 || hour > 12) {
      return null;
    }
    const period: Period = marker.startsWith("p") ? "PM" : "AM";
    return { hour24: (hour % 12) + (period === "PM" ? 12 : 0), minute, period };
  }
  if (hour > 12 && hour <= 23) {
    return { hour24: hour, minute, period: hour >= 12 ? "PM" : "AM" };
  }
  if (hour >= 1 && hour <= 12) {
    return { hour24: (hour % 12) + (fallback === "PM" ? 12 : 0), minute, period: fallback };
  }
  return null;
}

/** The instant for a complete, real draft in Dhaka time, or why it is not one yet. */
function instantFor(draft: Draft): { iso: string | null; error: DateError | null } {
  if (isEmpty(draft)) {
    return { iso: null, error: null };
  }
  if (!draft.date.trim() || !draft.time.trim()) {
    return { iso: null, error: "incomplete" };
  }
  // A short entry is still being typed, so it is not an error yet.
  const date = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(draft.date.trim());
  if (!date) {
    return { iso: null, error: draft.date.trim().length >= 10 ? "invalidDate" : "incomplete" };
  }
  const day = Number(date[1]);
  const month = Number(date[2]);
  const year = Number(date[3]);
  const probe = new Date(Date.UTC(year, month - 1, day));
  if (month < 1 || month > 12 || probe.getUTCDate() !== day) {
    return { iso: null, error: "invalidDate" };
  }
  const time = parseTime(draft.time, draft.period);
  if (!time) {
    return { iso: null, error: draft.time.trim().length >= 5 ? "invalidTime" : "incomplete" };
  }
  const iso = fromDhakaParts({ year, month: month - 1, day, hour: time.hour24, minute: time.minute }).toISOString();
  return { iso, error: null };
}

/**
 * A date and time in Asia/Dhaka: the date as dd/mm/yyyy, the time typed as hh:mm on a
 * 12-hour clock, and AM or PM. The value and `onChange` use ISO UTC, so the API always
 * gets an instant. A cleared form gives `null`. A partly typed form keeps the last
 * complete value until it is complete.
 */
export function DateTimeInput({
  value,
  onChange,
  invalid,
  id,
  name,
  disabled,
  "aria-describedby": describedBy,
  className,
}: {
  value: string | null;
  onChange: (iso: string | null) => void;
  invalid?: boolean;
  id?: string;
  name?: string;
  disabled?: boolean;
  "aria-describedby"?: string;
  className?: string;
}) {
  const { t } = useLocale();
  const { formatDateTime } = useFormat();
  const [draft, setDraft] = useState<Draft>(() => draftFor(value));
  const [error, setError] = useState<string | null>(null);
  const [clockOpen, setClockOpen] = useState(false);
  // The value this control last sent. A different value from outside (a save or a reset) replaces the draft.
  const sent = useRef<string | null>(value);

  useEffect(() => {
    if (value !== sent.current) {
      sent.current = value;
      setDraft(draftFor(value));
      setError(null);
    }
  }, [value]);

  function update(next: Draft) {
    // A typed AM or PM, or a 24-hour time, sets the AM or PM button too.
    const typed = next.time.trim() ? parseTime(next.time, next.period) : null;
    const settled = typed ? { ...next, period: typed.period } : next;
    setDraft(settled);
    const result = instantFor(settled);
    // "Incomplete" is not shown: the field is still being filled in.
    const shown = result.error === "invalidDate" || result.error === "invalidTime" ? result.error : null;
    setError(shown ? t(`common.dateTime.${shown}`) : null);
    if (result.iso) {
      sent.current = result.iso;
      onChange(result.iso);
    } else if (isEmpty(settled)) {
      sent.current = null;
      onChange(null);
    }
  }

  const complete = instantFor(draft).iso;
  // The clock face shows the hour on a 12-hour clock; the typed time keeps the 24-hour value.
  const clock = clockParts(draft.time);
  const clockHour = clock.hour === null ? null : clock.hour % 12 || 12;

  return (
    <div className={cn("grid gap-2", className)}>
      <div className="grid gap-3 sm:flex sm:flex-wrap sm:items-end">
        <label className="grid gap-1 text-meta text-muted sm:w-44">
          <span>{t("common.dateTime.date")}</span>
          <input
            id={id}
            name={name}
            type="text"
            inputMode="numeric"
            autoComplete="off"
            placeholder={t("common.dateTime.datePlaceholder")}
            value={draft.date}
            disabled={disabled}
            aria-invalid={invalid || error ? true : undefined}
            aria-describedby={describedBy}
            onChange={(e) => update({ ...draft, date: e.target.value })}
            className={cn(controlBase, controlBorder(invalid || Boolean(error)), "tabular-nums")}
          />
        </label>
        <label className="grid gap-1 text-meta text-muted sm:w-40">
          <span>{t("common.dateTime.time")}</span>
          <input
            name={name ? `${name}-time` : undefined}
            type="text"
            inputMode="numeric"
            autoComplete="off"
            placeholder={t("common.dateTime.timePlaceholder")}
            value={draft.time}
            disabled={disabled}
            aria-invalid={invalid || error ? true : undefined}
            onChange={(e) => update({ ...draft, time: e.target.value })}
            className={cn(controlBase, controlBorder(invalid || Boolean(error)), "tabular-nums")}
          />
        </label>
        <div role="radiogroup" aria-label={t("common.dateTime.period")} className="grid gap-1 text-meta text-muted">
          <span aria-hidden="true">{t("common.dateTime.period")}</span>
          <div className="flex gap-1">
            {(["AM", "PM"] as const).map((period) => {
              const selected = draft.period === period;
              return (
                <button
                  key={period}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  disabled={disabled}
                  onClick={() => update({ ...draft, period })}
                  className={cn(
                    "min-h-11 min-w-16 rounded-control border px-4 text-body font-medium transition-colors",
                    selected ? "border-action bg-action text-on-action" : "border-border-control bg-field text-text hover:border-muted",
                    "disabled:cursor-not-allowed disabled:opacity-60",
                  )}
                >
                  {t(`common.dateTime.${period === "AM" ? "am" : "pm"}`)}
                </button>
              );
            })}
          </div>
        </div>
      </div>
      <div className="grid gap-3">
        <button
          type="button"
          aria-expanded={clockOpen}
          disabled={disabled}
          onClick={() => setClockOpen((open) => !open)}
          className="min-h-11 justify-self-start rounded-control px-1 text-meta font-medium text-action underline-offset-2 hover:underline"
        >
          {t(clockOpen ? "common.dateTime.hideClock" : "common.dateTime.pickOnClock")}
        </button>
        {clockOpen ? (
          <TimeClock
            hour={clockHour}
            minute={clock.minute}
            onHour={(hour) => update({ ...draft, time: `${pad(hour)}:${pad(clock.minute ?? 0)}` })}
            onMinute={(minute) => update({ ...draft, time: `${pad(clockHour ?? 12)}:${pad(minute)}` })}
          />
        ) : null}
      </div>
      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : complete ? (
        <p className="text-meta text-muted">{t("common.dateTime.starts", { when: formatDateTime(complete) })}</p>
      ) : null}
    </div>
  );
}
