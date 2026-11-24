import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { StaffNavList } from "@/components/shell/staff-sidebar";
import { staffNavFor } from "@/components/shell/staff-nav";
import { fixtures, renderWithProviders } from "../helpers";

// The offline switch is on for these cases, so the offline group shows.
vi.mock("@/lib/config", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/config")>();
  return { ...actual, offlineAdmissionEnabled: true };
});

const FINANCE = 'a[href="/staff/finance"]';
const PAYMENTS = 'a[href="/staff/offline-instructor-payments"]';
const INSTRUCTORS = 'a[href="/staff/offline-instructors"]';

describe("staff navigation for offline instructor pay", () => {
  it("shows Finance only to the Master Admin, at the top of the Money group", () => {
    const money = (role: "MASTER_ADMIN" | "SYSTEM_ADMIN" | "INSTRUCTOR") =>
      staffNavFor(role).filter((item) => item.group === "money").map((item) => item.key);

    expect(money("MASTER_ADMIN")[0]).toBe("finance");
    expect(money("SYSTEM_ADMIN")).not.toContain("finance");
    expect(money("INSTRUCTOR")).not.toContain("finance");
  });

  it("shows the two offline instructor items to the Master Admin only (24 Nov)", () => {
    const offline = (role: "MASTER_ADMIN" | "SYSTEM_ADMIN" | "INSTRUCTOR") =>
      staffNavFor(role)
        .filter((item) => item.group === "offline")
        .map((item) => item.key);

    expect(offline("MASTER_ADMIN")).toEqual(expect.arrayContaining(["instructorPayments", "offlineInstructors"]));
    expect(offline("SYSTEM_ADMIN")).not.toContain("instructorPayments");
    expect(offline("SYSTEM_ADMIN")).not.toContain("offlineInstructors");
    expect(offline("INSTRUCTOR")).toEqual([]);
  });

  it("renders the Finance link for the Master Admin only", () => {
    const { container } = renderWithProviders(<StaffNavList items={staffNavFor("MASTER_ADMIN")} />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });
    expect(container.querySelector(FINANCE)).not.toBeNull();
    expect(screen.getByRole("link", { name: "Instructor payments" })).toHaveAttribute("href", "/staff/offline-instructor-payments");
    expect(screen.getByRole("link", { name: "Offline instructors" })).toHaveAttribute("href", "/staff/offline-instructors");
  });

  it("gives a System Admin neither the offline instructor items nor Finance", () => {
    const { container } = renderWithProviders(<StaffNavList items={staffNavFor("SYSTEM_ADMIN")} />, {
      user: fixtures.systemAdmin,
      locale: "en",
    });
    expect(container.querySelector(FINANCE)).toBeNull();
    expect(container.querySelector(PAYMENTS)).toBeNull();
    expect(container.querySelector(INSTRUCTORS)).toBeNull();
  });

  it("gives an instructor none of the three items", () => {
    const { container } = renderWithProviders(<StaffNavList items={staffNavFor("INSTRUCTOR")} />, {
      user: fixtures.instructor,
      locale: "en",
    });
    expect(container.querySelector(FINANCE)).toBeNull();
    expect(container.querySelector(PAYMENTS)).toBeNull();
    expect(container.querySelector(INSTRUCTORS)).toBeNull();
  });
});
