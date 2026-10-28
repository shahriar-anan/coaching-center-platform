import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PurchaseSheet } from "@/components/student/purchase/purchase-sheet";
import { fixtures, renderWithProviders } from "../helpers";

describe("the purchase sheet amounts (S2.6)", () => {
  it("charges the effective price, with the list price struck through, and names the package the exam is in", () => {
    renderWithProviders(
      <PurchaseSheet
        open
        onOpenChange={() => undefined}
        item={{
          kind: "exam",
          id: "e1",
          title: "CPC MCQ",
          price: 300,
          listPrice: 400,
          discountPercent: 25,
          discountEndsAt: "2099-11-30T17:59:59Z",
          packages: [{ id: "m1", title: "Package one" }],
          door: "mcq",
        }}
      />,
      { user: fixtures.student, locale: "en" },
    );

    expect(screen.getByText("In package: Package one")).toBeInTheDocument();
    expect(document.querySelector("s")?.textContent).toContain("400");
    expect(screen.getByText(/25% off/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /300/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /400/ })).not.toBeInTheDocument();
  });

  it("shows one plain price and no package line when the exam is not discounted and in no package", () => {
    renderWithProviders(
      <PurchaseSheet
        open
        onOpenChange={() => undefined}
        item={{ kind: "exam", id: "e2", title: "Evidence MCQ", price: 250, door: "mcq" }}
      />,
      { user: fixtures.student, locale: "en" },
    );

    expect(document.querySelector("s")).toBeNull();
    expect(screen.queryByText(/In package/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /250/ })).toBeInTheDocument();
  });
});
