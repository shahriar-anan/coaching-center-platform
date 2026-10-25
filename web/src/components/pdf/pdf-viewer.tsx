"use client";

import dynamic from "next/dynamic";
import { PdfSkeleton } from "./pdf-skeleton";

export type PdfViewerProps = {
  /** Fetches the file with the caller's auth, for example `() => apiBlob(path)`. Called again on retry. */
  load: () => Promise<Blob>;
  /** Drawn diagonally over every page, for example "নাঈম হাসান · S18492017". */
  watermark?: string;
  /** Staff answer scripts only. Students never get a download control or the context menu. */
  allowDownload?: boolean;
  /** Name for the downloaded file when `allowDownload` is set. */
  fileName?: string;
  /** Accessible name of the viewer region. Defaults to the question-paper label. */
  label?: string;
};

/**
 * Inline PDF viewer. `pdfjs-dist` and the worker load only when this component renders,
 * so pages that never show a PDF never download them.
 */
export const PdfViewer = dynamic<PdfViewerProps>(
  () => import("./pdf-document").then((module) => module.PdfDocument),
  { ssr: false, loading: () => <PdfSkeleton /> },
);

export default PdfViewer;
