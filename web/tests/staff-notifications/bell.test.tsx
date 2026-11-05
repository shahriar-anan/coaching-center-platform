import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StaffNotificationBell } from "@/components/staff/notifications/staff-notification-bell";
import { StaffShell } from "@/components/shell/staff-shell";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

describe("the staff header bell", () => {
  it("shows the unread count in its label and as a badge", async () => {
    const api = mockApi({ "GET /api/v1/me/staff-notifications/unread-count": { count: 3 } });
    renderWithProviders(<StaffNotificationBell enabled />, { user: fixtures.masterAdmin, locale: "en", route: "/staff" });

    const link = await screen.findByRole("link", { name: "Notifications, 3 unread" });
    expect(link).toHaveAttribute("href", "/staff/notifications");
    expect(within(link).getByText("3")).toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/me/staff-notifications/unread-count")).toBe(true);
  });

  it("shows the plain label, with no badge, when nothing is unread", async () => {
    mockApi({ "GET /api/v1/me/staff-notifications/unread-count": { count: 0 } });
    renderWithProviders(<StaffNotificationBell enabled />, { user: fixtures.systemAdmin, locale: "en", route: "/staff" });

    const link = await screen.findByRole("link", { name: "Notifications" });
    expect(link.querySelector("span")).toBeNull();
  });

  it("never asks for the count when it is not enabled", async () => {
    const api = mockApi({ "GET /api/v1/me/staff-notifications/unread-count": { count: 9 } });
    renderWithProviders(<StaffNotificationBell enabled={false} />, { user: fixtures.student, locale: "en", route: "/" });

    expect(await screen.findByRole("link", { name: "Notifications" })).toHaveAttribute("href", "/staff/notifications");
    expect(api.calls.some((call) => call.path.endsWith("/unread-count"))).toBe(false);
  });
});

describe("the bell in the staff header", () => {
  it("sits in the header of every staff page, for an instructor too", async () => {
    mockApi({
      "GET /api/v1/me/staff-notifications/unread-count": { count: 2 },
      "GET /api/v1/live-exam-pending-changes": { items: [] },
    });
    renderWithProviders(
      <StaffShell>
        <p>page</p>
      </StaffShell>,
      { user: fixtures.instructor, locale: "en", route: "/staff" },
    );

    expect(await screen.findByRole("link", { name: "Notifications, 2 unread" })).toHaveAttribute("href", "/staff/notifications");
  });
});
