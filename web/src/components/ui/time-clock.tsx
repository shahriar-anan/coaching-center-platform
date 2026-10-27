"use client";

import { useRef, useState, type PointerEvent } from "react";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";

type Mode = "hour" | "minute";

const SIZE = 264;
const CENTER = SIZE / 2;
const RADIUS = 92;
/** The tap target for each number button. Below 44 px is too small to hit on a phone. */
const TARGET = 44;

/** The numbers printed on the face: 12 hours, then the minutes in fives. Keyboard users press these; a tap or drag reads any minute. */
const HOURS = [12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11];
const MINUTES = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55];

const pad = (n: number) => String(n).padStart(2, "0");

/**
 * A clock face to tap or drag. The hour comes first (it snaps to the 12 hours), then the minutes,
 * which read every minute from the angle. The hour and minute above the face switch between the two.
 * `hour` is on the 12-hour clock (1 to 12).
 */
export function TimeClock({
  hour,
  minute,
  onHour,
  onMinute,
}: {
  hour: number | null;
  minute: number | null;
  onHour: (hour: number) => void;
  onMinute: (minute: number) => void;
}) {
  const { t } = useLocale();
  const [mode, setMode] = useState<Mode>("hour");
  const dragging = useRef(false);
  const values = mode === "hour" ? HOURS : MINUTES;
  const selected = mode === "hour" ? hour : minute;

  function commit(value: number) {
    if (mode === "hour") {
      onHour(value);
    } else {
      onMinute(value);
    }
  }

  /** The hour (12 positions) or the minute (60 positions) under the pointer, from the top of the face clockwise. */
  function valueAt(event: PointerEvent<HTMLDivElement>): number {
    const rect = event.currentTarget.getBoundingClientRect();
    const dx = event.clientX - (rect.left + rect.width / 2);
    const dy = event.clientY - (rect.top + rect.height / 2);
    let angle = Math.atan2(dx, -dy);
    if (angle < 0) {
      angle += 2 * Math.PI;
    }
    if (mode === "hour") {
      const step = Math.round((angle / (2 * Math.PI)) * 12) % 12;
      return step === 0 ? 12 : step;
    }
    return Math.round((angle / (2 * Math.PI)) * 60) % 60;
  }

  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    dragging.current = true;
    commit(valueAt(event));
  }

  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    if (dragging.current) {
      commit(valueAt(event));
    }
  }

  function onPointerUp() {
    dragging.current = false;
    // Choosing an hour moves on to the minutes.
    if (mode === "hour") {
      setMode("minute");
    }
  }

  // The hand points at the selected value: 12 positions for the hour, 60 for the minute.
  const hand = selected === null ? null : handPoint(mode === "hour" ? selected % 12 : selected, mode === "hour" ? 12 : 60);

  return (
    <div className="grid justify-items-center gap-3">
      <div className="flex gap-2 text-card-title font-semibold tabular-nums">
        <button
          type="button"
          aria-pressed={mode === "hour"}
          onClick={() => setMode("hour")}
          className={cn("min-h-11 min-w-16 rounded-control px-3", mode === "hour" ? "bg-action text-on-action" : "text-text hover:bg-field")}
        >
          {hour === null ? "--" : pad(hour)}
        </button>
        <span aria-hidden="true" className="self-center">
          :
        </span>
        <button
          type="button"
          aria-pressed={mode === "minute"}
          onClick={() => setMode("minute")}
          className={cn("min-h-11 min-w-16 rounded-control px-3", mode === "minute" ? "bg-action text-on-action" : "text-text hover:bg-field")}
        >
          {minute === null ? "--" : pad(minute)}
        </button>
      </div>

      <div
        role="group"
        aria-label={t(mode === "hour" ? "common.dateTime.clockHours" : "common.dateTime.clockMinutes")}
        className="relative touch-none select-none"
        style={{ width: SIZE, height: SIZE }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      >
        <svg aria-hidden="true" width={SIZE} height={SIZE} className="pointer-events-none absolute inset-0">
          <circle cx={CENTER} cy={CENTER} r={CENTER - 2} style={{ fill: "var(--field)", stroke: "var(--border-control)" }} />
          {hand ? <line x1={CENTER} y1={CENTER} x2={hand.x} y2={hand.y} style={{ stroke: "var(--action)" }} strokeWidth={2} /> : null}
          <circle cx={CENTER} cy={CENTER} r={4} style={{ fill: "var(--action)" }} />
        </svg>
        {values.map((value, index) => {
          const point = handPoint(index, values.length);
          const isSelected = selected !== null && value === (mode === "hour" ? selected % 12 || 12 : selected);
          const label = mode === "hour" ? String(value) : pad(value);
          return (
            <button
              key={value}
              type="button"
              aria-label={label}
              aria-pressed={isSelected}
              onPointerDown={(event) => event.stopPropagation()}
              onPointerUp={(event) => event.stopPropagation()}
              onClick={() => {
                commit(value);
                if (mode === "hour") {
                  setMode("minute");
                }
              }}
              style={{ left: point.x - TARGET / 2, top: point.y - TARGET / 2, width: TARGET, height: TARGET }}
              className={cn(
                // Taps and drags go to the face, which reads the exact minute. The numbers stay reachable by keyboard.
                "pointer-events-none absolute grid place-items-center rounded-full text-body font-medium tabular-nums transition-colors",
                isSelected ? "bg-action text-on-action" : "text-text hover:bg-bg",
              )}
            >
              {label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/** The position around the face of step `index` out of `steps`, with step 0 at the top. */
function handPoint(index: number, steps: number): { x: number; y: number } {
  const angle = ((index / steps) * 2 * Math.PI - Math.PI / 2);
  return { x: CENTER + RADIUS * Math.cos(angle), y: CENTER + RADIUS * Math.sin(angle) };
}
