import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { HomePage } from "@/components/student/home/home-page";
import { PopularSection } from "@/components/student/home/popular-section";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const exam = (id: string, titleEn: string) => ({
  id,
  categoryId: "c1",
  examType: "MCQ",
  mode: "LIVE",
  phase: "UPCOMING",
  titleEn,
  titleBn: titleEn,
  liveAt: "2099-01-01T00:00:00Z",
  endAt: "2099-01-01T00:20:00Z",
  popular: true,
  pricing: null,
  packages: [],
});

const pkg = (id: string, titleEn: string) => ({
  id,
  titleEn,
  titleBn: titleEn,
  categoryId: "c1",
  position: 1,
  price: 900,
  mcqCount: 2,
  writtenCount: 1,
  coverUrl: null,
  descriptionEn: null,
  descriptionBn: null,
  pricing: { price: 900, discountPercent: null, discountEndsAt: null, effectivePrice: 900, free: false, moduleOnly: false },
});

const page = (content: unknown[]) => ({ content, page: 0, size: 6, totalElements: content.length, totalPages: 1 });

describe("the doors on the student home", () => {
  it("keep the exam segment on its own: a heading, then MCQ and Written", async () => {
    mockApi({ "GET /api/v1/me/exam-routine": page([]) });
    renderWithProviders(<HomePage />, { user: fixtures.student, locale: "en" });

    const heading = await screen.findByRole("heading", { name: "Exams" });
    const segment = heading.closest("section") as HTMLElement;
    const doors = within(segment).getAllByRole("link");
    expect(doors.map((door) => door.getAttribute("href"))).toEqual(["/mcq", "/written"]);
    expect(within(segment).queryByRole("link", { name: /My orders|Exam packages/ })).not.toBeInTheDocument();
  });

  it("put My orders and Exam packages in a segment of their own, set apart from the exams", async () => {
    mockApi({ "GET /api/v1/me/exam-routine": page([]) });
    renderWithProviders(<HomePage />, { user: fixtures.student, locale: "en" });

    const exams = (await screen.findByRole("heading", { name: "Exams" })).closest("section") as HTMLElement;
    const shop = screen.getByRole("heading", { name: "Orders and packages" }).closest("section") as HTMLElement;
    expect(shop).not.toBe(exams);
    expect(exams.compareDocumentPosition(shop) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(shop).toHaveClass("border-t");
    const doors = within(shop).getAllByRole("link");
    expect(doors.map((door) => door.getAttribute("href"))).toEqual(["/my-modules", "/packages"]);
    expect(within(shop).getByRole("link", { name: /My orders/ })).toHaveTextContent("Everything you bought");
    expect(within(shop).getByRole("link", { name: /Exam packages/ })).toHaveTextContent("Browse all packages");
  });

  it("do not print the student's name, and show no list of orders or popular items", async () => {
    mockApi({ "GET /api/v1/me/exam-routine": page([]) });
    renderWithProviders(<HomePage />, { user: fixtures.student, locale: "en" });

    await screen.findByRole("heading", { name: "Exams" });
    expect(screen.queryByText(fixtures.student.fullName)).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "My orders" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Popular" })).not.toBeInTheDocument();
  });

  it("each show their own line icon: a checklist, a pen, a shopping bag and a package", async () => {
    mockApi({ "GET /api/v1/me/exam-routine": page([]) });
    renderWithProviders(<HomePage />, { user: fixtures.student, locale: "en" });

    await screen.findByRole("link", { name: /My orders/ });
    const icon = (name: RegExp) => screen.getByRole("link", { name }).querySelector("svg")?.getAttribute("class") ?? "";
    expect(icon(/MCQ/)).toContain("list-checks");
    expect(icon(/Written/)).toContain("pen-line");
    expect(icon(/My orders/)).toContain("shopping-bag");
    expect(icon(/Exam packages/)).toContain("package");
  });
});

describe("the Popular list, where it is used", () => {
  it("shows two exams and two packages, and its See all goes to the packages page the landing page uses", async () => {
    mockApi({
      "GET /api/v1/public/live-exams": page([exam("e1", "Exam one"), exam("e2", "Exam two"), exam("e3", "Exam three")]),
      "GET /api/v1/public/live-exam-modules": page([pkg("m1", "Package one"), pkg("m2", "Package two"), pkg("m3", "Package three")]),
    });
    renderWithProviders(<PopularSection />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("link", { name: "Exam one" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Exam three" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Package three" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "See all" })).toHaveAttribute("href", "/packages");
  });
});
