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
      <div className="hidden overflow-x-auto rounded-card border border-border bg-card sm:block">
        <table className="w-full min-w-[32rem] border-collapse text-body">
          <thead className="sticky top-0 bg-card">
            <tr className="border-b border-border text-start text-muted">
              {columns.map((col) => (
                <th
                  key={col.key}
                  scope="col"
                  className={cn("px-3 py-2.5 font-medium", col.align === "end" ? "text-end" : "text-start")}
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
                className={cn("border-b border-border last:border-0", clickable && "cursor-pointer hover:bg-bg")}
              >
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className={cn("px-3 py-2.5 align-middle", col.align === "end" ? "text-end" : "text-start")}
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
            {columns.map((col) => (
              <div key={col.key} className="flex items-start justify-between gap-3 text-body">
                <span className="shrink-0 text-muted">
                  {col.mobileLabel ?? (typeof col.header === "string" ? col.header : null)}
                </span>
                <span className="min-w-0 text-end">{col.cell(row)}</span>
              </div>
            ))}
          </li>
        ))}
      </ul>
    </>
  );
}
