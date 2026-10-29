"use client";

import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";
import { Skeleton } from "@/components/ui/skeleton";

export type Column<T> = {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  /** Label shown before the value in the stacked card view. Defaults to the header text. */
  mobileLabel?: string;
  align?: "start" | "end";
  /** A Tailwind width class, such as `w-36`. Set it on columns whose content must not squeeze. */
  width?: string;
  /** Keeps the value on one line. Use it for dates, phone numbers, amounts and codes. */
  nowrap?: boolean;
  /** Left out of the stacked card view on a phone. The column stays in the table. Use it for detail that is not needed at a glance. */
  mobileHidden?: boolean;
};

type TableProps<T> = {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  onRowClick?: (row: T) => void;
  loading?: boolean;
  empty?: ReactNode;
};

/**
 * A table of server-paged rows. Above 640 px it is a table with a sticky header
 * inside its own card; below 640 px each row becomes a stacked card with the
 * same fields and the same click action. The page itself never scrolls sideways.
 */
export function Table<T>({ columns, rows, rowKey, onRowClick, loading, empty }: TableProps<T>) {
  if (loading && rows.length === 0) {
    return <Skeleton variant="rows" count={5} />;
  }
  if (rows.length === 0) {
    return <>{empty ?? null}</>;
  }
  const clickable = Boolean(onRowClick);
  return (
    <>
      <div className="hidden overflow-x-auto rounded-card border border-border-control bg-card sm:block">
        <table className="w-full min-w-[32rem] border-collapse text-body">
          <thead className="sticky top-0 bg-bg">
            <tr className="border-b border-border-control text-start text-text">
              {columns.map((col) => (
                <th
                  key={col.key}
                  scope="col"
                  className={cn(
                    "px-4 py-3 text-meta font-semibold",
                    col.width,
                    col.nowrap && "whitespace-nowrap",
                    col.align === "end" ? "text-end" : "text-start",
                  )}
                >
                  {col.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={rowKey(row)}
                onClick={clickable ? () => onRowClick?.(row) : undefined}
                className={cn(
                  "border-b border-border-control/60 last:border-0 even:bg-bg hover:bg-accent/10",
                  clickable && "cursor-pointer",
                )}
              >
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className={cn(
                      "px-4 py-4 align-middle",
                      col.width,
                      col.nowrap && "whitespace-nowrap",
                      col.align === "end" ? "text-end" : "text-start",
                    )}
                  >
                    {col.cell(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul className="grid gap-3 sm:hidden">
        {rows.map((row) => (
          <li
            key={rowKey(row)}
            onClick={clickable ? () => onRowClick?.(row) : undefined}
            className={cn(
              "grid gap-2 rounded-card border border-border bg-card p-4",
              clickable && "cursor-pointer active:bg-bg",
            )}
          >
            {columns.filter((col) => !col.mobileHidden).map((col) => (
              <div key={col.key} className="flex items-start justify-between gap-3 text-body">
                <span className="shrink-0 text-muted">
                  {col.mobileLabel ?? (typeof col.header === "string" ? col.header : null)}
                </span>
                {/* On a phone the links in a card are 44 px tall, the same as the buttons. */}
                <span className="min-w-0 text-end [&_a]:inline-flex [&_a]:min-h-11 [&_a]:items-center [&_a]:justify-end">
                  {col.cell(row)}
                </span>
              </div>
            ))}
          </li>
        ))}
      </ul>
    </>
  );
}
