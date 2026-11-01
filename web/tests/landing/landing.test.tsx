import { screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import userEvent from "@testing-library/user-event";
import { LandingPage } from "@/components/student/landing/landing-page";
import {
  categoryCards,
  daysUntil,
  featuredModules,
  flattenModules,
  nextExams,
  totals,
} from "@/components/student/landing/landing-data";
import type { CategoryNode, PublicModule, UpcomingExam } from "@/lib/api/exam/catalog";
import type { CategoryExam } from "@/lib/api/exam/types";
import { formatMoney } from "@/lib/format";
import bn from "@/i18n/messages/bn/landing";
import bnCatalog from "@/i18n/messages/bn/catalog";
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

  it("gives one card per top-level category with packages of the chosen type, priced by that type", () => {
    // MCQ: the two MCQ packages; the written package sits in a subcategory and is left out.
    expect(categoryCards(tree, "MCQ")).toEqual([
      expect.objectContaining({ id: "bjs", moduleCount: 2, examCount: 0, fromPrice: 399, href: "/mcq/c/bjs" }),
    ]);
    // Written: only the written package, under the subcategory, so its price is the written one.
    expect(categoryCards(tree, "WRITTEN")).toEqual([
      expect.objectContaining({ id: "bjs", moduleCount: 1, fromPrice: 250, href: "/written/c/bjs" }),
    ]);
  });

  it("shows a category only under the type it has packages or exams of, and links to that type", () => {
    const written = [category({ id: "w", modules: [module({ id: "x", writtenCount: 3 })] })];
    expect(categoryCards(written, "MCQ")).toEqual([]);
    expect(categoryCards(written, "WRITTEN")).toEqual([
      expect.objectContaining({ id: "w", href: "/written/c/w", moduleCount: 1, fromPrice: null }),
    ]);
  });

  it("shows a category with standalone exams and no packages, counting the exams of the chosen type", () => {
    const exam = (id: string, examType: CategoryExam["examType"]) =>
      ({ id, examType, titleEn: id, titleBn: id, liveAt: null, endAt: null }) as unknown as CategoryExam;
    const standalone = [
      category({
        id: "bjs-exams",
        exams: [exam("e1", "MCQ"), exam("e2", "WRITTEN")],
        children: [category({ id: "bjs-sub", parentId: "bjs-exams", exams: [exam("e3", "MCQ")] })],
      }),
    ];
    expect(categoryCards(standalone, "MCQ")).toEqual([
      expect.objectContaining({ id: "bjs-exams", examCount: 2, moduleCount: 0, fromPrice: null, href: "/mcq/c/bjs-exams" }),
    ]);
    expect(categoryCards(standalone, "WRITTEN")).toEqual([
      expect.objectContaining({ id: "bjs-exams", examCount: 1, href: "/written/c/bjs-exams" }),
    ]);
  });

  it("hides a category with no packages and no exams under either type", () => {
    expect(categoryCards([category({ id: "empty" })], "MCQ")).toEqual([]);
    expect(categoryCards([category({ id: "empty" })], "WRITTEN")).toEqual([]);
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

  it("features popular packages first, then the rest in tree order, up to six", () => {
    const modules = Array.from({ length: 8 }, (_, i) => module({ id: `p${i}`, popular: i === 4 || i === 6 }));
    const nodes = [category({ id: "c", modules })];
    expect(featuredModules(nodes, [], 6).map((card) => card.module.id)).toEqual(["p4", "p6", "p0", "p1", "p2", "p3"]);
  });

  it("features only the chosen ids when there are any, each once, and ignores popularity", () => {
    const modules = [module({ id: "a" }), module({ id: "b", popular: true }), module({ id: "c" })];
    const nodes = [category({ id: "root", modules })];
    expect(featuredModules(nodes, ["c", "b", "c"], 6).map((card) => card.module.id)).toEqual(["c", "b"]);
  });

  it("counts whole Dhaka calendar days, so a time after midnight in Dhaka is already tomorrow", () => {
    // 2026-12-01 10:00 Dhaka, checked at 2026-12-01 18:00 Dhaka: today.
    expect(daysUntil("2026-12-01T04:00:00Z", Date.parse("2026-12-01T12:00:00Z"))).toBe(0);
    // 2026-12-02 00:30 Dhaka is tomorrow when checked at 2026-12-01 18:00 Dhaka, though UTC still says 2026-12-01.
    expect(daysUntil("2026-12-01T18:30:00Z", Date.parse("2026-12-01T12:00:00Z"))).toBe(1);
    expect(daysUntil("2026-12-06T04:00:00Z", Date.parse("2026-12-01T12:00:00Z"))).toBe(5);
    expect(daysUntil("2026-11-28T04:00:00Z", Date.parse("2026-12-01T12:00:00Z"))).toBe(-3);
  });
});

describe("LandingPage", () => {
  it("shows the hero with the exam category doors, and leaves out upcoming exams when there are none", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-categories": tree,
      "GET /api/v1/public/live-exams": { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<LandingPage />, { user: null, locale: "bn" });

    expect(screen.getByRole("heading", { level: 1, name: bn.heroTitle })).toBeInTheDocument();
    // The registered-students figure under the hero's sign-up button.
    expect(screen.getByText("৭৬৫+")).toBeInTheDocument();
    expect(screen.getByText(bn.registeredStudents)).toBeInTheDocument();
    // The exam categories follow the hero under the "exams" heading.
    expect(await screen.findByRole("heading", { name: bn.examsTitle })).toBeInTheDocument();
    expect(await screen.findAllByText(bn.categoryExplore)).not.toHaveLength(0);
    expect(await screen.findByRole("heading", { name: bn.packagesTitle })).toBeInTheDocument();
    // The two doors sit under the exam categories heading in the hero.
    expect(screen.getByRole("heading", { name: bn.categoriesTitle })).toBeInTheDocument();
    // The packages section's "see all" goes to the packages page.
    const packages = await screen.findByRole("region", { name: bn.packagesTitle });
    expect(within(packages).getByRole("link", { name: bn.seeAll })).toHaveAttribute("href", "/packages");
    expect(screen.getByRole("link", { name: /MCQ.*দ্রুত অনুশীলন/ })).toHaveAttribute("href", "/mcq");
    expect(screen.getByRole("link", { name: /Written.*লিখিত পরীক্ষা/ })).toHaveAttribute("href", "/written");
    expect(screen.queryByText("খালি ক্যাটাগরি")).not.toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("heading", { name: bn.upcomingTitle })).not.toBeInTheDocument());
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
    expect(await screen.findByRole("heading", { level: 3, name: "First MCQ" })).toBeInTheDocument();
    // The soonest exam is the spotlight, with a button to its page.
    expect(screen.getByRole("link", { name: en.upcomingView })).toHaveAttribute("href", "/exam/e1");
  });

  it("shows the soonest exam as the spotlight and the next ones as a list", async () => {
    const exam = (id: string, titleEn: string, liveAt: string) => ({
      id,
      examType: "MCQ",
      titleEn,
      titleBn: titleEn,
      moduleTitleEn: "BJS",
      moduleTitleBn: "বিজেএস মডিউল",
      liveAt,
      endAt: null,
      price: 100,
    });
    mockApi({
      "GET /api/v1/public/live-exam-categories": tree,
      "GET /api/v1/public/live-exams": ({ url }: { url: URL }) => ({
        content:
          url.searchParams.get("examType") === "MCQ"
            ? [exam("late", "Late MCQ", "2026-12-02T04:00:00Z"), exam("early", "Early MCQ", "2026-12-01T04:00:00Z")]
            : [],
        page: 0,
        size: 5,
        totalElements: 2,
        totalPages: 1,
      }),
    });
    renderWithProviders(<LandingPage />, { user: null, locale: "en" });

    const section = await screen.findByRole("region", { name: en.upcomingTitle });
    expect(await within(section).findByRole("heading", { level: 3, name: "Early MCQ" })).toBeInTheDocument();
    expect(within(section).getAllByRole("link", { name: en.upcomingView })).toHaveLength(1);
    expect(within(section).getByRole("link", { name: en.upcomingView })).toHaveAttribute("href", "/exam/early");
    expect(within(section).getByRole("link", { name: /Late MCQ/ })).toHaveAttribute("href", "/exam/late");
  });

  it("starts on MCQ and lists only the chosen type's categories when the switch changes", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-categories": tree,
      "GET /api/v1/public/live-exams": { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<LandingPage />, { user: null, locale: "bn" });

    const section = await screen.findByRole("region", { name: bn.examsTitle });
    const mcqButton = screen.getByRole("button", { name: bn.typeMcq });
    const writtenButton = screen.getByRole("button", { name: bn.typeWritten });
    expect(mcqButton).toHaveAttribute("aria-pressed", "true");
    expect(writtenButton).toHaveAttribute("aria-pressed", "false");
    await waitFor(() => expect(section.querySelector('a[href="/mcq/c/bjs"]')).not.toBeNull());
    expect(section.querySelector('a[href="/written/c/bjs"]')).toBeNull();

    await userEvent.click(writtenButton);
    expect(writtenButton).toHaveAttribute("aria-pressed", "true");
    expect(mcqButton).toHaveAttribute("aria-pressed", "false");
    await waitFor(() => expect(section.querySelector('a[href="/written/c/bjs"]')).not.toBeNull());
    expect(section.querySelector('a[href="/mcq/c/bjs"]')).toBeNull();
  });

  it("shows a short empty line for the chosen type when only the other type has categories", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-categories": ({ url }: { url: URL }) =>
        url.searchParams.get("examType") === "WRITTEN"
          ? [category({ id: "w", titleBn: "লিখিত ক্যাটাগরি", modules: [module({ id: "x", writtenCount: 3 })] })]
          : [],
      "GET /api/v1/public/live-exams": { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<LandingPage />, { user: null, locale: "bn" });

    const section = await screen.findByRole("region", { name: bn.examsTitle });
    expect(await screen.findByText(bn.categoriesEmpty.replace("{type}", bn.typeMcq))).toBeInTheDocument();
    expect(section.querySelector("a")).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: bn.typeWritten }));
    expect(await screen.findByText("লিখিত ক্যাটাগরি")).toBeInTheDocument();
  });

  it("hides the whole exam section when neither type has a category", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-categories": [category({ id: "empty", titleBn: "খালি ক্যাটাগরি" })],
      "GET /api/v1/public/live-exams": { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<LandingPage />, { user: null, locale: "bn" });

    expect(screen.getByRole("heading", { level: 1, name: bn.heroTitle })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("region", { name: bn.examsTitle })).not.toBeInTheDocument());
    expect(screen.queryByRole("button", { name: bn.typeMcq })).not.toBeInTheDocument();
  });

  it("shows the subtitle, the counts as chips and the price inside one link per category card", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-categories": tree,
      "GET /api/v1/public/live-exams": { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<LandingPage />, { user: null, locale: "bn" });

    const section = await screen.findByRole("region", { name: bn.examsTitle });
    expect(within(section).getByText(bn.categoriesSubtitle)).toBeInTheDocument();
    // Two MCQ packages in the category, priced from 399: the count and the price show, and the card is a single link.
    const card = await within(section).findByRole("link", { name: new RegExp(bn.categoryExplore) });
    expect(card).toHaveAttribute("href", "/mcq/c/bjs");
    expect(within(card).getByText(bnCatalog.countModules.replace("{n}", "২"))).toBeInTheDocument();
    expect(within(card).getByText(bn.categoryFrom.replace("{price}", formatMoney(399, "bn") ?? ""))).toBeInTheDocument();
    expect(section.querySelectorAll("a a")).toHaveLength(0);
  });
});
