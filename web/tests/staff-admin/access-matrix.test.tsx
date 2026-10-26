import type { ReactElement } from "react";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import StaffAnnouncementsPage from "@/app/(staff)/staff/announcements/page";
import InstructorsPage from "@/app/(staff)/staff/instructors/page";
import StaffOrdersPage from "@/app/(staff)/staff/orders/page";
import StaffSystemStatusPage from "@/app/(staff)/staff/system-status/page";
import StudentDetailPage from "@/app/(staff)/staff/students/[id]/page";
import StudentsPage from "@/app/(staff)/staff/students/page";
import { AnswerReleaseSwitch } from "@/components/staff/access/answer-release-switch";
import { StudentAccessSection } from "@/components/staff/access/student-access-section";
import { PendingOrders } from "@/components/staff/orders/pending-orders";
import { AccountStatusToggle, DeleteAccountButton } from "@/components/staff/status/account-controls";
import bnStaffAdmin from "@/i18n/messages/bn/staffAdmin";
import type { Me } from "@/lib/api/exam/types";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";
import { routerState } from "../helpers/router";
import "./copy-setup";

/*
 * The access matrix in the plan (section 5), checked page by page and role by
 * role. A control the API would refuse is never rendered as a control: it is
 * either left out, or shown as a sentence on who can do it. A role the API
 * refuses makes no request at all.
 */

const studentId = "11111111-1111-4111-8111-111111111111";
const instructorId = "44444444-4444-4444-8444-444444444444";
const examId = "eeeeeeee-0000-4000-8000-000000000001";

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
const access = {
  content: [
    {
      moduleId: "aaaaaaaa-0000-4000-8000-000000000001",
      moduleTitleEn: "19th BJS",
      moduleTitleBn: "১৯তম বিজেএস",
      via: "MANUAL",
      exams: [],
      orderId: null,
      tranId: null,
      grantId: "gggggggg-0000-4000-8000-000000000001",
    },
  ],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
};
const pendingOrder = {
  content: [
    {
      orderId: "oooooooo-0000-4000-8000-000000000001",
      tranId: "TRAN-MATRIX-1",
      status: "INITIATED",
      amount: 2500,
      scope: "MODULE",
      moduleTitleEn: "19th BJS",
      moduleTitleBn: "১৯তম বিজেএস",
      examTitleEn: null,
      examTitleBn: null,
      studentId,
      studentName: "রহিম উদ্দিন",
      studentPhone: "01711000002",
      studentCode: "STU-0001",
      createdAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};
const release = {
  id: examId,
  examType: "MCQ",
  titleEn: "CRPC",
  titleBn: "সিআরপিসি",
  answersReleasedAt: null,
};
const checks = { checks: [{ name: "seed.off", ok: true, note: "The demo seed is off." }] };
const modules = {
  content: [{ id: "aaaaaaaa-0000-4000-8000-000000000001", titleEn: "19th BJS", titleBn: "১৯তম বিজেএস", status: "PUBLISHED" }],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
};

/** Every route an admin screen calls, answered with usable data. */
const routes = {
  [`GET /api/v1/students/${studentId}/live-exam-access`]: access,
  "GET /api/v1/live-exam-orders": pendingOrder,
  [`GET /api/v1/live-exams/${examId}`]: release,
  "GET /api/v1/announcements": emptyPage,
  "GET /api/v1/live-exam-modules": modules,
  "GET /api/v1/system-status": checks,
};

/** Button names a System Admin must never see (section 5, admin-only actions). */
const forbiddenForSystemAdmin = [
  bnStaffAdmin.account.suspend,
  bnStaffAdmin.account.restore,
  bnStaffAdmin.account.deleteAction,
  bnStaffAdmin.orders.confirm,
  bnStaffAdmin.orders.reject,
  bnStaffAdmin.access.revoke,
  bnStaffAdmin.announce.send,
];

function StudentPageControls() {
  return (
    <>
      <StudentAccessSection studentId={studentId} studentName="রহিম উদ্দিন" />
      <AccountStatusToggle kind="students" id={studentId} status="ACTIVE" />
      <DeleteAccountButton kind="students" id={studentId} name="রহিম উদ্দিন" />
    </>
  );
}

function InstructorPageControls() {
  return (
    <>
      <AccountStatusToggle kind="instructors" id={instructorId} status="ACTIVE" />
      <DeleteAccountButton kind="instructors" id={instructorId} name="নাসরিন সুলতানা" />
    </>
  );
}

function renderAs(ui: ReactElement, user: Me | null) {
  return renderWithProviders(ui, { user, locale: "bn" });
}

/** Lets any request the screen would make start, so "no request" is checked after it could have happened. */
function settle() {
  return new Promise((resolve) => setTimeout(resolve, 30));
}

describe("access matrix: student page", () => {
  it("Master Admin sees revoke, suspend and delete", async () => {
    mockApi(routes);
    renderAs(<StudentPageControls />, fixtures.masterAdmin);

    expect((await screen.findAllByRole("button", { name: bnStaffAdmin.access.revoke })).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: bnStaffAdmin.account.suspend })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: bnStaffAdmin.account.deleteAction })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: bnStaffAdmin.access.grantOpen })).toBeInTheDocument();
  });

  it("System Admin sees grant by hand, and no action on the account", async () => {
    const { calls } = mockApi(routes);
    renderAs(<StudentPageControls />, fixtures.systemAdmin);

    expect((await screen.findAllByText("হাতে দেওয়া"))[0]).toBeInTheDocument();
    expect(screen.getByRole("button", { name: bnStaffAdmin.access.grantOpen })).toBeInTheDocument();
    expect(screen.getByText(bnStaffAdmin.access.revokeOnlyMaster)).toBeInTheDocument();
    for (const name of forbiddenForSystemAdmin) {
      expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    }
    expect(calls.some((call) => call.method !== "GET")).toBe(false);
  });

  it("Instructor sees nothing, and no request is made", async () => {
    const { calls } = mockApi(routes);
    renderAs(<StudentPageControls />, fixtures.instructor);
    await settle();

    expect(screen.queryAllByRole("button")).toHaveLength(0);
    expect(screen.queryByText(bnStaffAdmin.access.title)).not.toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});

describe("access matrix: instructors page", () => {
  it("Master Admin sees suspend and delete for an instructor", () => {
    mockApi(routes);
    renderAs(<InstructorPageControls />, fixtures.masterAdmin);

    expect(screen.getByRole("button", { name: bnStaffAdmin.account.suspend })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: bnStaffAdmin.account.deleteAction })).toBeInTheDocument();
  });

  it("System Admin and Instructor see neither control, and no request is made", async () => {
    const { calls } = mockApi(routes);
    const { unmount } = renderAs(<InstructorPageControls />, fixtures.systemAdmin);
    expect(screen.queryAllByRole("button")).toHaveLength(0);
    unmount();

    renderAs(<InstructorPageControls />, fixtures.instructor);
    await settle();
    expect(screen.queryAllByRole("button")).toHaveLength(0);
    expect(calls).toHaveLength(0);
  });
});

describe("access matrix: exam page", () => {
  it("Master Admin and System Admin see the answer switch", async () => {
    mockApi(routes);
    const { unmount } = renderAs(<AnswerReleaseSwitch examId={examId} />, fixtures.masterAdmin);
    expect(await screen.findByRole("switch")).toBeInTheDocument();
    unmount();

    renderAs(<AnswerReleaseSwitch examId={examId} />, fixtures.systemAdmin);
    expect(await screen.findByRole("switch")).toBeInTheDocument();
  });

  it("Instructor sees no switch, and the exam is not requested", async () => {
    const { calls } = mockApi(routes);
    renderAs(<AnswerReleaseSwitch examId={examId} />, fixtures.instructor);
    await settle();

    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});

describe("access matrix: orders page", () => {
  it("Master Admin confirms and rejects", async () => {
    mockApi(routes);
    renderAs(<StaffOrdersPage />, fixtures.masterAdmin);

    expect((await screen.findAllByText("TRAN-MATRIX-1"))[0]).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: bnStaffAdmin.orders.confirm })).not.toHaveLength(0);
    expect(screen.getAllByRole("button", { name: bnStaffAdmin.orders.reject })).not.toHaveLength(0);
  });

  it("System Admin sees the list and no decision, with a sentence on who can decide", async () => {
    mockApi(routes);
    renderAs(<StaffOrdersPage />, fixtures.systemAdmin);

    expect((await screen.findAllByText("TRAN-MATRIX-1"))[0]).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: bnStaffAdmin.orders.confirm })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: bnStaffAdmin.orders.reject })).not.toBeInTheDocument();
    expect(screen.getByText(bnStaffAdmin.orders.sysAdminNote)).toBeInTheDocument();
  });

  it("Instructor is sent to not found, and no request is made", async () => {
    const { calls } = mockApi(routes);
    renderAs(<StaffOrdersPage />, fixtures.instructor);
    await settle();

    expect(screen.queryByText("TRAN-MATRIX-1")).not.toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it("the list shows no decision button to System Admin, even on its own", async () => {
    mockApi(routes);
    renderAs(<PendingOrders />, fixtures.systemAdmin);

    expect((await screen.findAllByText("TRAN-MATRIX-1"))[0]).toBeInTheDocument();
    for (const name of [bnStaffAdmin.orders.confirm, bnStaffAdmin.orders.reject]) {
      expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    }
  });
});

describe("access matrix: announcements page", () => {
  it("Master Admin gets the form and the history", async () => {
    const { calls } = mockApi(routes);
    renderAs(<StaffAnnouncementsPage />, fixtures.masterAdmin);

    expect(screen.getByRole("button", { name: bnStaffAdmin.announce.send })).toBeInTheDocument();
    expect(screen.getByLabelText(bnStaffAdmin.announce.titleEn)).toBeInTheDocument();
    expect(await screen.findByText(bnStaffAdmin.history.empty)).toBeInTheDocument();
    expect(calls.some((call) => call.path === "/api/v1/announcements")).toBe(true);
  });

  it("System Admin gets information only: no form, no send, and no history request", async () => {
    const { calls } = mockApi(routes);
    renderAs(<StaffAnnouncementsPage />, fixtures.systemAdmin);
    await settle();

    expect(screen.queryByRole("button", { name: bnStaffAdmin.announce.send })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(bnStaffAdmin.announce.titleEn)).not.toBeInTheDocument();
    expect(screen.getByText(bnStaffAdmin.announce.readOnly)).toBeInTheDocument();
    expect(calls.some((call) => call.path === "/api/v1/announcements")).toBe(false);
  });

  it("Instructor gets not found, and no request is made", async () => {
    const { calls } = mockApi(routes);
    renderAs(<StaffAnnouncementsPage />, fixtures.instructor);
    await settle();

    expect(screen.queryByLabelText(bnStaffAdmin.announce.titleEn)).not.toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});

describe("access matrix: system status page", () => {
  it("Master Admin sees the checks", async () => {
    mockApi(routes);
    renderAs(<StaffSystemStatusPage />, fixtures.masterAdmin);

    expect((await screen.findAllByText(bnStaffAdmin.status.names.seedOff))[0]).toBeInTheDocument();
  });

  it("System Admin and Instructor are sent back to the staff home, and no check is requested", async () => {
    const { calls } = mockApi(routes);
    renderAs(<StaffSystemStatusPage />, fixtures.systemAdmin);
    expect(routerState.replace).toHaveBeenLastCalledWith("/staff");

    renderAs(<StaffSystemStatusPage />, fixtures.instructor);
    await settle();
    expect(routerState.replace).toHaveBeenLastCalledWith("/staff");
    expect(calls.some((call) => call.path === "/api/v1/system-status")).toBe(false);
  });
});

describe("access matrix: System Admin never sees a control the API refuses", () => {
  it("renders the admin controls together, and none of the refused ones is there", async () => {
    mockApi(routes);
    renderAs(
      <>
        <StudentPageControls />
        <PendingOrders />
        <AnswerReleaseSwitch examId={examId} />
        <InstructorPageControls />
      </>,
      fixtures.systemAdmin,
    );

    expect((await screen.findAllByText("TRAN-MATRIX-1"))[0]).toBeInTheDocument();
    for (const name of forbiddenForSystemAdmin) {
      expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
    }
    // Release is allowed to System Admin, so its switch stays.
    expect(await screen.findByRole("switch")).toBeInTheDocument();
  });
});

/* The three account pages, rendered whole: the same matrix, checked on the page the role reaches. */

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

/** The row menu labels of the students list, in Bangla. */
const studentMenu = { deactivate: "বন্ধ করুন", delete: "মুছুন" };

const studentRow = {
  id: studentId,
  fullName: "রহিম উদ্দিন",
  phone: "01711000002",
  email: "rahim@example.com",
  role: "STUDENT",
  status: "ACTIVE",
  studentCode: "STU-0001",
};

const instructorRow = {
  id: instructorId,
  fullName: "নাসরিন সুলতানা",
  phone: "01711000003",
  email: "nasrin@example.com",
  status: "ACTIVE",
  createdAt: "2026-10-01T00:00:00Z",
  modules: [],
};

const pageRoutes = {
  ...routes,
  "GET /api/v1/students": { content: [studentRow], page: 0, size: 20, totalElements: 1, totalPages: 1 },
  "GET /api/v1/instructors": { content: [instructorRow], page: 0, size: 20, totalElements: 1, totalPages: 1 },
  [`GET /api/v1/students/${studentId}`]: studentRow,
  [`GET /api/v1/students/${studentId}/live-exam-results`]: emptyPage,
};

const NOT_FOUND = "পাতাটি পাওয়া যায়নি";

describe("access matrix: students list page", () => {
  it("Master Admin gets suspend and delete in the row menu", async () => {
    mockApi(pageRoutes);
    renderAs(<StudentsPage />, fixtures.masterAdmin);
    const user = userEvent.setup();

    await user.click((await screen.findAllByRole("button", { name: "রহিম উদ্দিন-এর কাজ" }))[0]!);
    expect(await screen.findByRole("menuitem", { name: studentMenu.deactivate })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: studentMenu.delete })).toBeInTheDocument();
  });

  it("System Admin gets no suspend and no delete, and makes only reads", async () => {
    const { calls } = mockApi(pageRoutes);
    renderAs(<StudentsPage />, fixtures.systemAdmin);
    const user = userEvent.setup();

    await user.click((await screen.findAllByRole("button", { name: "রহিম উদ্দিন-এর কাজ" }))[0]!);
    expect(await screen.findByRole("menuitem", { name: "শিক্ষার্থীর পাতা খুলুন" })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: studentMenu.deactivate })).not.toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: studentMenu.delete })).not.toBeInTheDocument();
    expect(calls.every((call) => call.method === "GET")).toBe(true);
  });

  it("Instructor is sent to not found, and no request is made", async () => {
    const { calls } = mockApi(pageRoutes);
    renderAs(<StudentsPage />, fixtures.instructor);
    await settle();

    expect(screen.getByText(NOT_FOUND)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});

describe("access matrix: student page, whole", () => {
  it("Instructor is sent to not found, and no request is made", async () => {
    const { calls } = mockApi(pageRoutes);
    renderAs(<StudentDetailPage params={resolved({ id: studentId })} />, fixtures.instructor);
    await settle();

    expect(screen.getByText(NOT_FOUND)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});

describe("access matrix: instructors page, whole", () => {
  it("Master Admin gets suspend and delete for an active instructor", async () => {
    mockApi(pageRoutes);
    renderAs(<InstructorsPage />, fixtures.masterAdmin);

    expect((await screen.findAllByRole("button", { name: bnStaffAdmin.account.suspend })).length).toBeGreaterThan(0);
    expect((await screen.findAllByRole("button", { name: bnStaffAdmin.account.deleteAction })).length).toBeGreaterThan(0);
  });

  it("System Admin is sent to not found, and no request is made", async () => {
    const { calls } = mockApi(pageRoutes);
    renderAs(<InstructorsPage />, fixtures.systemAdmin);
    await settle();

    expect(screen.getByText(NOT_FOUND)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: bnStaffAdmin.account.deleteAction })).not.toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});
