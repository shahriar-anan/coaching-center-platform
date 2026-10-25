import type { ReactElement } from "react";

/**
 * WS0 stub body. WS4 replaces this file and keeps the props of `PdfViewer`.
 * Callers import `PdfViewer` from `pdf-viewer.tsx`, which loads this on demand.
 */
export type PdfViewerProps = {
  load: () => Promise<Blob>;
  watermark?: string;
  allowDownload?: boolean;
  fileName?: string;
};

export function PdfViewerImpl(props: PdfViewerProps): ReactElement {
  void props;
  return <div className="rounded-card border border-border bg-card p-6 text-center text-muted">PDF</div>;
}
