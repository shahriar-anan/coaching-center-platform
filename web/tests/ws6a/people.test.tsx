import { Suspense } from "react";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import StudentsPage from "@/app/(staff)/staff/students/page";
import StudentDetailPage from "@/app/(staff)/staff/students/[id]/page";
import InstructorsPage from "@/app/(staff)/staff/instructors/page";
import ParticipationPage from "@/app/(staff)/staff/participation/page";
import { SubmissionForm } from "@/components/staff/people/submission-form";
import type { SubmissionRow } from "@/lib/api/exam/staff-people";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";

// The PDF viewer loads pdf.js; the form tests only need to know it is not there for the wrong roles.
vi.mock("@/components/pdf/pdf-viewer", () => ({ PdfViewer: () => null }));

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

const STUDENT_ID = "cccccccc-0000-4000-8000-000000000001";

const studentRow = {
  id: STUDENT_ID,
  fullName: "Rahim Uddin",
  phone: "+8801711000001",
  email: "rahim@example.com",
  role: "STUDENT",
  status: "ACTIVE",
  studentCode: "STU-0001",
};

const page = (content: unknown[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });

describe("students", () => {
  it("lets Master Admin delete a student, but not System Admin", async () => {
    mockApi({ "GET /api/v1/students": page([studentRow]) });
    const { unmount } = renderWithProviders(<StudentsPage />, { user: fixtures.masterAdmin, route: "/staff/students" });
    expect(await screen.findAllByText("Rahim Uddin")).not.toHaveLength(0);
    expect(screen.getAllByRole("button", { name: "মুছুন" }).length).toBeGreaterThan(0);
    unmount();

    renderWithProviders(<StudentsPage />, { user: fixtures.systemAdmin, route: "/staff/students" });
    expect((await screen.findAllByText("Rahim Uddin")).length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: "মুছুন" })).toBeNull();
  });

  it("creates a student with name, phone and email, and shows the one-time code once", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/students": page([]),
      "POST /api/v1/students": {
        id: STUDENT_ID,
        fullName: "Nazma Akter",
        phone: "01711000002",
        email: "nazma@example.com",
        role: "STUDENT",
        status: "PENDING_ACTIVATION",
        activationCode: "482913",
        studentCode: "STU-0002",
      },
    });
    renderWithProviders(<StudentsPage />, { user: fixtures.systemAdmin, route: "/staff/students" });

    await user.click(await screen.findByRole("button", { name: "নতুন শিক্ষার্থী" }));
    await user.type(screen.getByLabelText(/^নাম/), "Nazma Akter");
    await user.type(screen.getByLabelText(/^ফোন/), "01711000002");
    await user.type(screen.getByLabelText(/^ইমেইল/), "nazma@example.com");
    await user.click(screen.getByRole("button", { name: "যোগ করুন" }));

    expect(await screen.findByText("482913")).toBeInTheDocument();
    const sent = api.calls.find((call) => call.method === "POST" && call.path === "/api/v1/students");
    expect(sent?.body).toEqual({ fullName: "Nazma Akter", phone: "01711000002", email: "nazma@example.com" });
    expect(sent?.body).not.toHaveProperty("password");
  });

  it("refuses a phone number that is not 01 and nine digits, without calling the API", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "GET /api/v1/students": page([]) });
    renderWithProviders(<StudentsPage />, { user: fixtures.systemAdmin, route: "/staff/students" });

    await user.click(await screen.findByRole("button", { name: "নতুন শিক্ষার্থী" }));
    await user.type(screen.getByLabelText(/^নাম/), "Nazma");
    await user.type(screen.getByLabelText(/^ফোন/), "0171");
    await user.type(screen.getByLabelText(/^ইমেইল/), "nazma@example.com");
    await user.click(screen.getByRole("button", { name: "যোগ করুন" }));

    await waitFor(() => expect(screen.getByLabelText(/^ফোন/)).toHaveAttribute("aria-invalid", "true"));
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });
});

describe("student access", () => {
  it("shows each reason, and the roster link for Master Admin only", async () => {
    mockApi({
      [`GET /api/v1/students/${STUDENT_ID}`]: studentRow,
      [`GET /api/v1/students/${STUDENT_ID}/live-exam-access`]: page([
        {
          moduleId: "bbbbbbbb-0000-4000-8000-000000000001",
          moduleTitleEn: "19th BJS",
          moduleTitleBn: "১৯তম বিজেএস",
          via: "ROSTER",
          exams: [],
          orderId: null,
          tranId: null,
        },
        {
          moduleId: "bbbbbbbb-0000-4000-8000-000000000001",
          moduleTitleEn: "19th BJS",
          moduleTitleBn: "১৯তম বিজেএস",
          via: "EXAM_PURCHASE",
          exams: [{ id: "e1", titleEn: "CrPC", titleBn: "সিআরপিসি" }],
          orderId: "o1",
          tranId: "TRAN-1",
        },
      ]),
    });
    renderWithProviders(
      <Suspense fallback={null}>
        <StudentDetailPage params={resolved({ id: STUDENT_ID })} />
      </Suspense>,
      { user: fixtures.masterAdmin, route: `/staff/students/${STUDENT_ID}` },
    );

    expect((await screen.findAllByText("সরাসরি ভর্তি")).length).toBeGreaterThan(0);
    expect((await screen.findAllByText("পরীক্ষা কেনা")).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "সরাসরি ভর্তিতে যোগ করুন" }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "পেমেন্ট দেখুন" })[0]).toHaveAttribute("href", "/staff/payments/TRAN-1");
  });

  it("gives System Admin the same list without the roster link", async () => {
    mockApi({
      [`GET /api/v1/students/${STUDENT_ID}`]: studentRow,
      [`GET /api/v1/students/${STUDENT_ID}/live-exam-access`]: page([]),
    });
    renderWithProviders(
      <Suspense fallback={null}>
        <StudentDetailPage params={resolved({ id: STUDENT_ID })} />
      </Suspense>,
      { user: fixtures.systemAdmin, route: `/staff/students/${STUDENT_ID}` },
    );
    expect(await screen.findByText("কোনো পরীক্ষায় অ্যাক্সেস নেই")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "সরাসরি ভর্তিতে যোগ করুন" })).toBeNull();
  });
});

describe("instructors", () => {
  it("sends the status and assigned filters from the URL", async () => {
    const api = mockApi({ "GET /api/v1/instructors": page([]) });
    renderWithProviders(<InstructorsPage />, {
      user: fixtures.masterAdmin,
      route: "/staff/instructors?status=PENDING_ACTIVATION&assigned=false",
    });
    expect(await screen.findByText("কোনো শিক্ষক পাওয়া যায়নি")).toBeInTheDocument();
    const sent = api.calls.find((call) => call.path === "/api/v1/instructors");
    expect(sent?.search).toContain("status=PENDING_ACTIVATION");
    expect(sent?.search).toContain("assigned=false");
  });

  it("is not a page for System Admin", async () => {
    mockApi({});
    renderWithProviders(<InstructorsPage />, { user: fixtures.systemAdmin, route: "/staff/instructors" });
    expect(await screen.findByText("পাতাটি পাওয়া যায়নি")).toBeInTheDocument();
  });
});

describe("participation", () => {
  it("shows a dash in the written-only columns of an MCQ row", async () => {
    mockApi({
      "GET /api/v1/live-exam-participation": page([
        {
          examId: "e1",
          moduleId: "m1",
          moduleTitleEn: "19th BJS",
          moduleTitleBn: "১৯তম বিজেএস",
          examType: "MCQ",
          titleEn: "CrPC",
          titleBn: "সিআরপিসি",
          liveAt: "2026-10-25T06:00:00Z",
          endAt: "2026-10-25T06:40:00Z",
          sitting: 84,
          submitted: 71,
          failedToSubmit: 13,
          awaitingMarking: null,
          draftsAwaitingPublish: null,
          published: null,
          retakes: 0,
        },
      ]),
      "GET /api/v1/live-exam-participation/summary": { awaitingMarking: 62, draftsAwaitingPublish: 9 },
      "GET /api/v1/live-exam-modules": page([]),
    });
    renderWithProviders(<ParticipationPage />, { user: fixtures.instructor, route: "/staff/participation?type=MCQ" });

    expect((await screen.findAllByText("সিআরপিসি")).length).toBeGreaterThan(0);
    const cells = screen.getAllByText("—");
    expect(cells.length).toBeGreaterThanOrEqual(3);
  });
});

const written: SubmissionRow = {
  id: "p1",
  examId: "e5",
  studentId: STUDENT_ID,
  studentName: "Rahim Uddin",
  status: "SUBMITTED",
  score: null,
  maximum: 50,
  passed: null,
  kind: "LIVE",
  answerPdf: true,
  evaluatedPdf: null,
  evaluatorName: null,
  published: false,
};

describe("submission form by role", () => {
  it("gives an instructor the draft form and no publish button", () => {
    renderWithProviders(<SubmissionForm row={written} examTitle="Evidence" canMark canPublish={false} />, {
      user: fixtures.instructor,
    });
    expect(screen.getByRole("button", { name: "খসড়া নম্বর সেভ করুন" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "ফলাফল প্রকাশ করুন" })).toBeNull();
    expect(screen.getByText(/শিক্ষার্থী এখনো এটি দেখতে পাবে না/)).toBeInTheDocument();
  });

  it("gives System Admin read-only facts with the examiner's name, and no form", () => {
    renderWithProviders(
      <SubmissionForm row={{ ...written, evaluatorName: "Teacher Karim" }} examTitle="Evidence" canMark={false} canPublish={false} />,
      { user: fixtures.systemAdmin },
    );
    expect(screen.queryByRole("button", { name: "খসড়া নম্বর সেভ করুন" })).toBeNull();
    expect(screen.getByText("Teacher Karim")).toBeInTheDocument();
  });

  it("keeps Master Admin's publish disabled until a draft mark exists", () => {
    const { unmount } = renderWithProviders(
      <SubmissionForm row={written} examTitle="Evidence" canMark canPublish />,
      { user: fixtures.masterAdmin },
    );
    expect(screen.getByRole("button", { name: "ফলাফল প্রকাশ করুন" })).toBeDisabled();
    unmount();

    renderWithProviders(
      <SubmissionForm row={{ ...written, evaluatedPdf: true }} examTitle="Evidence" canMark canPublish />,
      { user: fixtures.masterAdmin },
    );
    expect(screen.getByRole("button", { name: "ফলাফল প্রকাশ করুন" })).toBeEnabled();
  });

  it("shows the no-answer line and no form when the paper has no answer", () => {
    renderWithProviders(
      <SubmissionForm row={{ ...written, answerPdf: false, status: "FAILED_TO_SUBMIT" }} examTitle="Evidence" canMark canPublish />,
      { user: fixtures.masterAdmin },
    );
    expect(screen.getByText("জমা হয়নি · নম্বর দেওয়া যাবে না")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "খসড়া নম্বর সেভ করুন" })).toBeNull();
    expect(within(document.body).queryByRole("button", { name: "ফলাফল প্রকাশ করুন" })).toBeNull();
  });
});
