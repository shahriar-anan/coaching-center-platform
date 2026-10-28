import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { StudentShell } from "@/components/shell/student-shell";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const TOP_BAR = "Student menu";

function renderShell(route: string, user: typeof fixtures.student | null) {
  mockApi({ "GET /api/v1/me/notices/unread-count": { count: 0 } });
  renderWithProviders(
    <StudentShell>
      <p>page</p>
    </StudentShell>,
    { user, locale: "en", route },
  );
}

describe("student top bar (S1.1)", () => {
  it("shows Home, MCQ, Written, My exams, Results and Routine to a signed-in student", async () => {
    renderShell("/", fixtures.student);
    const bar = await screen.findByRole("navigation", { name: TOP_BAR });
    const links = within(bar).getAllByRole("link");
    expect(links.map((link) => link.textContent)).toEqual(["Home", "MCQ", "Written", "My exams", "Results", "Routine"]);
    expect(links.map((link) => link.getAttribute("href"))).toEqual(["/", "/mcq", "/written", "/my-modules", "/results", "/routine"]);
  });

  it("marks the page the student is on", async () => {
    renderShell("/results", fixtures.student);
    const bar = await screen.findByRole("navigation", { name: TOP_BAR });
    expect(within(bar).getByRole("link", { name: "Results" })).toHaveAttribute("aria-current", "page");
    expect(within(bar).getByRole("link", { name: "Home" })).not.toHaveAttribute("aria-current");
  });

  it("is not shown to a visitor", async () => {
    renderShell("/mcq", null);
    expect(screen.queryByRole("navigation", { name: TOP_BAR })).not.toBeInTheDocument();
  });

  it("puts Wish List in the account menu between My account and Notices (S1.2)", async () => {
    renderShell("/", fixtures.student);
    await userEvent.click(await screen.findByRole("button", { name: /Menu for/ }));
    const items = await screen.findAllByRole("menuitem");
    const names = items.map((item) => item.textContent);
    expect(names.indexOf("Wish List")).toBe(names.indexOf("My account") + 1);
    expect(names.indexOf("Notices")).toBe(names.indexOf("Wish List") + 1);
    expect(names).toContain("Routine");
    expect(items[names.indexOf("Wish List")]).toHaveAttribute("href", "/wishlist");
  });
});
