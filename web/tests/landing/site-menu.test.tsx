import { afterEach, describe, expect, it } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StudentShell } from "@/components/shell/student-shell";
import { mockApi, renderWithProviders } from "../helpers";

/** The eight entries of the main menu, in order. Exam is two exam types, so no entry is "Exam" itself. */
const ENTRIES = [
  { label: "Admission form", href: "/register" },
  { label: "MCQ", href: "/mcq" },
  { label: "Written", href: "/written" },
  { label: "Packages", href: "/packages" },
  { label: "Notices", href: "/notice-board" },
  { label: "Publications", href: "/publications" },
  { label: "Blog", href: "/blog" },
  { label: "Media", href: "/media" },
  { label: "Resources", href: "/resources" },
];

function renderVisitorLanding() {
  mockApi({});
  renderWithProviders(
    <StudentShell>
      <p>page</p>
    </StudentShell>,
    { route: "/", locale: "en" },
  );
}

describe("main menu", () => {
  afterEach(() => {
    delete document.documentElement.dataset.theme;
    window.localStorage.removeItem("theme");
  });

  it("the phone Menu lists the eight entries with Exam split into MCQ and Written", async () => {
    renderVisitorLanding();
    await userEvent.click(await screen.findByRole("button", { name: "Menu" }));
    const items = await screen.findAllByRole("menuitem");
    expect(items.slice(0, ENTRIES.length).map((item) => [item.textContent, item.getAttribute("href")])).toEqual(
      ENTRIES.map((entry) => [entry.label, entry.href]),
    );
  });

  it("the language and theme switches are menu items after the entries", async () => {
    renderVisitorLanding();
    await userEvent.click(await screen.findByRole("button", { name: "Menu" }));
    const items = await screen.findAllByRole("menuitem");
    expect(items.slice(ENTRIES.length).map((item) => item.textContent)).toEqual(["বাংলায় দেখুন", "Switch to dark mode"]);
  });

  it("the theme switch works from the keyboard and closes the menu", async () => {
    renderVisitorLanding();
    const trigger = await screen.findByRole("button", { name: "Menu" });
    trigger.focus();
    await userEvent.keyboard("{Enter}");
    await screen.findAllByRole("menuitem");
    await userEvent.keyboard("{End}");
    expect(screen.getByRole("menuitem", { name: "Switch to dark mode" })).toHaveFocus();
    await userEvent.keyboard("{Enter}");
    await waitFor(() => expect(screen.queryByRole("menu")).not.toBeInTheDocument());
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(trigger).toHaveFocus();
  });

  it("visitors on an inner page get the same Menu button", async () => {
    mockApi({});
    renderWithProviders(
      <StudentShell>
        <p>page</p>
      </StudentShell>,
      { route: "/packages", locale: "en" },
    );
    expect(await screen.findByRole("button", { name: "Menu" })).toBeInTheDocument();
  });

  it("the desktop Exam button opens MCQ and Written, and the bar has no link straight to MCQ", async () => {
    renderVisitorLanding();
    const bar = await screen.findByRole("navigation", { name: "Main menu" });
    expect(within(bar).queryByRole("link", { name: "Exams" })).not.toBeInTheDocument();
    expect(within(bar).getAllByRole("link").map((link) => link.getAttribute("href"))).not.toContain("/mcq");

    await userEvent.click(within(bar).getByRole("button", { name: "Exams" }));
    const types = await screen.findAllByRole("menuitem");
    expect(types.map((item) => item.getAttribute("href"))).toEqual(["/mcq", "/written"]);
  });

  it("closes on Escape and gives focus back to the Menu button", async () => {
    renderVisitorLanding();
    const trigger = await screen.findByRole("button", { name: "Menu" });
    await userEvent.click(trigger);
    await screen.findAllByRole("menuitem");
    await userEvent.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("menu")).not.toBeInTheDocument());
    expect(trigger).toHaveFocus();
  });
});
