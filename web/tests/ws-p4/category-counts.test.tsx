import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DoorPage } from "@/components/student/catalog/door-page";
import { countBranchExams, countModules } from "@/components/student/catalog/catalog-helpers";
import type { CategoryNode, PublicModule } from "@/lib/api/exam/types";
import { mockApi, renderWithProviders } from "../helpers";

/** A category with its own exams and packages. Only the fields the counts and the card read are filled in. */
function category(id: string, titleEn: string, exams: number, packages: number, children: CategoryNode[] = []): CategoryNode {
  return {
    id,
    parentId: null,
    position: 0,
    titleEn,
    titleBn: titleEn,
    children,
    modules: Array.from({ length: packages }, (_, index) => ({ id: `${id}-m${index}` }) as unknown as PublicModule),
    exams: Array.from({ length: exams }, (_, index) => ({ id: `${id}-e${index}` }) as never),
  };
}

// Judiciary (2 exams, 1 package) -> Civil (3 exams, 2 packages) -> Civil trial (4 exams, leaf)
const leaf = category("trial", "Civil trial", 4, 0);
const civil = category("civil", "Civil", 3, 2, [leaf]);
const judiciary = category("judiciary", "Judiciary", 2, 1, [civil]);

describe("category counts (Phase 4, A4)", () => {
  it("a parent counts its whole branch, and a leaf counts its own exams", () => {
    expect(countBranchExams(judiciary)).toBe(9);
    expect(countBranchExams(civil)).toBe(7);
    expect(countBranchExams(leaf)).toBe(4);
    expect(countModules(judiciary)).toBe(3);
    expect(countModules(leaf)).toBe(0);
  });

  it("a card with subcategories shows the subcategory count, then exams, then packages", async () => {
    mockApi({ "GET /api/v1/public/live-exam-categories": [judiciary] });
    renderWithProviders(<DoorPage door="mcq" />, { locale: "en", route: "/mcq" });

    const card = await screen.findByRole("link", { name: /Judiciary/ });
    expect(card).toHaveTextContent("1 categories · 9 exams · 3 packages");
  });

  it("a leaf card shows only its own exams and leaves out the zero parts", async () => {
    const onlyExams = category("solo", "Solo", 5, 0);
    mockApi({ "GET /api/v1/public/live-exam-categories": [onlyExams] });
    renderWithProviders(<DoorPage door="mcq" />, { locale: "en", route: "/mcq" });

    const card = await screen.findByRole("link", { name: /Solo/ });
    expect(card).toHaveTextContent("5 exams");
    expect(card).not.toHaveTextContent("packages");
    expect(card).not.toHaveTextContent("categories");
  });

  it("a card with nothing at all says Coming soon", async () => {
    const empty = category("empty", "Empty", 0, 0);
    mockApi({ "GET /api/v1/public/live-exam-categories": [empty] });
    renderWithProviders(<DoorPage door="mcq" />, { locale: "en", route: "/mcq" });

    const card = await screen.findByRole("link", { name: /Empty/ });
    expect(card).toHaveTextContent("Coming soon");
  });
});
