import { describe, expect, it } from "vitest";
import { MonthChart } from "@/components/staff/accounting/month-chart";
import { renderWithProviders } from "../helpers";

/*
 * The 12-month chart draws its bars and legend in the finance palette (--fin-src-1, --fin-src-2). Those variables exist only under
 * .finance-viz with the FinanceTheme style, so the chart must carry both or its bars are invisible.
 */
describe("accounting month chart", () => {
  it("carries the finance palette, so its bars and legend have colours", () => {
    const { container } = renderWithProviders(
      <MonthChart months={[{ month: "2026-11", expense: "2100.00", income: "0.00" }]} />,
    );
    const figure = container.querySelector("figure");
    expect(figure?.classList.contains("finance-viz")).toBe(true);
    const palette = Array.from(container.querySelectorAll("style")).map((style) => style.textContent ?? "").join("\n");
    expect(palette).toContain("--fin-src-1");
    expect(palette).toContain("--fin-src-2");
  });
});
