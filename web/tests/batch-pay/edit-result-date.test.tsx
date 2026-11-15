import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ClassEditDialog } from "@/components/staff/offline/class-dialogs";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { classRow } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

const PATCH = "PATCH /api/v1/offline-classes/c1";

describe("Edit class: an exam with a result (F7)", () => {
  it("shows the date read-only, with the reason, and saves the other fields without a date", async () => {
    const user = userEvent.setup();
    const api = mockApi({ [PATCH]: classRow({ id: "c1", subject: "Constitution", hasResult: true }) });
    const item = classRow({ id: "c1", subject: "Constitution", heldOn: "2026-11-02", hasResult: true });
    renderWithProviders(<ClassEditDialog item={item} open onOpenChange={vi.fn()} />, { user: fixtures.masterAdmin, locale: "en" });

    const date = screen.getByLabelText("Date");
    expect(date).toHaveAttribute("readonly");
    expect(date).toHaveValue("2026-11-02");
    expect(screen.getByText("This exam has a result, so its date cannot change.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Save class" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "PATCH")).toBe(true));
    const body = api.calls.find((call) => call.method === "PATCH")?.body as Record<string, unknown>;
    expect(body).not.toHaveProperty("heldOn");
    expect(body.subject).toBe("Constitution");
  });

  it("keeps the date editable for an exam without a result", () => {
    const item = classRow({ id: "c1", subject: "Constitution", heldOn: "2026-11-02", hasResult: false });
    renderWithProviders(<ClassEditDialog item={item} open onOpenChange={vi.fn()} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(screen.getByLabelText("Date")).not.toHaveAttribute("readonly");
    expect(screen.queryByText("This exam has a result, so its date cannot change.")).toBeNull();
  });
});
