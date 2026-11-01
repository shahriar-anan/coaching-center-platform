import { act, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { ModulePage } from "@/components/student/catalog/module-page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

// The package and exam pages ask for their announcements only when the viewer holds the target or the target is free.

const MODULE_ID = fixtures.module.id;
const EXAM_ID = "eeeeeeee-0000-0000-0000-000000000009";

const offer = (price: number | null, effective: number | null, free = false) => ({
  price,
  discountPercent: null,
  discountEndsAt: null,
  effectivePrice: effective,
  free,
  moduleOnly: false,
});

const pkg = (access: Record<string, unknown> | null, free = false) => ({
  ...fixtures.module,
  price: free ? 0 : 2500,
  pricing: offer(free ? 0 : 2500, free ? 0 : 2500, free),
  coverUrl: null,
  favourite: false,
  access,
});

const exam = (access: Record<string, unknown> | null, free = false) => ({
  id: EXAM_ID,
  moduleId: MODULE_ID,
  examType: "MCQ",
  titleEn: "CPC Full Mock",
  titleBn: "CPC Full Mock",
  descriptionEn: "A full mock paper.",
  descriptionBn: null,
  rulesEn: null,
  rulesBn: null,
  paperLanguage: "EN",
  liveAt: "2099-01-01T00:00:00Z",
  endAt: "2099-01-01T00:40:00Z",
  durationMinutes: 40,
  price: free ? 0 : 400,
  pricing: offer(free ? 0 : 400, free ? 0 : 400, free),
  packages: [],
  favourite: false,
  access,
});

const emptyPage = { content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 };

const ANNOUNCEMENT = {
  id: "ann-1",
  titleEn: "Bring a pencil",
  titleBn: "পেনসিল আনবেন",
  bodyEn: "Mock on Sunday.",
  bodyBn: null,
  createdAt: "2026-10-30T04:00:00Z",
};

/** The routes a package page reads. The announcements route is added by each test. */
function packageRoutes(access: Record<string, unknown> | null, free = false) {
  return {
    [`GET /api/v1/public/live-exam-modules/${MODULE_ID}`]: pkg(access, free),
    [`GET /api/v1/public/live-exam-modules/${MODULE_ID}/exams`]: { ...emptyPage, content: [exam(null)] },
    [`GET /api/v1/live-exam-modules/${MODULE_ID}/exams`]: emptyPage,
    "GET /api/v1/public/live-exam-categories": fixtures.categoryTree,
    "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
  };
}

/** The routes an exam page reads. The announcements route is added by each test. */
function examRoutes(access: Record<string, unknown> | null, free = false) {
  return {
    [`GET /api/v1/public/live-exams/${EXAM_ID}`]: exam(access, free),
    "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
  };
}

/** Lets the page finish its reads, so a request that would be made has been made. */
async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 30));
  });
}

function announcementRequests(calls: { path: string; search: string }[]) {
  return calls.filter((call) => call.path === "/api/v1/me/announcements");
}

describe("the package page shows its announcements to a holder, or for a free package", () => {
  it("shows the card to a student who holds the package, and asks for this package only", async () => {
    const { calls } = mockApi({
      ...packageRoutes({ state: "MODULE", buyable: false, blockedReason: null }),
      "GET /api/v1/me/announcements": [ANNOUNCEMENT],
    });
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("heading", { level: 2, name: "Announcements" })).toBeInTheDocument();
    expect(screen.getByText("Bring a pencil")).toBeInTheDocument();
    expect(announcementRequests(calls).map((call) => call.search)).toEqual([`?kind=package&id=${MODULE_ID}`]);
  });

  it("shows the card to a signed-in student for a free package they do not hold", async () => {
    const { calls } = mockApi({
      ...packageRoutes({ state: "NONE", buyable: false, blockedReason: null }, true),
      "GET /api/v1/me/announcements": [ANNOUNCEMENT],
    });
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("heading", { level: 2, name: "Announcements" })).toBeInTheDocument();
    expect(announcementRequests(calls)).toHaveLength(1);
  });

  it("makes no announcements request for a visitor", async () => {
    const { calls } = mockApi({
      ...packageRoutes(null),
      "GET /api/v1/me/announcements": [ANNOUNCEMENT],
    });
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { locale: "en" });

    await screen.findByRole("heading", { level: 1, name: "BCS Preliminary" });
    await settle();
    expect(announcementRequests(calls)).toHaveLength(0);
    expect(screen.queryByRole("heading", { name: "Announcements" })).not.toBeInTheDocument();
  });

  it("makes no announcements request for a staff user", async () => {
    const { calls } = mockApi({
      ...packageRoutes(null),
      "GET /api/v1/me/announcements": [ANNOUNCEMENT],
    });
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { user: fixtures.masterAdmin, locale: "en" });

    await screen.findByRole("heading", { level: 1, name: "BCS Preliminary" });
    await settle();
    expect(announcementRequests(calls)).toHaveLength(0);
  });

  it("makes no announcements request for a student who does not hold a paid package", async () => {
    const { calls } = mockApi({
      ...packageRoutes({ state: "NONE", buyable: true, blockedReason: null }),
      "GET /api/v1/me/announcements": [ANNOUNCEMENT],
    });
    renderWithProviders(<ModulePage door="mcq" moduleId={MODULE_ID} />, { user: fixtures.student, locale: "en" });

    await screen.findByRole("heading", { level: 1, name: "BCS Preliminary" });
    await settle();
    expect(announcementRequests(calls)).toHaveLength(0);
    expect(screen.queryByRole("heading", { name: "Announcements" })).not.toBeInTheDocument();
  });
});

describe("the exam page shows its announcements to a holder, or for a free exam", () => {
  it("shows the card to a student who holds the exam, and asks for this exam only", async () => {
    const { calls } = mockApi({
      ...examRoutes({ state: "EXAM", buyable: false, blockedReason: null }),
      "GET /api/v1/me/announcements": [ANNOUNCEMENT],
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("heading", { level: 2, name: "Announcements" })).toBeInTheDocument();
    expect(screen.getByText("Mock on Sunday.")).toBeInTheDocument();
    expect(announcementRequests(calls).map((call) => call.search)).toEqual([`?kind=exam&id=${EXAM_ID}`]);
  });

  it("shows the card to a signed-in student for a free exam they do not hold", async () => {
    const { calls } = mockApi({
      ...examRoutes({ state: "NONE", buyable: false, blockedReason: null }, true),
      "GET /api/v1/me/announcements": [ANNOUNCEMENT],
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("heading", { level: 2, name: "Announcements" })).toBeInTheDocument();
    expect(announcementRequests(calls)).toHaveLength(1);
  });

  it("makes no announcements request for a visitor", async () => {
    const { calls } = mockApi({ ...examRoutes(null), "GET /api/v1/me/announcements": [ANNOUNCEMENT] });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { locale: "en" });

    await screen.findByRole("heading", { level: 1, name: "CPC Full Mock" });
    await settle();
    expect(announcementRequests(calls)).toHaveLength(0);
    expect(screen.queryByRole("heading", { name: "Announcements" })).not.toBeInTheDocument();
  });

  it("makes no announcements request for a staff user", async () => {
    const { calls } = mockApi({ ...examRoutes(null), "GET /api/v1/me/announcements": [ANNOUNCEMENT] });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { user: fixtures.masterAdmin, locale: "en" });

    await screen.findByRole("heading", { level: 1, name: "CPC Full Mock" });
    await settle();
    expect(announcementRequests(calls)).toHaveLength(0);
  });

  it("makes no announcements request for a student who does not hold a paid exam", async () => {
    const { calls } = mockApi({
      ...examRoutes({ state: "NONE", buyable: true, blockedReason: null }),
      "GET /api/v1/me/announcements": [ANNOUNCEMENT],
    });
    renderWithProviders(<ExamPage examId={EXAM_ID} />, { user: fixtures.student, locale: "en" });

    await screen.findByRole("heading", { level: 1, name: "CPC Full Mock" });
    await settle();
    expect(announcementRequests(calls)).toHaveLength(0);
  });
});
