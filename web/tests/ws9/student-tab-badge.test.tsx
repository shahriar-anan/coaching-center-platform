import { describe, expect, it } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import { StudentShell } from "@/components/shell/student-shell";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

describe("student tab bar notices badge (F1)", () => {
  it("shows the unread count on the নোটিশ tab", async () => {
    mockApi({ "GET /api/v1/me/notices/unread-count": { count: 3 } });
    renderWithProviders(
      <StudentShell>
        <p>পাতা</p>
      </StudentShell>,
      { user: fixtures.student, route: "/" },
    );
    const bar = await screen.findByRole("navigation", { name: "নিচের মেনু" });
    const tab = within(bar).getByRole("link", { name: /নোটিশ/ });
    await waitFor(() => expect(tab).toHaveAccessibleName("নোটিশ ৩টি অপঠিত নোটিশ"));
  });

  it("shows no badge when nothing is unread", async () => {
    mockApi({ "GET /api/v1/me/notices/unread-count": { count: 0 } });
    renderWithProviders(
      <StudentShell>
        <p>পাতা</p>
      </StudentShell>,
      { user: fixtures.student, route: "/" },
    );
    const bar = await screen.findByRole("navigation", { name: "নিচের মেনু" });
    const tab = within(bar).getByRole("link", { name: /নোটিশ/ });
    await waitFor(() => expect(tab).not.toHaveTextContent(/\d/));
  });
});
