import { fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PdfDocument } from "@/components/pdf/pdf-document";
import { renderWithProviders } from "../helpers";

const load = vi.fn(async () => new Blob(["%PDF-1.7"], { type: "application/pdf" }));

vi.mock("pdfjs-dist", () => ({
  GlobalWorkerOptions: { workerSrc: "" },
  getDocument: () => ({
    promise: Promise.resolve({
      numPages: 2,
      loadingTask: { destroy: async () => undefined },
      getPage: async () => ({
        getViewport: ({ scale }: { scale: number }) => ({ width: 600 * scale, height: 800 * scale }),
        render: () => ({ promise: Promise.resolve(), cancel: vi.fn() }),
      }),
    }),
  }),
}));

beforeEach(() => {
  load.mockClear();
  document.body.style.overflow = "";
  Object.defineProperty(HTMLElement.prototype, "clientWidth", { configurable: true, get: () => 616 });
  HTMLCanvasElement.prototype.getContext = vi.fn(() => ({}) as CanvasRenderingContext2D) as never;
});

describe("the paper in full screen", () => {
  it("covers the page, locks the page behind it, and leaves again with the button or Esc, without reloading the file", async () => {
    const user = userEvent.setup();
    renderWithProviders(<PdfDocument load={load} />);
    const region = await screen.findByRole("region", { name: "প্রশ্নপত্র" });
    expect(region).not.toHaveClass("fixed");

    await user.click(screen.getByRole("button", { name: "ফুল স্ক্রিন" }));
    expect(region).toHaveClass("fixed", "inset-0");
    expect(document.body.style.overflow).toBe("hidden");

    await user.click(screen.getByRole("button", { name: "ফুল স্ক্রিন থেকে বের হন" }));
    expect(region).not.toHaveClass("fixed");
    expect(document.body.style.overflow).toBe("");

    await user.click(screen.getByRole("button", { name: "ফুল স্ক্রিন" }));
    fireEvent.keyDown(window, { key: "Escape" });
    expect(region).not.toHaveClass("fixed");
    expect(load).toHaveBeenCalledTimes(1);
  });

  it("gives a student no download and no context menu in full screen either", async () => {
    const user = userEvent.setup();
    renderWithProviders(<PdfDocument load={load} />);
    const region = await screen.findByRole("region", { name: "প্রশ্নপত্র" });
    await user.click(screen.getByRole("button", { name: "ফুল স্ক্রিন" }));
    expect(region).toHaveClass("fixed");
    expect(screen.queryByText("ডাউনলোড করুন")).not.toBeInTheDocument();
    const event = new MouseEvent("contextmenu", { bubbles: true, cancelable: true });
    region.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
  });
});
