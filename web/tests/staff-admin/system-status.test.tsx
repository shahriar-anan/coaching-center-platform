import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import StaffSystemStatusPage from "@/app/(staff)/staff/system-status/page";
import { routerState } from "../helpers/router";
import { fixtures, renderWithProviders } from "../helpers";
import { apiError, mockApi } from "../helpers/mock-api";
import "./copy-setup";

const checks = {
  checks: [
    { name: "checkout.configured", ok: true, note: "SSLCommerz store and callback values are set." },
    { name: "mail.configured", ok: false, note: "A mail host is set." },
    { name: "seed.off", ok: true, note: "The demo seed is off. It must be off in production." },
  ],
};

describe("system status page", () => {
  it("shows one row per check, with a Yes or No pill and the note", async () => {
    mockApi({ "GET /api/v1/system-status": checks });
    renderWithProviders(<StaffSystemStatusPage />, { user: fixtures.masterAdmin });

    expect((await screen.findAllByText("পেমেন্ট গেটওয়ের সেটিং")).length).toBeGreaterThan(0);
    // Each row renders in the table and in the phone cards, so two cells per row.
    expect(screen.getAllByText("হ্যাঁ")).toHaveLength(4);
    expect(screen.getAllByText("না")).toHaveLength(2);
    expect(screen.getAllByText("SSLCommerz store and callback values are set.").length).toBeGreaterThan(0);
  });

  it("says what to change for a failed check, and shows no value", async () => {
    mockApi({ "GET /api/v1/system-status": checks });
    renderWithProviders(<StaffSystemStatusPage />, { user: fixtures.masterAdmin });

    expect(
      (await screen.findAllByText("সার্ভারে মেইল সার্ভারের ঠিকানা সেট করুন।")).length,
    ).toBeGreaterThan(0);
    expect(screen.queryByText(/[a-z]+\.[a-z]+@/i)).not.toBeInTheDocument();
  });

  it("sends System Admin back to the staff home", () => {
    mockApi({ "GET /api/v1/system-status": checks });
    renderWithProviders(<StaffSystemStatusPage />, { user: fixtures.systemAdmin });

    expect(routerState.replace).toHaveBeenCalledWith("/staff");
  });

  it("shows the error state when the checks cannot load", async () => {
    mockApi({ "GET /api/v1/system-status": apiError(500, "INTERNAL_ERROR", "Boom.") });
    renderWithProviders(<StaffSystemStatusPage />, { user: fixtures.masterAdmin });

    expect(await screen.findByText("সিস্টেম স্ট্যাটাস আনা যায়নি।")).toBeInTheDocument();
  });
});
