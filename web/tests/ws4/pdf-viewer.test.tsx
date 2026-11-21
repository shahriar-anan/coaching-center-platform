import { readFileSync } from "node:fs";
import path from "node:path";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PdfDocument, PDF_WORKER_SRC } from "@/components/pdf/pdf-document";
import { clampZoom } from "@/components/pdf/zoom";
import { renderWithProviders as renderWs4 } from "../helpers";

const render = vi.fn(() => ({ promise: Promise.resolve(), cancel: vi.fn() }));
const destroy = vi.fn(async () => undefined);
const workerOptions = { workerSrc: "" };

const getDocument = vi.fn((options: Record<string, unknown>) => ({
  options,
  promise: Promise.resolve({
    numPages: 3,
    destroy,
    loadingTask: { destroy },
    getPage: async () => ({
      getViewport: ({ scale }: { scale: number }) => ({ width: 600 * scale, height: 800 * scale }),
      render,
      cleanup: vi.fn(() => true),
    }),
  }),
}));

// The viewer loads the legacy build: the modern one fails on browsers older than about Chrome 140 (22 Nov 2026).
vi.mock("pdfjs-dist/legacy/build/pdf.mjs", () => ({
  GlobalWorkerOptions: workerOptions,
  getDocument: (options: Record<string, unknown>) => getDocument(options),
}));

function pdfBlob() {
  return new Blob(["%PDF-1.7"], { type: "application/pdf" });
}

beforeEach(() => {
  render.mockClear();
  Object.defineProperty(HTMLElement.prototype, "clientWidth", { configurable: true, get: () => 616 });
  HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}) as CanvasRenderingContext2D) as never;
});

describe("PdfViewer", () => {
  it("shows the pages, page count, and the mark on each page, and loads the worker from the site", async () => {
    renderWs4(<PdfDocument load={async () => pdfBlob()} />);
    expect(await screen.findByText("পৃষ্ঠা ১ / ৩")).toBeInTheDocument();
    expect(await screen.findAllByTestId("pdf-watermark")).toHaveLength(3);
    expect(screen.queryByText("নাঈম হাসান · S18492017")).not.toBeInTheDocument();
    expect(workerOptions.workerSrc).toBe(PDF_WORKER_SRC);
    await waitFor(() => expect(render).toHaveBeenCalled());
  });

  it("prints a student's name and ID once at the top and once at the bottom of every page", async () => {
    renderWs4(<PdfDocument load={async () => pdfBlob()} studentLine="নাঈম হাসান · S18492017" />);
    expect(await screen.findByText("পৃষ্ঠা ১ / ৩")).toBeInTheDocument();
    expect(await screen.findAllByTestId("pdf-watermark")).toHaveLength(3);
    expect(screen.getAllByText("নাঈম হাসান · S18492017")).toHaveLength(6);
  });

  it("gives students no download and suppresses the context menu", async () => {
    renderWs4(<PdfDocument load={async () => pdfBlob()} />);
    const region = await screen.findByRole("region", { name: "প্রশ্নপত্র" });
    expect(screen.queryByText("ডাউনলোড করুন")).not.toBeInTheDocument();
    const event = new MouseEvent("contextmenu", { bubbles: true, cancelable: true });
    region.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
  });

  it("offers a download to staff, which saves under the file name", async () => {
    const clicks: HTMLAnchorElement[] = [];
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
      clicks.push(this);
    });
    URL.createObjectURL = vi.fn(() => "blob:script");
    const user = userEvent.setup();
    renderWs4(<PdfDocument load={async () => pdfBlob()} allowDownload fileName="script.pdf" />, { locale: "en" });
    await user.click(await screen.findByRole("button", { name: "Download" }));
    expect(clicks[0]).toHaveAttribute("download", "script.pdf");
    expect(clicks[0]).toHaveAttribute("href", "blob:script");
    click.mockRestore();
  });

  it("zooms with the buttons and resets to fit", async () => {
    const user = userEvent.setup();
    renderWs4(<PdfDocument load={async () => pdfBlob()} />, { locale: "en" });
    await user.click(await screen.findByRole("button", { name: "Zoom in" }));
    expect(screen.getByText("125%")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Fit to width" }));
    expect(screen.getByText("100%")).toBeInTheDocument();
  });

  it("shows an error with retry when the file cannot load", async () => {
    const load = vi.fn().mockRejectedValueOnce(new Error("offline")).mockResolvedValue(pdfBlob());
    renderWs4(<PdfDocument load={load} />);
    fireEvent.click(await screen.findByRole("button", { name: "আবার চেষ্টা করুন" }));
    expect(await screen.findByText("পৃষ্ঠা ১ / ৩")).toBeInTheDocument();
    expect(load).toHaveBeenCalledTimes(2);
  });

  it("tells a browser problem apart from a download problem", async () => {
    getDocument.mockImplementationOnce(() => ({ promise: Promise.reject(new TypeError("hashOriginal.toHex is not a function")) }) as never);
    renderWs4(<PdfDocument load={async () => pdfBlob()} />, { locale: "en" });
    expect(await screen.findByText("This browser cannot open the PDF.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copy page link" })).toBeInTheDocument();
  });

  it("says a failed download is a connection problem", async () => {
    renderWs4(<PdfDocument load={() => Promise.reject(new TypeError("Failed to fetch"))} />, { locale: "en" });
    expect(await screen.findByText(/could not be downloaded/)).toBeInTheDocument();
  });

  it("shows download progress", async () => {
    let finish: (blob: Blob) => void = () => undefined;
    renderWs4(
      <PdfDocument
        load={({ onProgress }) => {
          onProgress(5 * 1048576, 20 * 1048576);
          return new Promise<Blob>((resolve) => {
            finish = resolve;
          });
        }}
      />,
      { locale: "en" },
    );
    expect(await screen.findByText("Downloading… 5 of 20 MB")).toBeInTheDocument();
    finish(pdfBlob());
    expect(await screen.findByText("Page 1 of 3")).toBeInTheDocument();
  });

  it("aborts the download when the viewer closes", async () => {
    let signal: AbortSignal | null = null;
    const view = renderWs4(
      <PdfDocument
        load={(options) => {
          signal = options.signal;
          return new Promise<Blob>(() => undefined);
        }}
      />,
    );
    await waitFor(() => expect(signal).not.toBeNull());
    view.unmount();
    expect(signal!.aborted).toBe(true);
  });

  it("reuses a cached file instead of downloading it again", async () => {
    const load = vi.fn(async () => pdfBlob());
    const first = renderWs4(<PdfDocument load={load} cacheKey="/api/v1/test/answer-pdf" />, { locale: "en" });
    expect(await screen.findByText("Page 1 of 3")).toBeInTheDocument();
    first.unmount();
    renderWs4(<PdfDocument load={load} cacheKey="/api/v1/test/answer-pdf" />, { locale: "en" });
    expect(await screen.findByText("Page 1 of 3")).toBeInTheDocument();
    expect(load).toHaveBeenCalledTimes(1);
  });

  it("passes pdf.js the decoder, CMap and font locations", async () => {
    renderWs4(<PdfDocument load={async () => pdfBlob()} />);
    await waitFor(() => expect(getDocument).toHaveBeenCalled());
    const options = getDocument.mock.calls.at(-1)![0];
    expect(String(options.wasmUrl)).toMatch(/\/pdfjs\/wasm\/$/);
    expect(String(options.cMapUrl)).toMatch(/\/pdfjs\/cmaps\/$/);
    expect(String(options.standardFontDataUrl)).toMatch(/\/pdfjs\/standard_fonts\/$/);
  });

  it("keeps zoom in range", () => {
    expect(clampZoom(0.1)).toBe(0.5);
    expect(clampZoom(9)).toBe(3);
    expect(clampZoom(1.2345)).toBe(1.23);
  });

  it("ships a worker that matches the installed pdfjs-dist version", () => {
    const root = path.resolve(__dirname, "../..");
    const pkg = JSON.parse(readFileSync(path.join(root, "node_modules/pdfjs-dist/package.json"), "utf8"));
    const worker = readFileSync(path.join(root, "public/pdf.worker.min.mjs"), "utf8");
    expect(worker).toContain(pkg.version);
    // The legacy worker, to match the legacy API the viewer imports.
    const legacy = readFileSync(path.join(root, "node_modules/pdfjs-dist/legacy/build/pdf.worker.min.mjs"), "utf8");
    expect(worker.endsWith(legacy)).toBe(true);
  });
});
