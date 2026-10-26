import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LandingPage } from "@/components/student/landing/landing-page";
import {
  categoryCards,
  featuredModules,
  flattenModules,
  nextExams,
  totals,
} from "@/components/student/landing/landing-data";
import type { CategoryNode, PublicModule, UpcomingExam } from "@/lib/api/exam/catalog";
import bn from "@/i18n/messages/bn/landing";
import en from "@/i18n/messages/en/landing";
import { mockApi, renderWithProviders } from "../helpers";

function keys(tree: Record<string, unknown>, prefix = ""): string[] {
  return Object.entries(tree).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof value === "object" && value !== null ? keys(value as Record<string, unknown>, path) : [path];
  });
}

function module(overrides: Partial<PublicModule> & Pick<PublicModule, "id">): PublicModule {
  return {
    categoryId: "cat",
    titleEn: "Module",
    titleBn: "মডিউল",
    descriptionEn: null,
    descriptionBn: null,
    position: 1,
    price: null,
    mcqCount: 0,
    writtenCount: 0,
    ...overrides,
  };
}

function category(overrides: Partial<CategoryNode> & Pick<CategoryNode, "id">): CategoryNode {
  return {
    parentId: null,
    position: 1,
    titleEn: "Category",
    titleBn: "ক্যাটাগরি",
    children: [],
    modules: [],
    ...overrides,
  };
}

const tree: CategoryNode[] = [
  category({
    id: "bjs",
    titleBn: "বিজেএস মডিউল",
    modules: [
      module({ id: "m-mcq", price: 399, mcqCount: 10 }),
      module({ id: "m-free", price: null, mcqCount: 5 }),
    ],
    children: [
      category({
        id: "bjs-written",
        parentId: "bjs",
        modules: [module({ id: "m-written", price: 250, writtenCount: 4 })],
      }),
    ],
  }),
  category({ id: "empty", titleBn: "খালি ক্যাটাগরি" }),
];

describe("landing messages", () => {
  it("has the same keys in Bangla and English", () => {
    expect(keys(en).sort()).toEqual(keys(bn).sort());
  });

  it("has no empty copy", () => {
    for (const key of keys(bn)) {
      const value = key.split(".").reduce<unknown>((node, part) => (node as Record<string, unknown>)[part], bn);
      expect(String(value).trim(), key).not.toBe("");
    }
  });
});

describe("landing data", () => {
  it("counts modules across the whole tree, with MCQ and written totals", () => {
    expect(flattenModules(tree)).toHaveLength(3);
    expect(totals(tree)).toEqual({ modules: 3, mcq: 15, written: 4 });
  });

  it("gives one card per top-level category that has modules, with the lowest price", () => {
    const cards = categoryCards(tree);
    expect(cards.map((card) => card.id)).toEqual(["bjs"]);
    expect(cards[0]).toMatchObject({ moduleCount: 3, fromPrice: 250, href: "/mcq/c/bjs" });
    expect(cards[0]).toMatchObject({ mcqHref: "/mcq/c/bjs", writtenHref: "/written/c/bjs" });
  });

  it("links a category to the written door when it has no MCQ modules", () => {
    const written = [category({ id: "w", modules: [module({ id: "x", writtenCount: 3 })] })];
    expect(categoryCards(written)[0].href).toBe("/written/c/w");
    expect(categoryCards(written)[0]).toMatchObject({ mcqHref: null, writtenHref: "/written/c/w" });
  });

  it("features the chosen ids in order, or the first modules when none are chosen", () => {
    expect(featuredModules(tree, ["m-written", "m-mcq"], 3).map((card) => card.module.id)).toEqual([
      "m-written",
      "m-mcq",
    ]);
    expect(featuredModules(tree, [], 2).map((card) => card.module.id)).toEqual(["m-mcq", "m-free"]);
  });

  it("lists the soonest exams first, skips exams without a date, and applies the limit", () => {
    const exam = (id: string, liveAt: string | null) =>
      ({ id, examType: "MCQ", liveAt, titleEn: id, titleBn: id }) as unknown as UpcomingExam;
    const sorted = nextExams(
      [[exam("late", "2026-12-02T04:00:00Z"), exam("undated", null)], [exam("early", "2026-12-01T04:00:00Z")]],
      5,
    );
    expect(sorted.map((item) => item.id)).toEqual(["early", "late"]);
    expect(nextExams([[exam("a", "2026-12-01T04:00:00Z"), exam("b", "2026-12-02T04:00:00Z")]], 1)).toHaveLength(1);
  });
});

describe("LandingPage", () => {
  it("shows the hero and the categories, and leaves out upcoming exams when there are none", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-categories": tree,
      "GET /api/v1/public/live-exams": { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<LandingPage />, { user: null, locale: "bn" });

    expect(screen.getByRole("heading", { level: 1, name: bn.heroTitle })).toBeInTheDocument();
    expect(await screen.findByText("বিজেএস মডিউল")).toBeInTheDocument();
    // The hero lists the exam categories, with one explore button per exam type the category has.
    expect(screen.getByRole("link", { name: bn.categoryExploreMcq })).toHaveAttribute("href", "/mcq/c/bjs");
    expect(screen.getByRole("link", { name: bn.categoryExploreWritten })).toHaveAttribute("href", "/written/c/bjs");
    expect(screen.queryByText("খালি ক্যাটাগরি")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: bn.upcomingTitle })).not.toBeInTheDocument();
  });

  it("shows the two doors in the hero when the catalog has no categories", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-categories": [],
      "GET /api/v1/public/live-exams": { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<LandingPage />, { user: null, locale: "bn" });

    expect(await screen.findByRole("link", { name: /এমসিকিউ.*দ্রুত অনুশীলন/ })).toHaveAttribute("href", "/mcq");
    expect(screen.getByRole("link", { name: /রিটেন.*লিখিত পরীক্ষা/ })).toHaveAttribute("href", "/written");
  });

  it("lists upcoming MCQ exams from the API", async () => {
    const exam = {
      id: "e1",
      examType: "MCQ",
      titleEn: "First MCQ",
      titleBn: "প্রথম এমসিকিউ",
      moduleTitleEn: "BJS",
      moduleTitleBn: "বিজেএস মডিউল",
      liveAt: "2026-12-01T04:00:00Z",
      endAt: "2026-12-01T05:00:00Z",
    };
    mockApi({
      "GET /api/v1/public/live-exam-categories": tree,
      "GET /api/v1/public/live-exams": ({ url }: { url: URL }) => ({
        content: url.searchParams.get("examType") === "MCQ" ? [exam] : [],
        page: 0,
        size: 5,
        totalElements: 1,
        totalPages: 1,
      }),
    });
    renderWithProviders(<LandingPage />, { user: null, locale: "en" });

    expect(await screen.findByRole("heading", { name: en.upcomingTitle })).toBeInTheDocument();
    expect(await screen.findByText("First MCQ")).toBeInTheDocument();
    expect(screen.getByText("First MCQ").closest("a")).toHaveAttribute("href", "/exam/e1");
  });
});
