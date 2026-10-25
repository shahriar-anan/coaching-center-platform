import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StaffShell } from "@/components/shell/staff-shell";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

describe("staff search on phones (FN-7)", () => {
  it("opens the same search in a dialog from the top bar button", async () => {
    mockApi({ "GET /api/v1/students": { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 }, "GET /api/v1/live-exam-modules": { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 } });
    renderWithProviders(
      <StaffShell>
        <p>পাতা</p>
      </StaffShell>,
      { user: fixtures.masterAdmin, route: "/staff" },
    );
    await userEvent.click(screen.getByRole("button", { name: "খুঁজুন" }));
    const dialog = await screen.findByRole("dialog", { name: "দ্রুত অনুসন্ধান" });
    expect(dialog.querySelector("input[role='combobox']")).not.toBeNull();
  });
});
