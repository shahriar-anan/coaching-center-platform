import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BatchDetail } from "@/components/staff/offline/batch-detail";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { routerState } from "../helpers/router";
import { batch } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

function routes() {
  return {
    "GET /api/v1/offline-batches/b1": batch,
    "GET /api/v1/offline-batches/b1/students": [],
    "GET /api/v1/offline-batches/b1/routines": [],
    "GET /api/v1/offline-batches/b1/classes": [],
    "GET /api/v1/offline-batches/b1/rates": [],
    "GET /api/v1/offline-instructors": [],
  };
}

describe("batch page tabs (plan 8.1)", () => {
  it("shows the five tabs and opens the one named in the URL", async () => {
    mockApi(routes());
    renderWithProviders(<BatchDetail id="b1" />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/offline-batches/b1?tab=routines",
    });

    expect(await screen.findByRole("tab", { name: "Routines" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "Overview" })).toHaveAttribute("aria-selected", "false");
    expect(screen.getByRole("tab", { name: "Classes" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Instructor rates" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Students" })).toBeInTheDocument();
    expect(await screen.findByText("No routine yet")).toBeInTheDocument();
  });

  it("shows the details on Overview when the URL names no tab", async () => {
    mockApi(routes());
    renderWithProviders(<BatchDetail id="b1" />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-batches/b1" });

    expect(await screen.findByRole("heading", { name: "Details" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Overview" })).toHaveAttribute("aria-selected", "true");
  });

  it("writes the chosen tab into the URL", async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderWithProviders(<BatchDetail id="b1" />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-batches/b1" });

    await user.click(await screen.findByRole("tab", { name: "Instructor rates" }));

    expect(routerState.replace).toHaveBeenCalledWith(expect.stringContaining("tab=rates"), expect.anything());
  });
});
