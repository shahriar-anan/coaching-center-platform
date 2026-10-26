import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { AccessBadge } from "@/components/ui/purchase/access-badge";
import { renderWithProviders } from "../helpers";

describe("AccessBadge (P-2)", () => {
  it.each([
    ["MODULE", "মডিউল"],
    ["EXAM", "এই পরীক্ষা"],
    ["ROSTER", "রোস্টার"],
    ["MANUAL", "প্রশাসক দিয়েছেন"],
  ] as const)("says the access is there and how it was given (%s)", (via, label) => {
    renderWithProviders(<AccessBadge via={via} />, { locale: "bn" });
    expect(screen.getByText("অ্যাক্সেস আছে")).toBeInTheDocument();
    expect(screen.getByText(new RegExp(label))).toBeInTheDocument();
  });

  it("uses the ok tone", () => {
    renderWithProviders(<AccessBadge via="MODULE" />, { locale: "bn" });
    expect(screen.getByText("অ্যাক্সেস আছে").closest("span.inline-flex")).toHaveClass("text-ok-text");
  });
});
