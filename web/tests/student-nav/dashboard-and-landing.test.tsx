import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StudentShell } from "@/components/shell/student-shell";
import { HomePage } from "@/components/student/home/home-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

describe("the landing page and the dashboard (16 Nov)", () => {
  it("shows a signed-in student the landing page on /, with a way back to their dashboard", async () => {
    mockApi({});
    renderWithProviders(<HomePage />, { user: fixtures.student, locale: "en", route: "/" });

    expect(await screen.findByRole("link", { name: "Go to my dashboard" })).toHaveAttribute("href", "/dashboard");
  });

  it("gives the side menu a Dashboard item and a Home item that leads to the landing page", async () => {
    mockApi({ "GET /api/v1/me/notices/unread-count": { count: 0 } });
    renderWithProviders(
      <StudentShell>
        <p>page</p>
      </StudentShell>,
      { user: fixtures.student, locale: "en", route: "/dashboard" },
    );

    const menus = await screen.findAllByRole("navigation");
    const side = menus.find((nav) => within(nav).queryByRole("link", { name: "Wish List" }));
    expect(side).toBeDefined();
    const menu = within(side as HTMLElement);
    expect(menu.getByRole("link", { name: "Dashboard" })).toHaveAttribute("href", "/dashboard");
    expect(menu.getByRole("link", { name: "Home" })).toHaveAttribute("href", "/");
  });

  it("shows no side menu on the landing page", async () => {
    mockApi({ "GET /api/v1/me/notices/unread-count": { count: 0 } });
    renderWithProviders(
      <StudentShell>
        <p>page</p>
      </StudentShell>,
      { user: fixtures.student, locale: "en", route: "/" },
    );

    await screen.findByText("page");
    const menus = screen.queryAllByRole("navigation");
    expect(menus.some((nav) => within(nav).queryByRole("link", { name: "Wish List" }))).toBe(false);
  });
});
