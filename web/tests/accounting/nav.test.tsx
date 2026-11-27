import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { redirect } from "next/navigation";
import { StaffNavList } from "@/components/shell/staff-sidebar";
import { staffNavFor } from "@/components/shell/staff-nav";
import { ACC_GROUP_IDS } from "@/lib/api/accounting/ids";
import OldInstructorPaymentsPage from "@/app/(staff)/staff/offline-instructor-payments/page";
import OldInstructorsPage from "@/app/(staff)/staff/offline-instructors/page";
import OldInstructorPage from "@/app/(staff)/staff/offline-instructors/[id]/page";
import { fixtures, renderWithProviders } from "../helpers";

/*
 * The menu (accounting-plan 4.6): one Accounting item for the Master Admin, in the money group; the two old items are gone; the old
 * addresses redirect into Accounting.
 */

const OLD_LINKS = ['a[href="/staff/offline-instructor-payments"]', 'a[href="/staff/offline-instructors"]'];

describe("staff navigation: accounting", () => {
  it("shows Accounting to the Master Admin only, in the money group", () => {
    const money = (role: "MASTER_ADMIN" | "SYSTEM_ADMIN" | "INSTRUCTOR") =>
      staffNavFor(role)
        .filter((item) => item.group === "money")
        .map((item) => item.key);

    expect(money("MASTER_ADMIN")).toContain("accounting");
    expect(money("SYSTEM_ADMIN")).not.toContain("accounting");
    expect(money("INSTRUCTOR")).not.toContain("accounting");
  });

  it("has no Instructor payments or Offline instructors item any more", () => {
    const keys = staffNavFor("MASTER_ADMIN").map((item) => item.key);
    expect(keys).not.toContain("instructorPayments");
    expect(keys).not.toContain("offlineInstructors");
  });

  it("renders the Accounting link for the Master Admin and no old link", () => {
    const { container } = renderWithProviders(<StaffNavList items={staffNavFor("MASTER_ADMIN")} />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });
    expect(screen.getByRole("link", { name: "Accounting" })).toHaveAttribute("href", "/staff/accounting");
    for (const selector of OLD_LINKS) {
      expect(container.querySelector(selector)).toBeNull();
    }
  });

  it("gives a System Admin no accounting link", () => {
    const { container } = renderWithProviders(<StaffNavList items={staffNavFor("SYSTEM_ADMIN")} />, {
      user: fixtures.systemAdmin,
      locale: "en",
    });
    expect(container.querySelector('a[href="/staff/accounting"]')).toBeNull();
  });
});

describe("old addresses redirect into Accounting", () => {
  it("sends the Offline instructors list to the Offline instructors group", () => {
    vi.mocked(redirect).mockClear();
    OldInstructorsPage();
    expect(redirect).toHaveBeenCalledWith(`/staff/accounting/groups/${ACC_GROUP_IDS.OFFLINE_INSTRUCTORS}`);
  });

  it("sends one instructor to the payee page with the same id", async () => {
    vi.mocked(redirect).mockClear();
    await OldInstructorPage({ params: Promise.resolve({ id: "ins-milon" }) });
    expect(redirect).toHaveBeenCalledWith("/staff/accounting/payees/ins-milon");
  });

  it("sends the Instructor payments address to the entries of the Offline instructors group", () => {
    vi.mocked(redirect).mockClear();
    OldInstructorPaymentsPage();
    expect(redirect).toHaveBeenCalledWith(`/staff/accounting/entries?groupId=${ACC_GROUP_IDS.OFFLINE_INSTRUCTORS}`);
  });
});
