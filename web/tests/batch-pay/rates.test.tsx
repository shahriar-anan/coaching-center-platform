import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BatchRates } from "@/components/staff/offline/batch-rates";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { classRow, milon, rahim } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

type Call = { method: string; path: string; body: unknown };

describe("instructor rates tab (plan 8.1, F7)", () => {
  it("lists instructors with classes but no rate first, and saves a rate", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/offline-batches/b1/rates": [{ instructorId: "i2", name: "Rahim Sir", rate: 500 }],
      "GET /api/v1/offline-batches/b1/classes": [
        classRow({ id: "c1", instructorId: "i1", instructorName: "Milon Hossain", status: "SCHEDULED" }),
        classRow({ id: "c2", instructorId: "i2", instructorName: "Rahim Sir", status: "PAID" }),
      ],
      "PUT /api/v1/offline-batches/b1/rates/i1": { instructorId: "i1", name: "Milon Hossain", rate: 600 },
      "GET /api/v1/offline-instructors": [milon, rahim],
    });
    renderWithProviders(<BatchRates batchId="b1" archived={false} />, { user: fixtures.masterAdmin, locale: "en" });

    const rateFields = await screen.findAllByRole("textbox", { name: /Default rate/ });
    expect(rateFields[0]).toHaveAccessibleName(/Milon Hossain/);
    expect(screen.getAllByText("Set a rate").length).toBeGreaterThan(0);
    expect(screen.getByText("A class can override this on its own row.")).toBeInTheDocument();

    await user.type(rateFields[0], "600{Enter}");

    expect(await screen.findAllByText("Saved ✓")).not.toHaveLength(0);
    const put = (api.calls as Call[]).find((call) => call.method === "PUT");
    expect(put?.path).toBe("/api/v1/offline-batches/b1/rates/i1");
    expect(put?.body).toEqual({ rate: "600" });
  });

  it("removes a rate with DELETE", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/offline-batches/b1/rates": [{ instructorId: "i2", name: "Rahim Sir", rate: 500 }],
      "GET /api/v1/offline-batches/b1/classes": [classRow({ id: "c2", instructorId: "i2", instructorName: "Rahim Sir", status: "PAID" })],
      "DELETE /api/v1/offline-batches/b1/rates/i2": null,
      "GET /api/v1/offline-instructors": [rahim],
    });
    renderWithProviders(<BatchRates batchId="b1" archived={false} />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click((await screen.findAllByRole("button", { name: "Remove rate" }))[0]);

    expect((api.calls as Call[]).some((call) => call.method === "DELETE" && call.path === "/api/v1/offline-batches/b1/rates/i2")).toBe(true);
  });
});
