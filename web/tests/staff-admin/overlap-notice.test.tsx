import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { OverlapNotice } from "@/components/staff/enrol/overlap-notice";
import type { OverlapItem } from "@/lib/api/exam/staff-admin";
import { fixtures, renderWithProviders } from "../helpers";

const examWithPlan: OverlapItem = {
  kind: "EXAM",
  id: "e1",
  titleEn: "Penal Code",
  titleBn: "দণ্ডবিধি",
  via: "DIRECT",
  planId: "p1",
  paid: 2100,
  due: 4200,
};

describe("the moved-from-exam warning (plan 14.11, D26)", () => {
  it("says the exam plan stays open when a package is enrolled while money is owed on that exam", () => {
    renderWithProviders(<OverlapNotice items={[examWithPlan]} scope="MODULE" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(screen.getByText(/is still being paid in installments/)).toBeInTheDocument();
    expect(screen.getByText(/does not close that plan/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open the payment plan" })).toBeInTheDocument();
  });

  it("does not use the moved-from-exam wording on an exam enrolment", () => {
    renderWithProviders(<OverlapNotice items={[examWithPlan]} scope="EXAM" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(screen.queryByText(/does not close that plan/)).not.toBeInTheDocument();
    expect(screen.getByText(/Paying in installments/)).toBeInTheDocument();
  });
});
