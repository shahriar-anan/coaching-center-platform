import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { PriceBar } from "@/components/ui/purchase/price-bar";
import { renderWithProviders } from "../helpers";

describe("PriceBar (P-1)", () => {
  it("shows the price with Bengali digits in Bangla", () => {
    renderWithProviders(<PriceBar price={400} />, { locale: "bn" });
    expect(screen.getByText("৳৪০০")).toBeInTheDocument();
  });

  it("shows the price in western digits in English", () => {
    renderWithProviders(<PriceBar price={400} />, { locale: "en" });
    expect(screen.getByText("৳400.00")).toBeInTheDocument();
  });

  it("says the price is not set and offers no price when null", () => {
    renderWithProviders(<PriceBar price={null} />, { locale: "bn" });
    expect(screen.getByText("দাম এখনো নির্ধারণ হয়নি।")).toBeInTheDocument();
  });

  it("shows the note, the status and the action", () => {
    renderWithProviders(
      <PriceBar price={400} priceNote="তিন মাসের জন্য" status={<span>চালু</span>} action={<button type="button">কিনুন</button>} />,
      { locale: "bn" },
    );
    expect(screen.getByText("তিন মাসের জন্য")).toBeInTheDocument();
    expect(screen.getByText("চালু")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "কিনুন" })).toBeInTheDocument();
  });
});
