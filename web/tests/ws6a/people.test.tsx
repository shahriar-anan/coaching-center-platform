import { Suspense } from "react";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import StudentsPage from "@/app/(staff)/staff/students/page";
import StudentDetailPage from "@/app/(staff)/staff/students/[id]/page";
import InstructorsPage from "@/app/(staff)/staff/instructors/page";
import ParticipationPage from "@/app/(staff)/staff/participation/page";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";

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
    // Delete sits in the row menu, so open it first.
    await userEvent.click((await screen.findAllByRole("button", { name: "Rahim Uddin-এর কাজ" }))[0]);
    expect(await screen.findByRole("menuitem", { name: "মুছুন" })).toBeInTheDocument();
    unmount();

    renderWithProviders(<StudentsPage />, { user: fixtures.systemAdmin, route: "/staff/students" });
    expect((await screen.findAllByText("Rahim Uddin")).length).toBeGreaterThan(0);
    await userEvent.click((await screen.findAllByRole("button", { name: "Rahim Uddin-এর কাজ" }))[0]);
    expect(await screen.findByRole("menuitem", { name: "শিক্ষার্থীর পাতা খুলুন" })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "মুছুন" })).toBeNull();
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

    expect((await screen.findAllByText("রোস্টার")).length).toBeGreaterThan(0);
    expect((await screen.findAllByText("পরীক্ষা কেনা")).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "সরাসরি ভর্তিতে যোগ করুন" }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "TRAN-1" })[0]).toHaveAttribute("href", "/staff/payments/TRAN-1");
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
    expect(await screen.findByText("এই শিক্ষার্থীর এখনো কোনো অ্যাক্সেস নেই।")).toBeInTheDocument();
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
  it("shows an MCQ row as scored automatically, with no marking numbers and no \"undefined\"", async () => {
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
          // The API leaves out the written-only counts for an MCQ.
          retakes: 0,
        },
      ]),
      "GET /api/v1/live-exam-modules": page([]),
    });
    renderWithProviders(<ParticipationPage />, { user: fixtures.instructor, route: "/staff/participation?type=MCQ" });

    expect((await screen.findAllByText("সিআরপিসি")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("স্বয়ংক্রিয় মূল্যায়ন").length).toBeGreaterThan(0);
    expect(screen.queryByText(/মূল্যায়িত/)).toBeNull();
    expect(document.body.textContent).not.toContain("undefined");
  });
});

