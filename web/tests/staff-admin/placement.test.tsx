import { Suspense } from "react";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ExamPage from "@/app/(staff)/staff/exams/[examId]/page";
import InstructorsPage from "@/app/(staff)/staff/instructors/page";
import StudentPage from "@/app/(staff)/staff/students/[id]/page";
import StudentsPage from "@/app/(staff)/staff/students/page";
import { fixtures, mockApi, apiError, renderWithProviders, routerState } from "../helpers";
import "./copy-setup";

/*
 * Where the admin controls sit on the pages (ADMIN-UI, placement): the answer switch
 * on the exam page, the access list and account controls on the student page, the
 * controls on the instructors page, and the row menu and delete on the students list.
 */

const hoisted = vi.hoisted(() => ({ examId: "eeeeeeee-0000-4000-8000-000000000001" }));

// The exam page reads its id from the route. The other next/navigation helpers stay as the setup file has them.
vi.mock("next/navigation", async () => {
  const { routerState: router } = await import("../helpers/router");
  return {
    useRouter: () => router,
    usePathname: () => router.pathname,
    useSearchParams: () => new URLSearchParams(router.search),
    useParams: () => hoisted,
    redirect: vi.fn(),
    notFound: vi.fn(),
  };
});

// The exam editor loads the whole paper; these tests only need the page around it.
vi.mock("@/components/staff/catalog/exam-editor", () => ({ ExamEditor: () => null }));

const EXAM = hoisted.examId;
const STUDENT = "11111111-1111-4111-8111-000000000001";
const MODULE = "bbbbbbbb-0000-4000-8000-000000000001";
const ACTIVE_INSTRUCTOR = "44444444-0000-4000-8000-000000000001";
const SUSPENDED_INSTRUCTOR = "44444444-0000-4000-8000-000000000002";
const PENDING_INSTRUCTOR = "44444444-0000-4000-8000-000000000003";

const PAID_MESSAGE = "এই শিক্ষার্থীর পেমেন্ট বা অ্যাক্সেস আছে, তাই মুছে ফেলা যাবে না। অ্যাকাউন্ট নিষ্ক্রিয় করুন।";
const MARKED_WORK_MESSAGE = "এই ইনস্ট্রাক্টরের মূল্যায়ন করা কাজ আছে, তাই মুছে ফেলা যাবে না। অ্যাকাউন্ট নিষ্ক্রিয় করুন।";
const GENERIC_MESSAGE = "কিছু একটা ভুল হয়েছে। আবার চেষ্টা করুন।";

function pageOf<T>(content: T[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

const rahim = {
  id: STUDENT,
  fullName: "রহিম উদ্দিন",
  phone: "01711000011",
  email: "rahim@example.com",
  role: "STUDENT",
  status: "ACTIVE",
  studentCode: "STU-0011",
};

const access = pageOf([
  {
    moduleId: MODULE,
    moduleTitleEn: "19th BJS",
    moduleTitleBn: "১৯তম বিজেএস",
    via: "MANUAL",
    exams: [],
    orderId: null,
    tranId: null,
    grantId: "gggggggg-0000-4000-8000-000000000011",
  },
  {
    moduleId: MODULE,
    moduleTitleEn: "19th BJS",
    moduleTitleBn: "১৯তম বিজেএস",
    via: "EXAM_PURCHASE",
    exams: [{ id: EXAM, titleEn: "CRPC", titleBn: "সিআরপিসি" }],
    orderId: "oooooooo-0000-4000-8000-000000000011",
    tranId: "TRAN-PLACE-1",
    grantId: null,
  },
]);

function studentRoutes(extra: Record<string, unknown> = {}) {
  return {
    [`GET /api/v1/students/${STUDENT}`]: rahim,
    [`GET /api/v1/students/${STUDENT}/live-exam-access`]: access,
    [`GET /api/v1/students/${STUDENT}/live-exam-results`]: pageOf([]),
    ...extra,
  };
}

function renderStudentPage(user: typeof fixtures.masterAdmin | typeof fixtures.systemAdmin) {
  return renderWithProviders(
    <Suspense fallback={null}>
      <StudentPage params={resolved({ id: STUDENT })} />
    </Suspense>,
    { user, locale: "bn", route: `/staff/students/${STUDENT}` },
  );
}

/** Lets the screen's requests start, so "no change" is checked after they could have happened. */
function settle() {
  return new Promise((resolve) => setTimeout(resolve, 30));
}

describe("placement: exam page", () => {
  it("shows the answer switch for an MCQ exam", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${EXAM}`]: {
        id: EXAM,
        examType: "MCQ",
        titleEn: "CRPC",
        titleBn: "সিআরপিসি",
        answersReleasedAt: null,
      },
    });
    renderWithProviders(<ExamPage />, { user: fixtures.masterAdmin, locale: "bn" });

    expect(await screen.findByRole("switch")).toBeInTheDocument();
  });

  it("shows no answer switch for a written paper", async () => {
    const { calls } = mockApi({
      [`GET /api/v1/live-exams/${EXAM}`]: {
        id: EXAM,
        examType: "WRITTEN",
        titleEn: "CPC written",
        titleBn: "সিপিসি লিখিত",
        answersReleasedAt: null,
      },
    });
    renderWithProviders(<ExamPage />, { user: fixtures.masterAdmin, locale: "bn" });

    await waitFor(() => expect(calls.some((call) => call.path === `/api/v1/live-exams/${EXAM}`)).toBe(true));
    await settle();
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });
});

describe("placement: student page", () => {
  it("shows one access list, and it is the only one on the page", async () => {
    mockApi(studentRoutes());
    renderStudentPage(fixtures.masterAdmin);

    expect(await screen.findAllByText("হাতে দেওয়া")).not.toHaveLength(0);
    expect(screen.getAllByRole("heading", { name: "পরীক্ষার অ্যাক্সেস" })).toHaveLength(1);
  });

  it("gives Master Admin grant, revoke, the roster link, suspend and delete", async () => {
    mockApi(studentRoutes());
    renderStudentPage(fixtures.masterAdmin);

    expect(await screen.findByRole("button", { name: "হাতে অ্যাক্সেস দিন" })).toBeInTheDocument();
    expect((await screen.findAllByRole("button", { name: "অ্যাক্সেস বাতিল" })).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "সরাসরি ভর্তিতে যোগ করুন" }).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "নিষ্ক্রিয় করুন" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "মুছে ফেলুন" })).toBeInTheDocument();
  });

  it("gives System Admin grant only, with no revoke, roster link, suspend or delete", async () => {
    mockApi(studentRoutes());
    renderStudentPage(fixtures.systemAdmin);

    expect(await screen.findByRole("button", { name: "হাতে অ্যাক্সেস দিন" })).toBeInTheDocument();
    expect(await screen.findByText("অ্যাক্সেস বাতিল করতে পারেন শুধু মাস্টার অ্যাডমিন।")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "অ্যাক্সেস বাতিল" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "সরাসরি ভর্তিতে যোগ করুন" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "নিষ্ক্রিয় করুন" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "মুছে ফেলুন" })).not.toBeInTheDocument();
  });

  it("shows the paid-orders refusal with a way to suspend, and no raw code", async () => {
    const api = mockApi(
      studentRoutes({
        [`DELETE /api/v1/students/${STUDENT}`]: apiError(409, "ACCOUNT_HAS_PAID_ORDERS", "Paid orders exist."),
        [`PATCH /api/v1/students/${STUDENT}/status`]: null,
      }),
    );
    renderStudentPage(fixtures.masterAdmin);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "মুছে ফেলুন" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText("নাম লিখে নিশ্চিত করুন"), "রহিম উদ্দিন");
    await user.click(within(dialog).getByRole("button", { name: "মুছে ফেলুন" }));

    expect(await within(dialog).findByText(PAID_MESSAGE)).toBeInTheDocument();
    expect(screen.queryByText("ACCOUNT_HAS_PAID_ORDERS")).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "নিষ্ক্রিয় করুন" }));
    await waitFor(() => expect(api.calls.find((call) => call.method === "PATCH")?.body).toEqual({ status: "INACTIVE" }));
  });

  it("goes back to the students list after a successful delete", async () => {
    const api = mockApi(studentRoutes({ [`DELETE /api/v1/students/${STUDENT}`]: null }));
    renderStudentPage(fixtures.masterAdmin);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "মুছে ফেলুন" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText("নাম লিখে নিশ্চিত করুন"), "রহিম উদ্দিন");
    await user.click(within(dialog).getByRole("button", { name: "মুছে ফেলুন" }));

    await waitFor(() => expect(routerState.push).toHaveBeenCalledWith("/staff/students"));
    expect(api.calls.some((call) => call.method === "DELETE")).toBe(true);
  });
});

describe("placement: instructors page", () => {
  const instructor = (id: string, fullName: string, status: "ACTIVE" | "INACTIVE" | "PENDING_ACTIVATION") => ({
    id,
    fullName,
    phone: "01711000021",
    email: "teacher@example.com",
    status,
    createdAt: "2026-10-01T00:00:00Z",
    modules: [],
  });

  it("gives an active instructor suspend and delete", async () => {
    mockApi({
      "GET /api/v1/instructors": pageOf([instructor(ACTIVE_INSTRUCTOR, "ফারিহা রহমান", "ACTIVE")]),
    });
    renderWithProviders(<InstructorsPage />, { user: fixtures.masterAdmin, locale: "bn", route: "/staff/instructors" });

    expect((await screen.findAllByRole("button", { name: "নিষ্ক্রিয় করুন" })).length).toBeGreaterThan(0);
    expect((await screen.findAllByRole("button", { name: "মুছে ফেলুন" })).length).toBeGreaterThan(0);
  });

  it("gives a suspended instructor restore and delete, and restore sends ACTIVE", async () => {
    const api = mockApi({
      "GET /api/v1/instructors": pageOf([instructor(SUSPENDED_INSTRUCTOR, "নাসরিন সুলতানা", "INACTIVE")]),
      [`PATCH /api/v1/instructors/${SUSPENDED_INSTRUCTOR}/status`]: null,
    });
    renderWithProviders(<InstructorsPage />, { user: fixtures.masterAdmin, locale: "bn", route: "/staff/instructors" });
    const user = userEvent.setup();

    await user.click((await screen.findAllByRole("button", { name: "আবার সক্রিয় করুন" }))[0]!);
    await waitFor(() => expect(api.calls.find((call) => call.method === "PATCH")?.body).toEqual({ status: "ACTIVE" }));
  });

  it("gives a pending instructor the reissue menu and no suspend or delete", async () => {
    mockApi({
      "GET /api/v1/instructors": pageOf([instructor(PENDING_INSTRUCTOR, "তানভীর আহমেদ", "PENDING_ACTIVATION")]),
    });
    renderWithProviders(<InstructorsPage />, { user: fixtures.masterAdmin, locale: "bn", route: "/staff/instructors" });
    const user = userEvent.setup();

    await user.click((await screen.findAllByRole("button", { name: "তানভীর আহমেদ-এর কাজ" }))[0]!);
    expect(await screen.findByRole("menuitem", { name: "কোড আবার দিন" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "নিষ্ক্রিয় করুন" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "মুছে ফেলুন" })).not.toBeInTheDocument();
  });

  it("shows the marked-work refusal with a way to suspend", async () => {
    const api = mockApi({
      "GET /api/v1/instructors": pageOf([instructor(ACTIVE_INSTRUCTOR, "ফারিহা রহমান", "ACTIVE")]),
      [`DELETE /api/v1/instructors/${ACTIVE_INSTRUCTOR}`]: apiError(409, "ACCOUNT_HAS_MARKED_WORK", "Marked work exists."),
      [`PATCH /api/v1/instructors/${ACTIVE_INSTRUCTOR}/status`]: null,
    });
    renderWithProviders(<InstructorsPage />, { user: fixtures.masterAdmin, locale: "bn", route: "/staff/instructors" });
    const user = userEvent.setup();

    await user.click((await screen.findAllByRole("button", { name: "মুছে ফেলুন" }))[0]!);
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByLabelText("নাম লিখে নিশ্চিত করুন"), "ফারিহা রহমান");
    await user.click(within(dialog).getByRole("button", { name: "মুছে ফেলুন" }));

    expect(await within(dialog).findByText(MARKED_WORK_MESSAGE)).toBeInTheDocument();
    expect(screen.queryByText("ACCOUNT_HAS_MARKED_WORK")).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "নিষ্ক্রিয় করুন" }));
    await waitFor(() => expect(api.calls.find((call) => call.method === "PATCH")?.body).toEqual({ status: "INACTIVE" }));
  });
});

describe("placement: students list", () => {
  const listRoutes = {
    "GET /api/v1/students": pageOf([rahim]),
  };

  it("shows suspend and delete in the row menu to Master Admin", async () => {
    mockApi(listRoutes);
    renderWithProviders(<StudentsPage />, { user: fixtures.masterAdmin, locale: "bn", route: "/staff/students" });
    const user = userEvent.setup();

    await user.click((await screen.findAllByRole("button", { name: "রহিম উদ্দিন-এর কাজ" }))[0]!);
    expect(await screen.findByRole("menuitem", { name: "বন্ধ করুন" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "মুছুন" })).toBeInTheDocument();
  });

  it("shows no suspend and no delete in the row menu to System Admin", async () => {
    mockApi(listRoutes);
    renderWithProviders(<StudentsPage />, { user: fixtures.systemAdmin, locale: "bn", route: "/staff/students" });
    const user = userEvent.setup();

    await user.click((await screen.findAllByRole("button", { name: "রহিম উদ্দিন-এর কাজ" }))[0]!);
    expect(await screen.findByRole("menuitem", { name: "শিক্ষার্থীর পাতা খুলুন" })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "বন্ধ করুন" })).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "মুছুন" })).not.toBeInTheDocument();
  });

  it("shows the paid-orders refusal with a way to suspend, and no raw code", async () => {
    mockApi({
      ...listRoutes,
      [`DELETE /api/v1/students/${STUDENT}`]: apiError(409, "ACCOUNT_HAS_PAID_ORDERS", "Paid orders exist."),
    });
    renderWithProviders(<StudentsPage />, { user: fixtures.masterAdmin, locale: "bn", route: "/staff/students" });
    const user = userEvent.setup();

    await user.click((await screen.findAllByRole("button", { name: "রহিম উদ্দিন-এর কাজ" }))[0]!);
    await user.click(await screen.findByRole("menuitem", { name: "মুছুন" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("নাম লিখে নিশ্চিত করুন"), "রহিম উদ্দিন");
    await user.click(within(dialog).getByRole("button", { name: "মুছে ফেলুন" }));

    expect(await within(dialog).findByText(PAID_MESSAGE)).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "নিষ্ক্রিয় করুন" })).toBeInTheDocument();
    expect(screen.queryByText("ACCOUNT_HAS_PAID_ORDERS")).not.toBeInTheDocument();
  });

  it("shows the generic message for an unknown code, and never the code", async () => {
    mockApi({
      ...listRoutes,
      [`DELETE /api/v1/students/${STUDENT}`]: apiError(500, "SOME_NEW_CODE", "Something new."),
    });
    renderWithProviders(<StudentsPage />, { user: fixtures.masterAdmin, locale: "bn", route: "/staff/students" });
    const user = userEvent.setup();

    await user.click((await screen.findAllByRole("button", { name: "রহিম উদ্দিন-এর কাজ" }))[0]!);
    await user.click(await screen.findByRole("menuitem", { name: "মুছুন" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("নাম লিখে নিশ্চিত করুন"), "রহিম উদ্দিন");
    await user.click(within(dialog).getByRole("button", { name: "মুছে ফেলুন" }));

    expect(await within(dialog).findByText(GENERIC_MESSAGE)).toBeInTheDocument();
    expect(screen.queryByText("SOME_NEW_CODE")).not.toBeInTheDocument();
  });
});
