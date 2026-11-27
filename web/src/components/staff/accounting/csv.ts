/*
 * CSV for the accounting reports, built in the browser from the answer (plan 4.6: Print and CSV on each report). Excel reads the byte
 * order mark, so Bangla text comes out right. Cells with a comma, quote or line break are quoted; quotes inside are doubled.
 */

export function csvCell(value: string | number | null | undefined): string {
  const text = value === null || value === undefined ? "" : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/** The rows as CSV text: a byte order mark, then one line per row, CRLF between lines. */
export function toCsv(rows: (string | number | null | undefined)[][]): string {
  return `﻿${rows.map((row) => row.map(csvCell).join(",")).join("\r\n")}\r\n`;
}

/** Saves CSV text as a file in the browser. */
export function downloadCsv(fileName: string, text: string): void {
  const blob = new Blob([text], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}
