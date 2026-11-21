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
import { errorText, reportClientEvent } from "@/lib/api/client-events";

/** What the viewer hands a loader: abort it when the viewer closes, and report bytes as they arrive. */
export type PdfLoadOptions = {
  signal: AbortSignal;
  onProgress: (received: number, total: number | null) => void;
};

/** The props `PdfViewer` (pdf-viewer.tsx) passes through. */
export type PdfViewerProps = {
  /**
   * Fetches the file with the caller's auth, for example `(o) => apiBlob(path, o)`. Called again on retry. Pass the
   * options on, so the download stops when the viewer closes and shows its progress.
   */
  load: (options: PdfLoadOptions) => Promise<Blob>;
  /**
   * Keeps the downloaded file in memory for the page session under this key (usually the API path), so switching tabs
   * or reopening a dialog does not download it again. Leave it out for files that change while the page is open.
   */
  cacheKey?: string;
  /** Fetches the file the Download button saves, when it differs from the one shown (the original upload). */
  download?: (options: PdfLoadOptions) => Promise<Blob>;
  /** Which kind of file this is, for failure reports only. */
  fileKind?: "question" | "answer" | "marked" | "routine" | "other";
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
import { cachedPdf, rememberPdf } from "./pdf-blob-cache";
import { PdfSkeleton } from "./pdf-skeleton";
import { PdfWatermark, pageStrip } from "./pdf-watermark";
import { clampZoom, MIN_ZOOM, ZOOM_STEP } from "./zoom";

export const PDF_WORKER_SRC = "/pdf.worker.min.mjs";

/** Where scripts/copy-pdfjs-assets.mjs puts pdf.js's decoders, CMaps, fonts and colour profiles. */
const PDFJS_ASSETS = "/pdfjs/";

/** Canvas pixels per CSS pixel. */
const MAX_PIXEL_RATIO = 2;
/**
 * Most canvas pixels one page may use. A scanned page zoomed in on a phone would otherwise need tens of megapixels,
 * and a phone that runs out of canvas memory shows blank pages or reloads the tab.
 */
const MAX_PAGE_PIXELS_TOUCH = 4_000_000;
const MAX_PAGE_PIXELS = 8_000_000;
/** After this many failed opens of a downloaded file, the viewer says retrying will not help in this browser. */
const OPEN_FAILURES_BEFORE_HINT = 2;

type LoadState =
  | { status: "loading"; received: number; total: number | null }
  | { status: "error"; stage: "download" | "open" }
  | { status: "ready"; doc: PDFDocumentProxy; baseWidth: number; baseHeight: number };

/**
 * The LEGACY pdf.js build: the modern one calls browser APIs that phones older than about Chrome 140 / Safari 18.2 and
 * in-app WebViews lack, so no PDF opened there at all (22 Nov 2026). The worker in public/ is the matching legacy worker.
 */
async function openDocument(blob: Blob): Promise<PDFDocumentProxy> {
  const pdfjs = await import("pdfjs-dist/legacy/build/pdf.mjs");
  pdfjs.GlobalWorkerOptions.workerSrc = PDF_WORKER_SRC;
  const data = new Uint8Array(await blob.arrayBuffer());
  const assets = new URL(PDFJS_ASSETS, window.location.origin).href;
  return pdfjs.getDocument({
    data,
    wasmUrl: `${assets}wasm/`,
    cMapUrl: `${assets}cmaps/`,
    cMapPacked: true,
    standardFontDataUrl: `${assets}standard_fonts/`,
    iccUrl: `${assets}iccs/`,
  }).promise as Promise<PDFDocumentProxy>;
}

function isAbort(error: unknown): boolean {
  return typeof error === "object" && error !== null && (error as { name?: unknown }).name === "AbortError";
}

export function PdfViewerImpl({
  load,
  cacheKey,
  download,
  fileKind = "other",
  studentLine,
  allowDownload = false,
  fileName,
  label,
}: PdfViewerProps) {
  const { t, locale } = useLocale();
  const [state, setState] = useState<LoadState>({ status: "loading", received: 0, total: null });
  const [attempt, setAttempt] = useState(0);
  const [openFailures, setOpenFailures] = useState(0);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [zoom, setZoom] = useState(1);
  const [width, setWidth] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [full, setFull] = useState(false);
  const [scroller, setScroller] = useState<HTMLDivElement | null>(null);
  const loadRef = useRef(load);
  const kindRef = useRef(fileKind);

  useEffect(() => {
    loadRef.current = load;
    kindRef.current = fileKind;
  }, [load, fileKind]);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;
    let opened: PDFDocumentProxy | null = null;
    (async () => {
      let file: Blob | undefined = cacheKey ? cachedPdf(cacheKey) : undefined;
      if (!file) {
        try {
          file = await loadRef.current({
            signal: controller.signal,
            onProgress: (received, total) => {
              if (!cancelled) {
                setState({ status: "loading", received, total });
              }
            },
          });
        } catch (error) {
          if (!cancelled && !isAbort(error)) {
            setState({ status: "error", stage: "download" });
            reportClientEvent({ kind: "pdf", stage: "download", file: kindRef.current, message: errorText(error) });
          }
          return;
        }
        if (cancelled) {
          return;
        }
        if (cacheKey) {
          rememberPdf(cacheKey, file);
        }
      }
      try {
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
      } catch (error) {
        if (!cancelled) {
          setOpenFailures((value) => value + 1);
          setState({ status: "error", stage: "open" });
          reportClientEvent({ kind: "pdf", stage: "open", file: kindRef.current, message: errorText(error) });
        }
      }
    })();
    return () => {
      cancelled = true;
      // Closing the viewer stops its download, so an abandoned 20 MB script does not compete with the next one.
      controller.abort();
      if (opened) {
        void opened.loadingTask.destroy();
      }
    };
  }, [attempt, cacheKey]);

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

  // Full screen is an overlay over the page, not the browser's own full screen, so it works on phones too and the
  // paper does not reload. Esc leaves it, and the page behind does not scroll while it is open.
  useEffect(() => {
    if (!full) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setFull(false);
      }
    };
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = previous;
    };
  }, [full]);

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

  const n = (value: number) => formatNumber(value, locale);
  const mb = (bytes: number) => formatNumber(Math.round((bytes / 1048576) * 10) / 10, locale);

  if (state.status === "loading") {
    const text =
      state.received > 0
        ? state.total
          ? t("sitting.pdf.downloading", { received: mb(state.received), total: mb(state.total) })
          : t("sitting.pdf.downloadingUnknown", { received: mb(state.received) })
        : t("sitting.pdf.loading");
    const percent = state.total ? Math.min(100, Math.round((state.received / state.total) * 100)) : null;
    return (
      <div role="status" aria-live="polite" className="relative">
        <PdfSkeleton />
        <div className="absolute inset-x-0 top-6 mx-auto flex w-fit max-w-[90%] flex-col items-center gap-2 rounded-card border border-border bg-card px-4 py-3 text-center shadow-sm">
          <span className="text-sm text-text">{text}</span>
          {percent !== null ? (
            <span className="block h-1.5 w-48 max-w-full overflow-hidden rounded-full bg-border" aria-hidden="true">
              <span className="block h-full bg-accent transition-[width]" style={{ width: `${percent}%` }} />
            </span>
          ) : null}
        </div>
      </div>
    );
  }

  if (state.status === "error") {
    const browserProblem = state.stage === "open";
    const hopeless = browserProblem && openFailures >= OPEN_FAILURES_BEFORE_HINT;
    return (
      <div
        role="alert"
        className="flex min-h-[240px] flex-col items-center justify-center gap-3 rounded-card border border-border bg-card p-6 text-center"
      >
        <p className="font-medium text-text">{browserProblem ? t("sitting.pdf.errorOpen") : t("sitting.pdf.errorDownload")}</p>
        {browserProblem ? <p className="max-w-prose text-sm text-muted">{t("sitting.pdf.openHint")}</p> : null}
        {hopeless ? <p className="max-w-prose text-sm text-muted">{t("sitting.pdf.retryNoHelp")}</p> : null}
        <div className="flex flex-wrap items-center justify-center gap-2">
          <button
            type="button"
            onClick={() => {
              setState({ status: "loading", received: 0, total: null });
              setAttempt((value) => value + 1);
            }}
            className="min-h-11 rounded-control border border-border px-4 font-medium text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          >
            {t("sitting.pdf.retry")}
          </button>
          {browserProblem ? <CopyLinkButton label={t("sitting.pdf.copyLink")} done={t("sitting.pdf.linkCopied")} /> : null}
        </div>
      </div>
    );
  }

  const control =
    "inline-flex min-h-11 min-w-11 items-center justify-center rounded-control border border-border bg-card px-3 text-text disabled:opacity-40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";

  return (
    <section
      aria-label={label ?? t("sitting.pdf.region")}
      className={
        full
          ? "fixed inset-0 z-[60] flex flex-col overflow-hidden bg-card"
          : "overflow-hidden rounded-card border border-border bg-card"
      }
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
          <button
            type="button"
            className={control}
            aria-label={full ? t("sitting.pdf.exitFullScreen") : t("sitting.pdf.fullScreen")}
            aria-pressed={full}
            onClick={() => {
              // Full screen opens at 50% so a whole page is in view; leaving it does not change the zoom.
              if (!full) setZoom(MIN_ZOOM);
              setFull((value) => !value);
            }}
          >
            <FullScreenIcon exit={full} />
          </button>
          {allowDownload && blob ? (
            <DownloadButton
              blob={blob}
              download={download}
              fileName={fileName}
              label={t("sitting.pdf.download")}
              busyLabel={t("sitting.pdf.preparingDownload")}
            />
          ) : null}
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
        className={
          full
            ? "min-h-0 flex-1 scroll-clean touch-pan-x touch-pan-y overflow-auto bg-bg p-2 focus-visible:outline-2 focus-visible:outline-accent"
            : "h-[70vh] max-h-[880px] min-h-[360px] scroll-clean touch-pan-x touch-pan-y overflow-auto bg-bg p-2 focus-visible:outline-2 focus-visible:outline-accent"
        }
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
                  fileKind={fileKind}
                />
              ))
            : null}
        </div>
      </div>
    </section>
  );
}

/** Four corners pointing out to enter full screen, and in to leave it. */
function FullScreenIcon({ exit }: { exit: boolean }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      {exit ? (
        <path d="M7 2v4a1 1 0 0 1-1 1H2M13 2v4a1 1 0 0 0 1 1h4M7 18v-4a1 1 0 0 0-1-1H2M13 18v-4a1 1 0 0 1 1-1h4" />
      ) : (
        <path d="M2 7V3a1 1 0 0 1 1-1h4M18 7V3a1 1 0 0 0-1-1h-4M2 13v4a1 1 0 0 0 1 1h4M18 13v4a1 1 0 0 1-1 1h-4" />
      )}
    </svg>
  );
}

/** For a browser that cannot show the PDF: copies the page link, to paste into Chrome. */
function CopyLinkButton({ label, done }: { label: string; done: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      type="button"
      onClick={() => {
        const href = window.location.href;
        const finish = () => setCopied(true);
        if (navigator.clipboard?.writeText) {
          navigator.clipboard.writeText(href).then(finish, () => window.prompt(label, href));
        } else {
          window.prompt(label, href);
        }
      }}
      className="min-h-11 rounded-control border border-border px-4 font-medium text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
    >
      {copied ? done : label}
    </button>
  );
}

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/**
 * Saves the file. With `download`, it fetches that file first (the original upload, when the viewer shows a smaller
 * copy); without it, it saves the file on screen. The object URL is freed after the browser has started the save.
 */
function DownloadButton({
  blob,
  download,
  fileName,
  label,
  busyLabel,
}: {
  blob: Blob;
  download?: PdfViewerProps["download"];
  fileName?: string;
  label: string;
  busyLabel: string;
}) {
  const [busy, setBusy] = useState(false);
  const name = fileName ?? "file.pdf";
  return (
    <button
      type="button"
      disabled={busy}
      onClick={async () => {
        if (!download) {
          saveBlob(blob, name);
          return;
        }
        setBusy(true);
        try {
          saveBlob(await download({ signal: new AbortController().signal, onProgress: () => undefined }), name);
        } catch {
          // The original could not be fetched: the copy on screen is still better than nothing.
          saveBlob(blob, name);
        } finally {
          setBusy(false);
        }
      }}
      className="inline-flex min-h-11 items-center rounded-control border border-border px-3 text-sm text-text disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
    >
      {busy ? busyLabel : label}
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
  fileKind: NonNullable<PdfViewerProps["fileKind"]>;
};

/** Canvas pixels per CSS pixel for one page: the screen's ratio, capped so the page stays inside the pixel budget. */
function pixelRatio(width: number, height: number): number {
  const coarse = typeof window.matchMedia === "function" && window.matchMedia("(pointer: coarse)").matches;
  const budget = coarse ? MAX_PAGE_PIXELS_TOUCH : MAX_PAGE_PIXELS;
  const fit = Math.sqrt(budget / Math.max(1, width * height));
  return Math.max(0.5, Math.min(window.devicePixelRatio || 1, MAX_PIXEL_RATIO, fit));
}

let renderFailuresReported = false;

/**
 * One page. It draws while it is within about two screens of the visible area and frees its canvas when it moves
 * further away, so a 38-page scan holds a handful of canvases instead of all of them. It draws again when the scale
 * changes or it comes back into range.
 */
const PdfPage = memo(function PdfPage({
  doc,
  pageNumber,
  scale,
  placeholderWidth,
  placeholderHeight,
  studentLine,
  root,
  fileKind,
}: PageProps) {
  const wrapperRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  /** Whether the canvas holds a drawn page that can be freed. */
  const drawnRef = useRef(false);
  // Without IntersectionObserver (very old browsers) every page renders at once.
  const [near, setNear] = useState(() => typeof IntersectionObserver === "undefined");
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

  useEffect(() => {
    const element = wrapperRef.current;
    if (!element || typeof IntersectionObserver === "undefined") {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        const latest = entries[entries.length - 1];
        if (latest) {
          setNear(latest.isIntersecting);
        }
      },
      { root, rootMargin: "200% 0px" },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [root]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!near) {
      // Out of range: give the canvas memory back. The wrapper keeps its size, so the scroll position does not jump.
      if (canvas && drawnRef.current) {
        drawnRef.current = false;
        canvas.width = 0;
        canvas.height = 0;
        void doc
          .getPage(pageNumber)
          .then((page) => page.cleanup())
          .catch(() => undefined);
      }
      return;
    }
    let task: RenderTask | null = null;
    let cancelled = false;
    (async () => {
      try {
        const page = await doc.getPage(pageNumber);
        const target = canvasRef.current;
        if (cancelled || !target) {
          return;
        }
        const viewport = page.getViewport({ scale });
        const ratio = pixelRatio(viewport.width, viewport.height);
        target.width = Math.floor(viewport.width * ratio);
        target.height = Math.floor(viewport.height * ratio);
        const context = target.getContext("2d");
        if (!context) {
          return;
        }
        setSize({ width: viewport.width, height: viewport.height });
        drawnRef.current = true;
        task = page.render({
          canvas: target,
          canvasContext: context,
          viewport,
          transform: ratio === 1 ? undefined : [ratio, 0, 0, ratio, 0, 0],
        });
        await task.promise;
      } catch (error) {
        const name = error instanceof Error ? error.name : "";
        if (!cancelled && name !== "RenderingCancelledException" && !renderFailuresReported) {
          renderFailuresReported = true;
          reportClientEvent({ kind: "pdf", stage: "render", file: fileKind, message: errorText(error) });
        }
      }
    })();
    return () => {
      cancelled = true;
      task?.cancel();
    };
  }, [doc, near, pageNumber, scale, fileKind]);

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
