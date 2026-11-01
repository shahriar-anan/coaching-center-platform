import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AdminHome } from "@/components/staff/home/admin-home";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const counts = {
  "GET /api/v1/live-exam-categories": [{ id: "c1", parentId: null, titleEn: "BCS", titleBn: "বিজেএস", position: 1, archived: false, children: [], modules: [] }],
  "GET /api/v1/live-exam-participation/summary": { awaitingMarking: 0, draftsAwaitingPublish: 0 },
  "GET /api/v1/live-exam-participation": { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
  "GET /api/v1/live-exam-orders": { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
};

describe("the admin home tabs for a System Admin", () => {
  it("has no submitted answer sheets tab", async () => {
    mockApi(counts);
    renderWithProviders(<AdminHome isMasterAdmin={false} />, { user: fixtures.systemAdmin, locale: "en" });

    const tabs = await screen.findByRole("tablist");
    expect(within(tabs).getByRole("tab", { name: "Exams" })).toHaveAttribute("aria-selected", "true");
    expect(within(tabs).getByRole("tab", { name: "Payment management" })).toBeInTheDocument();
    expect(within(tabs).queryByRole("tab", { name: "Submitted answer sheets" })).toBeNull();
  });

  it("falls back to the exams tab when the URL asks for submissions", async () => {
    mockApi(counts);
    renderWithProviders(<AdminHome isMasterAdmin={false} />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: "/staff?tab=submissions",
    });

    const tabs = await screen.findByRole("tablist");
    expect(within(tabs).getByRole("tab", { name: "Exams" })).toHaveAttribute("aria-selected", "true");
    expect(within(tabs).queryByRole("tab", { name: "Submitted answer sheets" })).toBeNull();
  });

  it("the Master Admin keeps the submitted answer sheets tab", async () => {
    mockApi(counts);
    renderWithProviders(<AdminHome isMasterAdmin />, { user: fixtures.masterAdmin, locale: "en" });

    const tabs = await screen.findByRole("tablist");
    expect(within(tabs).getByRole("tab", { name: "Submitted answer sheets" })).toBeInTheDocument();
  });
});
