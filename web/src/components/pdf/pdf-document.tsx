// The viewer body lives in pdf-viewer-impl.tsx (WS0 loads it through PdfViewer). These names stay
// exported here so existing imports and tests keep working.
export { PdfViewerImpl as PdfDocument, PDF_WORKER_SRC } from "./pdf-viewer-impl";
