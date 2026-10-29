"use client";

import { useEffect, useId, useRef, useState } from "react";
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/components/ui/cn";
import { controlBase, controlBorder } from "@/components/ui/control";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { dhakaParts, fromDhakaParts, toBengaliDigits } from "@/lib/format";

type Period = "AM" | "PM";

/** What the user has typed: the date as dd/mm/yyyy, the time as hh:mm, and AM or PM. */
type Draft = { date: string; time: string; period: Period };

type DateError = "incomplete" | "invalidDate" | "invalidTime";

/** The month shown in the calendar. `month` is 0 to 11. */
type Month = { year: number; month: number };

const EMPTY: Draft = { date: "", time: "", period: "AM" };
const pad = (n: number) => String(n).padStart(2, "0");
const HOURS = Array.from({ length: 12 }, (_, i) => pad(i + 1));
const MINUTES = Array.from({ length: 60 }, (_, i) => pad(i));
const secondaryButton =
  "min-h-11 rounded-control border border-border-control bg-field px-4 text-body font-medium text-text transition-colors hover:border-muted disabled:cursor-not-allowed disabled:opacity-60 sm:min-h-9";

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

/** The day, month (0 to 11) and year of a typed dd/mm/yyyy date, or null while it is not one. */
function typedDate(text: string): { day: number; month: number; year: number } | null {
  const match = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(text.trim());
  if (!match) {
    return null;
  }
  return { day: Number(match[1]), month: Number(match[2]) - 1, year: Number(match[3]) };
}

/** The month the calendar opens on: the typed date's month, or this month in Dhaka. */
function monthFor(draft: Draft): Month {
  const date = typedDate(draft.date);
  if (date && date.month >= 0 && date.month <= 11) {
    return { year: date.year, month: date.month };
  }
  const now = dhakaParts(Date.now());
  return { year: now.year, month: now.month };
}

/** The day numbers of a month in calendar order, with `null` for the blank cells before the first day (weeks start on Sunday). */
function calendarDays({ year, month }: Month): (number | null)[] {
  const first = new Date(Date.UTC(year, month, 1)).getUTCDay();
  const count = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  return [...Array.from({ length: first }, () => null), ...Array.from({ length: count }, (_, i) => i + 1)];
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

function isEmpty(draft: Draft): boolean {
  return !draft.date.trim() && !draft.time.trim();
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
  const date = typedDate(draft.date);
  if (!date) {
    return { iso: null, error: draft.date.trim().length >= 10 ? "invalidDate" : "incomplete" };
  }
  const { day, month, year } = date;
  const probe = new Date(Date.UTC(year, month, day));
  if (month < 0 || month > 11 || probe.getUTCDate() !== day) {
    return { iso: null, error: "invalidDate" };
  }
  const time = parseTime(draft.time, draft.period);
  if (!time) {
    return { iso: null, error: draft.time.trim().length >= 5 ? "invalidTime" : "incomplete" };
  }
  const iso = fromDhakaParts({ year, month, day, hour: time.hour24, minute: time.minute }).toISOString();
  return { iso, error: null };
}

/** One scroll column of the picker. It opens scrolled so the chosen row is near the top. */
function TimeColumn({
  label,
  options,
  selected,
  onPick,
}: {
  label: string;
  options: { value: string; text: string }[];
  selected: string | null;
  onPick: (value: string) => void;
}) {
  const list = useRef<HTMLDivElement>(null);
  // The chosen row is measured, because a row is taller on a phone (44 px) than on a desktop (32 px).
  useEffect(() => {
    const chosen = list.current?.querySelector<HTMLElement>('[aria-pressed="true"]');
    if (list.current && chosen) {
      list.current.scrollTop = Math.max(0, chosen.offsetTop - 2 * chosen.offsetHeight);
    }
  }, []);
  return (
    <div
      ref={list}
      role="group"
      aria-label={label}
      className="relative grid max-h-40 content-start gap-1 overflow-y-auto pe-1 sm:max-h-[15.5rem]"
    >
      {options.map((option) => {
        const chosen = option.value === selected;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={chosen}
            onClick={() => onPick(option.value)}
            className={cn(
              "min-h-11 rounded-control border px-2 text-body font-medium transition-colors sm:min-h-8",
              chosen ? "border-action bg-action text-on-action" : "border-transparent text-text hover:bg-field",
            )}
          >
            {option.text}
          </button>
        );
      })}
    </div>
  );
}

/**
 * A date and time in Asia/Dhaka: the date as dd/mm/yyyy, the time typed as hh:mm on a
 * 12-hour clock, and AM or PM. A calendar and three scroll columns (hour, minute, AM or PM)
 * open from a button beside the typed fields. The value and `onChange` use ISO UTC, so the API
 * always gets an instant. A cleared form gives `null`. A partly typed form keeps the last
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
  const { t, locale } = useLocale();
  const { formatDateTime } = useFormat();
  const [draft, setDraft] = useState<Draft>(() => draftFor(value));
  const [error, setError] = useState<string | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);
  const [view, setView] = useState<Month>(() => monthFor(draft));
  const panelId = useId();
  const wrapRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  // The value this control last sent. A different value from outside (a save or a reset) replaces the draft.
  const sent = useRef<string | null>(value);

  useEffect(() => {
    if (value !== sent.current) {
      sent.current = value;
      setDraft(draftFor(value));
      setError(null);
    }
  }, [value]);

  useEffect(() => {
    if (!panelOpen) {
      return;
    }
    // A press outside the picker closes it. Escape closes it and returns focus to the button.
    const onPress = (event: Event) => {
      if (!wrapRef.current?.contains(event.target as Node)) {
        setPanelOpen(false);
      }
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setPanelOpen(false);
        triggerRef.current?.focus();
      }
    };
    document.addEventListener("pointerdown", onPress);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPress);
      document.removeEventListener("keydown", onKey);
    };
  }, [panelOpen]);

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
  // The typed time on a 12-hour clock, or null while it is not a time yet. The columns follow it.
  const typed = parseTime(draft.time, draft.period);
  const selectedDate = typedDate(draft.date);
  const selectedDay = selectedDate && selectedDate.year === view.year && selectedDate.month === view.month ? selectedDate.day : null;

  // Digits follow the interface language: Bangla digits in Bangla.
  const digits = (text: string) => (locale === "bn" ? toBengaliDigits(text) : text);
  const tag = locale === "bn" ? "bn-BD" : "en-GB";
  const monthTitle = new Intl.DateTimeFormat(tag, { month: "long", year: "numeric", timeZone: "UTC" }).format(
    new Date(Date.UTC(view.year, view.month, 1)),
  );
  const dayName = new Intl.DateTimeFormat(tag, { weekday: "short", timeZone: "UTC" });
  // 7 January 2024 was a Sunday.
  const weekdays = Array.from({ length: 7 }, (_, i) => dayName.format(new Date(Date.UTC(2024, 0, 7 + i))));

  function togglePanel() {
    if (!panelOpen) {
      setView(monthFor(draft));
    }
    setPanelOpen((open) => !open);
  }

  function shiftMonth(delta: number) {
    setView((current) => {
      const next = new Date(Date.UTC(current.year, current.month + delta, 1));
      return { year: next.getUTCFullYear(), month: next.getUTCMonth() };
    });
  }

  function pickDay(day: number) {
    update({ ...draft, date: `${pad(day)}/${pad(view.month + 1)}/${view.year}` });
  }

  // A column pick keeps the other two parts: the minute when the hour is picked, the hour when the minute is.
  function pickHour(hour: string) {
    update({ ...draft, time: `${hour}:${pad(typed?.minute ?? 0)}` });
  }

  function pickMinute(minute: string) {
    update({ ...draft, time: `${pad(typed ? (typed.hour24 % 12) || 12 : 12)}:${minute}` });
  }

  // The chosen period wins over a 24-hour time or a typed marker, so the time is written on a 12-hour clock.
  function pickPeriod(period: Period) {
    update({ ...draft, period, time: typed ? `${pad((typed.hour24 % 12) || 12)}:${pad(typed.minute)}` : draft.time });
  }

  function chooseNow() {
    const now = draftFor(new Date().toISOString());
    update(now);
    setView(monthFor(now));
  }

  const hourOptions = HOURS.map((hour) => ({ value: hour, text: digits(hour) }));
  const minuteOptions = MINUTES.map((minute) => ({ value: minute, text: digits(minute) }));
  const periodOptions = [
    { value: "AM", text: t("common.dateTime.am") },
    { value: "PM", text: t("common.dateTime.pm") },
  ];

  return (
    <div ref={wrapRef} className={cn("grid gap-2", className)}>
      <div className="grid gap-3 sm:flex sm:flex-wrap sm:items-end">
        {/* The calendar is the date field's own picker, so its button sits beside the typed date. */}
        <div className="flex items-end gap-2 sm:w-56">
          <label className="grid min-w-0 flex-1 gap-1 text-meta text-muted">
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
          <button
            ref={triggerRef}
            type="button"
            aria-label={t(panelOpen ? "common.dateTime.hideCalendar" : "common.dateTime.pickOnCalendar")}
            title={t(panelOpen ? "common.dateTime.hideCalendar" : "common.dateTime.pickOnCalendar")}
            aria-expanded={panelOpen}
            aria-controls={panelId}
            disabled={disabled}
            onClick={togglePanel}
            className="grid min-h-11 min-w-11 shrink-0 place-items-center rounded-control border border-border-control bg-field text-text transition-colors hover:border-muted disabled:cursor-not-allowed disabled:opacity-60"
          >
            <CalendarDays aria-hidden="true" className="size-5" />
          </button>
        </div>
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
          {/* A small segmented control beside the time: AM and PM on one track. */}
          <div className="inline-flex gap-0.5 rounded-control border border-border-control bg-field p-0.5">
            {(["AM", "PM"] as const).map((period) => {
              const selected = draft.period === period;
              return (
                <button
                  key={period}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  disabled={disabled}
                  onClick={() => pickPeriod(period)}
                  className={cn(
                    "min-h-11 min-w-14 rounded-control px-3 text-label font-medium transition-colors sm:min-h-10",
                    selected ? "bg-action text-on-action" : "text-text hover:bg-card",
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
      {/* The panel opens under the row, from the date's side. The empty wrapper takes no space while it is closed. */}
      <div className="relative empty:hidden">
        {panelOpen ? (
          <div
            id={panelId}
            className="absolute start-0 top-full z-30 mt-2 grid w-[min(20rem,calc(100vw-2rem))] gap-3 rounded-card border border-border bg-card p-3 shadow-lg sm:w-[min(27rem,calc(100vw-2rem))]"
          >
            {/* The calendar on the left and the time columns on the right; stacked on a phone. */}
            <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
            <div className="grid content-start gap-2">
            <div className="flex items-center justify-between gap-2">
              <button
                type="button"
                aria-label={t("common.dateTime.previousMonth")}
                onClick={() => shiftMonth(-1)}
                className="grid min-h-11 min-w-11 place-items-center rounded-control text-text hover:bg-field sm:min-h-8 sm:min-w-8"
              >
                <ChevronLeft aria-hidden="true" className="size-5" />
              </button>
              <p className="text-label font-semibold text-text">{monthTitle}</p>
              <button
                type="button"
                aria-label={t("common.dateTime.nextMonth")}
                onClick={() => shiftMonth(1)}
                className="grid min-h-11 min-w-11 place-items-center rounded-control text-text hover:bg-field sm:min-h-8 sm:min-w-8"
              >
                <ChevronRight aria-hidden="true" className="size-5" />
              </button>
            </div>
            <div role="group" aria-label={t("common.dateTime.calendar")} className="grid grid-cols-7 gap-1">
              {weekdays.map((name) => (
                <span key={name} aria-hidden="true" className="grid place-items-center text-pill text-muted">
                  {name}
                </span>
              ))}
              {calendarDays(view).map((day, index) =>
                day === null ? (
                  <span key={`blank-${index}`} aria-hidden="true" />
                ) : (
                  <button
                    key={day}
                    type="button"
                    aria-pressed={selectedDay === day}
                    onClick={() => pickDay(day)}
                    className={cn(
                      "min-h-11 rounded-control text-body font-medium transition-colors sm:min-h-8",
                      selectedDay === day ? "bg-action text-on-action" : "text-text hover:bg-field",
                    )}
                  >
                    {digits(String(day))}
                  </button>
                ),
              )}
            </div>
            </div>
            <div className="grid grid-cols-3 gap-1 sm:w-44">
              <TimeColumn
                label={t("common.dateTime.chooseHour")}
                options={hourOptions}
                selected={typed ? pad((typed.hour24 % 12) || 12) : null}
                onPick={pickHour}
              />
              <TimeColumn
                label={t("common.dateTime.chooseMinute")}
                options={minuteOptions}
                selected={typed ? pad(typed.minute) : null}
                onPick={pickMinute}
              />
              <TimeColumn
                label={t("common.dateTime.period")}
                options={periodOptions}
                selected={draft.period}
                onPick={(period) => pickPeriod(period === "PM" ? "PM" : "AM")}
              />
            </div>
            </div>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex gap-2">
                <button type="button" disabled={disabled} onClick={chooseNow} className={secondaryButton}>
                  {t("common.dateTime.now")}
                </button>
                <button type="button" disabled={disabled} onClick={() => update(EMPTY)} className={secondaryButton}>
                  {t("common.dateTime.clear")}
                </button>
              </div>
              <button
                type="button"
                onClick={() => {
                  setPanelOpen(false);
                  triggerRef.current?.focus();
                }}
                className="min-h-11 rounded-control bg-action px-4 text-body font-medium text-on-action transition-colors hover:opacity-90 sm:min-h-9"
              >
                {t("common.dateTime.done")}
              </button>
            </div>
          </div>
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
