import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CatalogExamRow } from "@/components/student/catalog/exam-row";
import { phaseStatus } from "@/components/student/catalog/catalog-helpers";
import type { CategoryExam } from "@/lib/api/exam/types";
import { renderWithProviders } from "../helpers";

const base: CategoryExam = {
  id: "e1",
  categoryId: "c1",
  examType: "MCQ",
  mode: "LIVE",
  phase: "LIVE",
  titleEn: "CPC MCQ",
  titleBn: "CPC MCQ",
  liveAt: "2026-11-05T17:00:00Z",
  endAt: "2026-11-05T17:20:00Z",
  popular: false,
  pricing: null,
  packages: [],
};

const pkg = (id: string, title: string) => ({ id, titleEn: title, titleBn: title });

/** The row that holds the title: the title is the only link, so the price and status sit in its container. */
function row(): HTMLElement {
  return screen.getByRole("link", { name: /CPC MCQ/ }).closest(".group") as HTMLElement;
}

function renderRow(exam: CategoryExam) {
  return renderWithProviders(
    <ul>
      <li>
        <CatalogExamRow exam={exam} />
      </li>
    </ul>,
    { locale: "en" },
  );
}

describe("an exam row in the category list (S2.2)", () => {
  it("shows the list price struck through, the charged price, the discount and its end date", () => {
    renderRow({
      ...base,
      pricing: { price: 400, discountPercent: 25, discountEndsAt: "2026-11-30T17:59:59Z", effectivePrice: 300, free: false, moduleOnly: false },
    });
    const link = row();
    expect(within(link).getByText("25% off", { exact: false })).toBeInTheDocument();
    expect(within(link).getByText(/until/)).toBeInTheDocument();
    expect(link.querySelector("s")?.textContent).toContain("400");
    expect(link.textContent).toContain("300");
  });

  it("shows a plain price when there is no discount", () => {
    renderRow({ ...base, pricing: { price: 300, discountPercent: null, discountEndsAt: null, effectivePrice: 300, free: false, moduleOnly: false } });
    const link = row();
    expect(link.querySelector("s")).toBeNull();
    expect(link.textContent).toContain("300");
  });

  it("says Free for a free exam", () => {
    renderRow({ ...base, pricing: { price: null, discountPercent: null, discountEndsAt: null, effectivePrice: null, free: true, moduleOnly: false } });
    expect(row()).toHaveTextContent("Free");
  });

  it("says the price is not set for an exam with no price and no package price", () => {
    renderRow({ ...base, pricing: { price: null, discountPercent: null, discountEndsAt: null, effectivePrice: null, free: false, moduleOnly: false } });
    expect(row()).toHaveTextContent("Price not set yet.");
  });

  it("says the exam is only in a package when it has no price of its own", () => {
    renderRow({ ...base, pricing: { price: null, discountPercent: null, discountEndsAt: null, effectivePrice: null, free: false, moduleOnly: true } });
    expect(row()).toHaveTextContent("Only in a package");
  });

  it("does not show the packages the exam is in: the detail page lists them", () => {
    renderRow({ ...base, packages: [pkg("p1", "Alpha"), pkg("p2", "Beta"), pkg("p3", "Gamma")] });
    const link = row();
    expect(link).not.toHaveTextContent("Alpha");
    expect(link).not.toHaveTextContent("Beta");
    expect(link).not.toHaveTextContent("Gamma");
    expect(link).not.toHaveTextContent("more");
  });

  it("shows the window status word from the phase", () => {
    renderRow({ ...base, phase: "LIVE" });
    expect(row()).toHaveTextContent("Live now");
  });
});

describe("the window status word for each phase (S2.2)", () => {
  it("maps each phase to its copy key, and an unknown phase to ended", () => {
    expect(phaseStatus("UNANNOUNCED").key).toBe("catalog.phase.UNANNOUNCED");
    expect(phaseStatus("UPCOMING").key).toBe("catalog.phase.UPCOMING");
    expect(phaseStatus("LIVE")).toEqual({ key: "catalog.phase.LIVE", tone: "warn", pulse: true });
    expect(phaseStatus("PRACTICE").key).toBe("catalog.phase.PRACTICE");
    expect(phaseStatus("ENDED").key).toBe("catalog.phase.ENDED");
    expect(phaseStatus("SOMETHING_NEW").key).toBe("catalog.phase.ENDED");
  });
});

describe("the three zones of a catalog row (S14.2)", () => {
  it("shows the title in at most two lines, and the date on one line that truncates", () => {
    renderRow({ ...base, liveAt: null });
    const title = screen.getByRole("link", { name: /CPC MCQ/ });
    expect(title).toHaveClass("line-clamp-2");
    expect(screen.getByText("Date to be announced")).toHaveClass("truncate");
  });

  it("has no package chip even for a long package name", () => {
    renderRow({ ...base, packages: [pkg("p1", "A very long package name for the Penal Code practice set")] });
    expect(screen.queryByTitle("A very long package name for the Penal Code practice set")).not.toBeInTheDocument();
  });

  it("shows the discount as a green chip, beside the price and before the end date", () => {
    renderRow({
      ...base,
      pricing: { price: 400, discountPercent: 25, discountEndsAt: "2026-11-30T17:59:59Z", effectivePrice: 300, free: false, moduleOnly: false },
    });
    const chip = screen.getByText("25% off");
    expect(chip).toHaveClass("rounded-control", "text-ok-text");
    expect(chip).toHaveClass("bg-ok/10");
    expect(screen.getByText(/until/)).toHaveClass("text-meta");
  });

  it("shows Free in green in the same bottom-right place", () => {
    renderRow({ ...base, pricing: { price: null, discountPercent: null, discountEndsAt: null, effectivePrice: null, free: true, moduleOnly: false } });
    expect(screen.getByText("Free")).toHaveClass("text-ok-text");
  });
});
