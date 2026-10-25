import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { ModulePage } from "@/components/student/catalog/module-page";
import { useMyLiveExamAccess } from "@/lib/api/exam/catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const MODULE = "/api/v1/public/live-exam-modules/m-1";
const MODULE_EXAMS = `${MODULE}/exams`;
const STUDENT_MODULE = "/api/v1/live-exam-modules/m-1";
const STUDENT_MODULE_EXAMS = `${STUDENT_MODULE}/exams`;
const EXAM = "/api/v1/public/live-exams/ex-1";
const STUDENT_EXAM = "/api/v1/live-exams/ex-1";
const MY_ACCESS = "/api/v1/me/live-exam-access";

const access = (state: string, buyable: boolean, blockedReason: string | null) => ({ state, buyable, blockedReason });

function moduleRow(moduleAccess: ReturnType<typeof access> | null) {
  return {
    id: "m-1",
    categoryId: "c-1",
    titleEn: "Evidence",
    titleBn: "সাক্ষ্য",
    descriptionEn: null,
    descriptionBn: null,
    price: 1200,
    status: "PUBLISHED",
    categoryPath: [],
    ...(moduleAccess ? { access: moduleAccess } : {}),
  };
}

function examRow(id: string, price: number | null, examAccess: ReturnType<typeof access> | null) {
  return {
    id,
    moduleId: "m-1",
    examType: "MCQ",
    titleEn: `Exam ${id}`,
    titleBn: `পরীক্ষা ${id}`,
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
    rulesBn: null,
    paperLanguage: "BN",
    liveAt: null,
    endAt: null,
    durationMinutes: 40,
    passPercent: 40,
    maximum: 50,
    status: "PUBLISHED",
    price,
    myStatus: null,
    retakeAvailable: null,
    sitting: null,
    ...(examAccess ? { access: examAccess } : {}),
  };
}

function modulePage(moduleAccess: ReturnType<typeof access> | null, rows: ReturnType<typeof examRow>[]) {
  return mockApi({
    [`GET ${MODULE}`]: moduleRow(moduleAccess),
    [`GET ${MODULE_EXAMS}`]: { content: rows, page: 0, size: 100, totalElements: rows.length, totalPages: 1 },
    "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
  });
}

function calledPaths(calls: { method: string; path: string }[]) {
  return calls.map((call) => `${call.method} ${call.path}`);
}

describe("module page ownership from the access object (S-003)", () => {
  it("a visitor-like student with no access: buy is enabled and no ownership probe is made", async () => {
    const { calls } = modulePage(access("NONE", true, null), [examRow("ex-1", 400, access("NONE", true, null))]);
    renderWithProviders(<ModulePage door="mcq" moduleId="m-1" />, { user: fixtures.student });

    expect(await screen.findByRole("button", { name: "পুরো মডিউল কিনুন" })).toBeEnabled();
    const paths = calledPaths(calls);
    expect(paths).not.toContain(`GET ${STUDENT_MODULE}`);
    expect(paths).not.toContain(`GET ${STUDENT_MODULE_EXAMS}`);
    expect(paths).not.toContain(`GET ${STUDENT_EXAM}`);
  });

  it("module-owned: the badge shows, there is no buy button, and the student's exam list is read", async () => {
    const { calls } = modulePage(access("MODULE", false, "ALREADY_HAS_ACCESS"), [
      examRow("ex-1", 400, access("MODULE", false, "ALREADY_HAS_ACCESS")),
    ]);
    renderWithProviders(<ModulePage door="mcq" moduleId="m-1" />, { user: fixtures.student });

    expect((await screen.findAllByText("অ্যাক্সেস আছে")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "পুরো মডিউল কিনুন" })).not.toBeInTheDocument();
    await waitFor(() => expect(calledPaths(calls)).toContain(`GET ${STUDENT_MODULE_EXAMS}`));
  });

  it("owning one exam leaves the module NONE and buyable: the module buy stays, the owned row shows the badge", async () => {
    modulePage(access("NONE", true, null), [
      examRow("ex-1", 400, access("EXAM", false, "ALREADY_HAS_ACCESS")),
      examRow("ex-2", 400, access("NONE", true, null)),
    ]);
    renderWithProviders(<ModulePage door="mcq" moduleId="m-1" />, { user: fixtures.student });

    expect(await screen.findByRole("button", { name: "পুরো মডিউল কিনুন" })).toBeEnabled();
    expect(await screen.findByText("অ্যাক্সেস আছে")).toBeInTheDocument();
    expect(screen.getAllByText("কিনুন ›")).toHaveLength(1);
  });

  it("a blocked module with checkout off: the buy button is disabled", async () => {
    mockApi({
      [`GET ${MODULE}`]: moduleRow(access("NONE", false, "CHECKOUT_OFF")),
      [`GET ${MODULE_EXAMS}`]: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: false },
    });
    renderWithProviders(<ModulePage door="mcq" moduleId="m-1" />, { user: fixtures.student });

    expect(await screen.findByRole("button", { name: "পুরো মডিউল কিনুন" })).toBeDisabled();
  });

  it("a priced exam row that is not owned and not buyable does not offer a buy link", async () => {
    modulePage(access("NONE", true, null), [examRow("ex-1", null, access("NONE", false, "PRICE_REQUIRED"))]);
    renderWithProviders(<ModulePage door="mcq" moduleId="m-1" />, { user: fixtures.student });

    expect((await screen.findAllByText("মূল্য এখনো ঠিক হয়নি")).length).toBeGreaterThan(0);
    expect(screen.queryByText("কিনুন ›")).not.toBeInTheDocument();
  });
});

describe("exam page ownership from the access object (S-003)", () => {
  it("a non-owner makes no student exam or module read", async () => {
    const { calls } = mockApi({
      [`GET ${EXAM}`]: examRow("ex-1", 400, access("NONE", true, null)),
      [`GET ${MODULE}`]: moduleRow(access("NONE", true, null)),
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId="ex-1" />, { user: fixtures.student });

    expect((await screen.findAllByRole("button", { name: /কিনুন/ })).length).toBeGreaterThan(0);
    const paths = calledPaths(calls);
    expect(paths).not.toContain(`GET ${STUDENT_EXAM}`);
    expect(paths).not.toContain(`GET ${STUDENT_MODULE}`);
  });

  it("owned through the exam: the badge shows, no buy button, and the student's exam is read once", async () => {
    const { calls } = mockApi({
      [`GET ${EXAM}`]: examRow("ex-1", 400, access("EXAM", false, "ALREADY_HAS_ACCESS")),
      [`GET ${MODULE}`]: moduleRow(access("NONE", true, null)),
      [`GET ${STUDENT_EXAM}`]: examRow("ex-1", 400, null),
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: true },
    });
    renderWithProviders(<ExamPage examId="ex-1" />, { user: fixtures.student });

    expect((await screen.findAllByText("অ্যাক্সেস আছে")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /কিনুন/ })).not.toBeInTheDocument();
    await waitFor(() => expect(calledPaths(calls).filter((p) => p === `GET ${STUDENT_EXAM}`)).toHaveLength(1));
  });

  it("a blocked exam with checkout off: the buy button is disabled", async () => {
    mockApi({
      [`GET ${EXAM}`]: examRow("ex-1", 400, access("NONE", false, "CHECKOUT_OFF")),
      [`GET ${MODULE}`]: moduleRow(access("NONE", true, null)),
      "GET /api/v1/public/exam-site/status": { checkoutEnabled: false },
    });
    renderWithProviders(<ExamPage examId="ex-1" />, { user: fixtures.student });

    expect((await screen.findAllByRole("button", { name: /কিনুন/ })).at(0)).toBeDisabled();
  });
});

describe("my live-exam access (for my modules)", () => {
  function MyAccess() {
    const mine = useMyLiveExamAccess(true);
    return <p>{mine.data ? `modules ${mine.data.modules.length}, exams ${mine.data.exams.length}` : "loading"}</p>;
  }

  it("reads /api/v1/me/live-exam-access once and returns the student's own grants", async () => {
    const { calls } = mockApi({
      [`GET ${MY_ACCESS}`]: {
        modules: [{ moduleId: "m-1", via: "MODULE", orderId: "o-1" }],
        exams: [{ examId: "ex-2", moduleId: "m-2", via: "EXAM", orderId: "o-2" }],
      },
    });
    renderWithProviders(<MyAccess />, { user: fixtures.student });

    expect(await screen.findByText("modules 1, exams 1")).toBeInTheDocument();
    expect(calledPaths(calls).filter((p) => p === `GET ${MY_ACCESS}`)).toHaveLength(1);
  });
});
