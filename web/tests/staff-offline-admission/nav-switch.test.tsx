import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { StaffNavList } from "@/components/shell/staff-sidebar";
import { staffNavFor } from "@/components/shell/staff-nav";
import { fixtures, renderWithProviders } from "../helpers";

// The feature switch is read from the config module. A getter lets each case set it before it renders.
const flag = vi.hoisted(() => ({ on: false }));

vi.mock("@/lib/config", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/config")>();
  return {
    ...actual,
    get offlineAdmissionEnabled() {
      return flag.on;
    },
  };
});

describe("the staff sidebar's Offline admission group", () => {
  beforeEach(() => {
    flag.on = false;
  });

  it("is absent when the offline admission switch is off", () => {
    flag.on = false;
    renderWithProviders(<StaffNavList items={staffNavFor("MASTER_ADMIN")} />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });

    expect(screen.queryByRole("heading", { name: "Offline admission" })).toBeNull();
    expect(document.querySelector('a[href="/staff/admissions"]')).toBeNull();
    expect(document.querySelector('a[href="/staff/offline-fees"]')).toBeNull();
    expect(document.querySelector('a[href="/staff/offline-batches"]')).toBeNull();
    // The other groups still show.
    expect(screen.getByRole("heading", { name: "Money" })).toBeInTheDocument();
  });

  it("is present when the offline admission switch is on", () => {
    flag.on = true;
    const { container } = renderWithProviders(<StaffNavList items={staffNavFor("MASTER_ADMIN")} />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });

    expect(screen.getByRole("heading", { name: "Offline admission" })).toBeInTheDocument();
    expect(container.querySelector('a[href="/staff/admissions"]')).not.toBeNull();
    expect(container.querySelector('a[href="/staff/offline-fees"]')).not.toBeNull();
    expect(container.querySelector('a[href="/staff/offline-batches"]')).not.toBeNull();
  });
});
