import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StaffNavList } from "@/components/shell/staff-sidebar";
import { staffNavFor } from "@/components/shell/staff-nav";
import { fixtures, renderWithProviders } from "../helpers";

describe("the staff sidebar by role", () => {
  it("an instructor sees no Packages item and a My exams item", () => {
    renderWithProviders(<StaffNavList items={staffNavFor("INSTRUCTOR")} />, {
      user: fixtures.instructor,
      locale: "en",
    });

    expect(screen.queryByRole("button", { name: "Packages" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Packages" })).toBeNull();
    expect(screen.getByRole("button", { name: "My exams" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Exams" })).toBeNull();
    expect(screen.getByRole("link", { name: "Submissions" })).toHaveAttribute("href", "/staff/submissions");
  });

  it("a System Admin sees no Submissions item, and still sees Results & marking", () => {
    renderWithProviders(<StaffNavList items={staffNavFor("SYSTEM_ADMIN")} />, {
      user: fixtures.systemAdmin,
      locale: "en",
    });

    expect(screen.queryByRole("link", { name: "Submissions" })).toBeNull();
    expect(screen.getByRole("link", { name: "Results & marking" })).toHaveAttribute("href", "/staff/participation");
    expect(screen.getByRole("button", { name: "Exams" })).toBeInTheDocument();
  });
});
