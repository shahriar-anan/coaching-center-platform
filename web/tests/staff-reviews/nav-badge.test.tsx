import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StaffShell } from "@/components/shell/staff-shell";
import type { Me } from "@/lib/api/exam/types";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

function renderShell(user: Me) {
  const api = mockApi({
    "GET /api/v1/staff/reviews/pending-count": { count: 4 },
    "GET /api/v1/live-exam-pending-changes": { items: [] },
  });
  renderWithProviders(
    <StaffShell>
      <p>page</p>
    </StaffShell>,
    { user, route: "/staff", locale: "en" },
  );
  return api;
}

describe("the Reviews item in the staff sidebar", () => {
  it.each([
    ["the Master Admin", fixtures.masterAdmin],
    ["a System Admin", fixtures.systemAdmin],
  ])("%s sees the item with the count of reviews waiting", async (_label, user) => {
    const api = renderShell(user);
    const link = await screen.findByRole("link", { name: /Reviews/ });
    expect(await within(link).findByText("4 reviews waiting for approval")).toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/staff/reviews/pending-count")).toBe(true);
  });

  it("a teacher sees the item with no count, and the count is never asked for", async () => {
    const api = renderShell(fixtures.instructor);
    const link = await screen.findByRole("link", { name: "Reviews" });
    expect(link).toHaveAttribute("href", "/staff/reviews");
    expect(within(link).queryByText(/reviews waiting for approval/)).toBeNull();
    expect(api.calls.some((call) => call.path === "/api/v1/staff/reviews/pending-count")).toBe(false);
  });

  it("a System Admin sees Announcements, which the Master Admin also sees", async () => {
    renderShell(fixtures.systemAdmin);
    expect(await screen.findByRole("link", { name: "Announcements" })).toHaveAttribute("href", "/staff/announcements");
  });
});
