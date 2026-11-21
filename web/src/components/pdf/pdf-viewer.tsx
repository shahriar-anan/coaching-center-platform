"use client";

import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/skeleton";
import type { PdfViewerProps } from "@/components/pdf/pdf-viewer-impl";

/**
 * The PDF viewer, loaded on demand so `pdfjs-dist` stays out of the first
 * bundle. Props: `load`, `cacheKey?`, `download?`, `fileKind?`, `studentLine?`, `allowDownload?`, `fileName?`, `label?`
 * (see `PdfViewerProps`). Pass `load`'s options on to `apiBlob`, so the download shows progress and stops when the
 * viewer closes.
 */
export const PdfViewer = dynamic<PdfViewerProps>(
  () => import("@/components/pdf/pdf-viewer-impl").then((m) => m.PdfViewerImpl),
  { ssr: false, loading: () => <Skeleton variant="card" /> },
);
