import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import StaffStudentsPage from "@/app/(staff)/staff/students/page";
import { DeskActivationDialog } from "@/components/staff/offline/desk-activation-dialog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { page } from "./fixtures";

describe("Students → New student (plan 6.3, desk creation)", () => {
  it("has the Offline admission checkbox on by default for staff", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/students": page([]),
      "GET /api/v1/offline-admissions": page([]),
    });
    renderWithProviders(<StaffStudentsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/students" });

    await user.click(await screen.findByRole("button", { name: "New student" }));

    const checkbox = await screen.findByRole("checkbox", { name: /Offline admission/ });
    expect(checkbox).toBeChecked();
  });

  it("is on for a System Admin as well", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/students": page([]),
      "GET /api/v1/offline-admissions": page([]),
    });
    renderWithProviders(<StaffStudentsPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/students" });

    await user.click(await screen.findByRole("button", { name: "New student" }));

    expect(await screen.findByRole("checkbox", { name: /Offline admission/ })).toBeChecked();
  });
});

describe("the activation code of a desk admission", () => {
  it("shows the code in large type with the steps to read out", () => {
    renderWithProviders(
      <DeskActivationDialog open onClose={() => undefined} name="Rahim Uddin" phone="01711000001" code="K7Q2-9PXA" />,
      { user: fixtures.masterAdmin, locale: "en" },
    );

    expect(screen.getByText("K7Q2-9PXA", { selector: "p" })).toBeInTheDocument();
    expect(screen.getByText("Say these steps to the student")).toBeInTheDocument();
    expect(screen.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      "Open the site on the student's phone and tap Activate.",
      "Enter this code, then set a password.",
      "Fill in the admission form, take or choose a photo, and submit it.",
    ]);
  });
});
