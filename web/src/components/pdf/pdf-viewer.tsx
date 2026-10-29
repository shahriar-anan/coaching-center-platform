"use client";

import dynamic from "next/dynamic";
import { Skeleton } from "@/components/ui/skeleton";
import type { PdfViewerProps } from "@/components/pdf/pdf-viewer-impl";

/**
 * The PDF viewer, loaded on demand so `pdfjs-dist` stays out of the first
 * bundle. Props: `load`, `studentLine?`, `allowDownload?`, `fileName?`, `label?`.
 */
export const PdfViewer = dynamic<PdfViewerProps>(
  () => import("@/components/pdf/pdf-viewer-impl").then((m) => m.PdfViewerImpl),
  { ssr: false, loading: () => <Skeleton variant="card" /> },
);
