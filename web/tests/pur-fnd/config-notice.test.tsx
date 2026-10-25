import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { ConfigNotice } from "@/components/ui/purchase/config-notice";
import { renderWithProviders } from "../helpers";

const SENTENCE = "পেমেন্ট এখন চালু নেই। কিছুক্ষণ পরে আবার চেষ্টা করুন।";

describe("ConfigNotice (P-6)", () => {
  it("shows the checkout-off sentence as a status banner when show is true", () => {
    renderWithProviders(<ConfigNotice show />, { locale: "bn" });
    expect(screen.getByText(SENTENCE)).toHaveAttribute("role", "status");
  });

  it("renders nothing when show is false", () => {
    renderWithProviders(<ConfigNotice show={false} />, { locale: "bn" });
    expect(screen.queryByText(SENTENCE)).not.toBeInTheDocument();
  });
});
