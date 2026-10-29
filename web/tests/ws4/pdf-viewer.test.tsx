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

vi.mock("pdfjs-dist", () => ({
  GlobalWorkerOptions: workerOptions,
  getDocument: () => ({
    promise: Promise.resolve({
      numPages: 3,
      loadingTask: { destroy },
      getPage: async () => ({
        getViewport: ({ scale }: { scale: number }) => ({ width: 600 * scale, height: 800 * scale }),
        render,
      }),
    }),
  }),
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
  });
});
