import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import StaffModulesPage from "@/app/(staff)/staff/modules/page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

/** The not-found page RequireStaff shows for a role the page does not admit. */
const NOT_FOUND = "Page not found";

describe("the package list refuses an instructor", () => {
  it("shows the not-found page to an instructor who types the address", async () => {
    mockApi({});
    renderWithProviders(<StaffModulesPage />, { user: fixtures.instructor, locale: "en", route: "/staff/modules" });

    expect(await screen.findByText(NOT_FOUND)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Packages" })).not.toBeInTheDocument();
  });

  it("still shows the package list to a Master Admin", async () => {
    mockApi({ "GET /api/v1/live-exam-modules": { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } });
    renderWithProviders(<StaffModulesPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/modules" });

    expect(await screen.findByRole("heading", { name: "Packages" })).toBeInTheDocument();
    expect(screen.queryByText(NOT_FOUND)).not.toBeInTheDocument();
  });
});
