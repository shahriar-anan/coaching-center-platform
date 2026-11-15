import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BatchDetail } from "@/components/staff/offline/batch-detail";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { batch } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

/*
 * Batches are drafts until the Master Admin publishes them (V103). A draft can be edited and published; a published batch's form is locked
 * until it is moved back to draft.
 */
function routes(published: boolean) {
  return mockApi({
    "GET /api/v1/offline-batches/b1": { ...batch, published },
    "GET /api/v1/offline-batches/b1/students": [],
    "GET /api/v1/offline-batches/b1/routines": [],
    "GET /api/v1/offline-batches/b1/classes": [],
    "GET /api/v1/offline-batches/b1/rates": [],
    "GET /api/v1/offline-instructors": [],
    "POST /api/v1/offline-batches/b1/publish": { ...batch, published: true },
    "POST /api/v1/offline-batches/b1/unpublish": { ...batch, published: false },
  });
}

describe("publishing a batch (V103)", () => {
  it("a draft shows Edit and Publish, says students do not see it, and Publish posts", async () => {
    const user = userEvent.setup();
    const api = routes(false);
    renderWithProviders(<BatchDetail id="b1" />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-batches/b1" });

    expect(await screen.findByText(/Students do not see this batch/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit details" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Publish" }));
    await waitFor(() => expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith("/publish"))).toBe(true));
  });

  it("a published batch has no Edit, only Move to draft", async () => {
    const user = userEvent.setup();
    const api = routes(true);
    renderWithProviders(<BatchDetail id="b1" />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-batches/b1" });

    expect(await screen.findByText(/Students see this batch/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit details" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Move to draft" }));
    await waitFor(() => expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith("/unpublish"))).toBe(true));
  });

  it("staff see the state but no Publish or Move to draft", async () => {
    routes(false);
    renderWithProviders(<BatchDetail id="b1" />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/offline-batches/b1" });

    expect(await screen.findByText(/Students do not see this batch/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Publish" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Move to draft" })).not.toBeInTheDocument();
  });
});
