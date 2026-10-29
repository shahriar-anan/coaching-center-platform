"use client";

import {
  memo,
  useCallback,
  useEffect,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from "react";
import type { PDFDocumentProxy, RenderTask } from "pdfjs-dist";
import { useLocale } from "@/i18n/locale-context";
import { formatNumber } from "@/lib/format";
/** The props `PdfViewer` (pdf-viewer.tsx) passes through. Unchanged from the WS0 contract; `label` is optional. */
export type PdfViewerProps = {
  /** Fetches the file with the caller's auth, for example `() => apiBlob(path)`. Called again on retry. */
  load: () => Promise<Blob>;
  /**
   * A student's own view only: their name and ID, for example "নাঈম হাসান · S18492017", printed as one
   * small line at the top and bottom of every page. Staff views leave it out.
   */
  studentLine?: string;
  /** Staff answer scripts only. Students never get a download control or the context menu. */
  allowDownload?: boolean;
  /** Name for the downloaded file when `allowDownload` is set. */
  fileName?: string;
  /** Accessible name of the viewer region. Defaults to the question-paper label. */
  label?: string;
};
import { PdfSkeleton } from "./pdf-skeleton";
import { PdfWatermark, pageStrip } from "./pdf-watermark";
import { clampZoom, ZOOM_STEP } from "./zoom";

export const PDF_WORKER_SRC = "/pdf.worker.min.mjs";

/** Canvas pixels per CSS pixel. Capped so a 40-page paper stays inside a low-end phone's memory. */
const MAX_PIXEL_RATIO = 2;

type LoadState =
  | { status: "loading" }
  | { status: "error" }
  | { status: "ready"; doc: PDFDocumentProxy; baseWidth: number; baseHeight: number };

async function openDocument(blob: Blob): Promise<PDFDocumentProxy> {
  const pdfjs = await import("pdfjs-dist");
  pdfjs.GlobalWorkerOptions.workerSrc = PDF_WORKER_SRC;
  const data = new Uint8Array(await blob.arrayBuffer());
  return pdfjs.getDocument({ data }).promise;
}

export function PdfViewerImpl({ load, studentLine, allowDownload = false, fileName, label }: PdfViewerProps) {
  const { t, locale } = useLocale();
  const [state, setState] = useState<LoadState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [zoom, setZoom] = useState(1);
  const [width, setWidth] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [scroller, setScroller] = useState<HTMLDivElement | null>(null);
  const loadRef = useRef(load);

  useEffect(() => {
    loadRef.current = load;
  }, [load]);

  useEffect(() => {
    let cancelled = false;
    let opened: PDFDocumentProxy | null = null;
    (async () => {
      try {
        const file = await loadRef.current();
        const doc = await openDocument(file);
        opened = doc;
        const first = await doc.getPage(1);
        const viewport = first.getViewport({ scale: 1 });
        if (cancelled) {
          void doc.loadingTask.destroy();
          return;
        }
        setBlob(file);
        setState({ status: "ready", doc, baseWidth: viewport.width, baseHeight: viewport.height });
      } catch {
        if (!cancelled) {
          setState({ status: "error" });
        }
      }
    })();
    return () => {
      cancelled = true;
      if (opened) {
        void opened.loadingTask.destroy();
      }
    };
  }, [attempt]);

  useEffect(() => {
    if (!scroller) {
      return;
    }
    const measure = () => setWidth(scroller.clientWidth);
    measure();
    if (typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(measure);
    observer.observe(scroller);
    return () => observer.disconnect();
  }, [scroller]);

  const pageCount = state.status === "ready" ? state.doc.numPages : 0;
  const fitScale = state.status === "ready" && width > 0 ? (width - 16) / state.baseWidth : 1;
  const scale = fitScale * zoom;

  const scrollToPage = useCallback(
    (page: number) => {
      const target = scroller?.querySelector<HTMLElement>(`[data-page="${page}"]`);
      if (target && scroller) {
        scroller.scrollTo({ top: target.offsetTop - 8, behavior: "smooth" });
      }
      setCurrentPage(page);
    },
    [scroller],
  );

  const onScroll = useCallback(() => {
    const container = scroller;
    if (!container) {
      return;
    }
    const middle = container.scrollTop + container.clientHeight / 2;
    const pages = container.querySelectorAll<HTMLElement>("[data-page]");
    for (const page of pages) {
      if (page.offsetTop <= middle && page.offsetTop + page.offsetHeight >= middle) {
        setCurrentPage(Number(page.dataset.page));
        return;
      }
    }
  }, [scroller]);

  const { contentRef, onPointerDown, onPointerMove, onPointerEnd } = usePinchZoom(setZoom);

  if (state.status === "loading") {
    return (
      <div role="status" aria-live="polite">
        <span className="sr-only">{t("sitting.pdf.loading")}</span>
        <PdfSkeleton />
      </div>
    );
  }

  if (state.status === "error") {
    return (
      <div
        role="alert"
        className="flex min-h-[240px] flex-col items-center justify-center gap-4 rounded-card border border-border bg-card p-6 text-center"
      >
        <p className="text-text">{t("sitting.pdf.error")}</p>
        <button
          type="button"
          onClick={() => {
            setState({ status: "loading" });
            setAttempt((value) => value + 1);
          }}
          className="min-h-11 rounded-control border border-border px-4 font-medium text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        >
          {t("sitting.pdf.retry")}
        </button>
      </div>
    );
  }

  const n = (value: number) => formatNumber(value, locale);
  const control =
    "inline-flex min-h-11 min-w-11 items-center justify-center rounded-control border border-border bg-card px-3 text-text disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";

  return (
    <section
      aria-label={label ?? t("sitting.pdf.region")}
      className="overflow-hidden rounded-card border border-border bg-card"
      onContextMenu={allowDownload ? undefined : (event) => event.preventDefault()}
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border p-2">
        <div className="flex items-center gap-1">
          <button
            type="button"
            className={control}
            aria-label={t("sitting.pdf.previous")}
            disabled={currentPage <= 1}
            onClick={() => scrollToPage(currentPage - 1)}
          >
            <span aria-hidden="true">‹</span>
          </button>
          <span className="px-2 text-sm text-muted" aria-live="polite">
            {t("sitting.pdf.page", { page: n(currentPage), total: n(pageCount) })}
          </span>
          <button
            type="button"
            className={control}
            aria-label={t("sitting.pdf.next")}
            disabled={currentPage >= pageCount}
            onClick={() => scrollToPage(currentPage + 1)}
          >
            <span aria-hidden="true">›</span>
          </button>
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            className={control}
            aria-label={t("sitting.pdf.zoomOut")}
            onClick={() => setZoom((value) => clampZoom(value - ZOOM_STEP))}
          >
            <span aria-hidden="true">−</span>
          </button>
          <button type="button" className={control} onClick={() => setZoom(1)} aria-label={t("sitting.pdf.fit")}>
            <span className="text-sm">{t("sitting.pdf.zoom", { percent: n(Math.round(zoom * 100)) })}</span>
          </button>
          <button
            type="button"
            className={control}
            aria-label={t("sitting.pdf.zoomIn")}
            onClick={() => setZoom((value) => clampZoom(value + ZOOM_STEP))}
          >
            <span aria-hidden="true">+</span>
          </button>
          {allowDownload && blob ? <DownloadButton blob={blob} fileName={fileName} label={t("sitting.pdf.download")} /> : null}
        </div>
      </div>
      <div
        ref={setScroller}
        tabIndex={0}
        aria-label={label ?? t("sitting.pdf.region")}
        onScroll={onScroll}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerEnd}
        onPointerCancel={onPointerEnd}
        className="h-[70vh] max-h-[880px] min-h-[360px] touch-pan-x touch-pan-y overflow-auto bg-bg p-2 focus-visible:outline-2 focus-visible:outline-accent"
      >
        {/* Starts at the left edge and is as wide as the widest page, so a zoomed page scrolls to both sides. Each page centres itself (mx-auto) when it is narrower. */}
        <div ref={contentRef} className="flex min-w-max origin-top flex-col items-start gap-2">
          {width > 0
            ? Array.from({ length: pageCount }, (_, index) => (
                <PdfPage
                  key={index + 1}
                  doc={state.doc}
                  pageNumber={index + 1}
                  scale={scale}
                  placeholderWidth={state.baseWidth * scale}
                  placeholderHeight={state.baseHeight * scale}
                  studentLine={studentLine}
                  root={scroller}
                />
              ))
            : null}
        </div>
      </div>
    </section>
  );
}

/** Staff only. The object URL is made on click and freed after the browser has started the save. */
function DownloadButton({ blob, fileName, label }: { blob: Blob; fileName?: string; label: string }) {
  return (
    <button
      type="button"
      onClick={() => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = fileName ?? "file.pdf";
        link.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      }}
      className="inline-flex min-h-11 items-center rounded-control border border-border px-3 text-sm text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
    >
      {label}
    </button>
  );
}

type PageProps = {
  doc: PDFDocumentProxy;
  pageNumber: number;
  scale: number;
  placeholderWidth: number;
  placeholderHeight: number;
  studentLine?: string;
  root: HTMLDivElement | null;
};

/** One page. Renders only once it is near the visible area, and again when the scale changes. */
const PdfPage = memo(function PdfPage({
  doc,
  pageNumber,
  scale,
  placeholderWidth,
  placeholderHeight,
  studentLine,
  root,
}: PageProps) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  // Without IntersectionObserver (very old browsers) every page renders at once.
  const [near, setNear] = useState(() => typeof IntersectionObserver === "undefined");
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  useEffect(() => {
    const element = wrapperRef.current;
    if (!element || near) {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setNear(true);
          observer.disconnect();
        }
      },
      { root, rootMargin: "100% 0px" },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [root, near]);

  useEffect(() => {
    if (!near) {
      return;
    }
    let task: RenderTask | null = null;
    let cancelled = false;
    (async () => {
      const page = await doc.getPage(pageNumber);
      const canvas = canvasRef.current;
      if (cancelled || !canvas) {
        return;
      }
      const viewport = page.getViewport({ scale });
      const ratio = Math.min(window.devicePixelRatio || 1, MAX_PIXEL_RATIO);
      canvas.width = Math.floor(viewport.width * ratio);
      canvas.height = Math.floor(viewport.height * ratio);
      const context = canvas.getContext("2d");
      if (!context) {
        return;
      }
      setSize({ width: viewport.width, height: viewport.height });
      task = page.render({
        canvas,
        canvasContext: context,
        viewport,
        transform: ratio === 1 ? undefined : [ratio, 0, 0, ratio, 0, 0],
      });
      await task.promise.catch(() => undefined);
    })();
    return () => {
      cancelled = true;
      task?.cancel();
    };
  }, [doc, near, pageNumber, scale]);

  const width = size?.width ?? placeholderWidth;
  const height = size?.height ?? placeholderHeight;
  // A student's page has a strip above and below the paper for the name line; the paper sits between them.
  const strip = pageStrip(studentLine);

  return (
    <div
      ref={wrapperRef}
      data-page={pageNumber}
      className="relative mx-auto shrink-0 bg-white shadow-sm"
      style={{ width, height: height + 2 * strip }}
    >
      <canvas ref={canvasRef} className="absolute left-0 block" style={{ top: strip, width, height }} />
      <PdfWatermark studentLine={studentLine} />
    </div>
  );
});

/**
 * Two-finger pinch on touch screens. While the fingers move, the pages are scaled with a CSS
 * transform (cheap); on release the zoom is committed and the pages render sharp at the new size.
 */
function usePinchZoom(setZoom: (update: (value: number) => number) => void) {
  const contentRef = useRef<HTMLDivElement>(null);
  const pointers = useRef(new Map<number, { x: number; y: number }>());
  const startDistance = useRef(0);
  const ratio = useRef(1);

  const distance = () => {
    const [a, b] = [...pointers.current.values()];
    return Math.hypot(a.x - b.x, a.y - b.y);
  };

  const onPointerDown = (event: ReactPointerEvent) => {
    if (event.pointerType !== "touch") {
      return;
    }
    pointers.current.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (pointers.current.size === 2) {
      startDistance.current = distance();
      ratio.current = 1;
    }
  };

  const onPointerMove = (event: ReactPointerEvent) => {
    if (!pointers.current.has(event.pointerId)) {
      return;
    }
    pointers.current.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (pointers.current.size === 2 && startDistance.current > 0 && contentRef.current) {
      ratio.current = distance() / startDistance.current;
      contentRef.current.style.transform = `scale(${ratio.current})`;
    }
  };

  const onPointerEnd = (event: ReactPointerEvent) => {
    pointers.current.delete(event.pointerId);
    if (pointers.current.size < 2 && startDistance.current > 0) {
      const committed = ratio.current;
      startDistance.current = 0;
      ratio.current = 1;
      if (contentRef.current) {
        contentRef.current.style.transform = "";
      }
      if (Math.abs(committed - 1) > 0.05) {
        setZoom((value) => clampZoom(value * committed));
      }
    }
  };

  return { contentRef, onPointerDown, onPointerMove, onPointerEnd };
}
