import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import AccountPage from "@/app/(site)/account/page";
import PaymentsPage from "@/app/(site)/account/payments/page";
import NoticesPage from "@/app/(site)/notices/page";
import { NoticeBell } from "@/components/student/notice-bell";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe("account password", () => {
  it("keeps a wrong current password inline and keeps the values", async () => {
    mockApi({ "POST /api/v1/auth/change-password": () => apiError(400, "CURRENT_PASSWORD_INVALID") });
    renderWithProviders(<AccountPage />, { user: fixtures.student, route: "/account" });
    await userEvent.click(screen.getByRole("button", { name: "পাসওয়ার্ড বদলান" }));
    await userEvent.type(screen.getByLabelText(/বর্তমান পাসওয়ার্ড/), "old-password");
    await userEvent.type(screen.getByLabelText(/নতুন পাসওয়ার্ড/), "new-password1");
    await userEvent.click(screen.getByRole("button", { name: "পাসওয়ার্ড বদলান" }));
    expect(await screen.findByText("বর্তমান পাসওয়ার্ডটি সঠিক নয়।")).toBeInTheDocument();
    expect(screen.getByLabelText(/বর্তমান পাসওয়ার্ড/)).toHaveValue("old-password");
  });
});

describe("payments", () => {
  it("shows the empty sentence when there are no orders", async () => {
    mockApi({ "GET /api/v1/me/live-exam-orders": emptyPage });
    renderWithProviders(<PaymentsPage />, { user: fixtures.student, route: "/account/payments" });
    expect(await screen.findByText("এখনো কোনো পেমেন্ট নেই।")).toBeInTheDocument();
  });

  it("says the history is not available yet when the endpoint is missing", async () => {
    mockApi({ "GET /api/v1/me/live-exam-orders": () => apiError(404, "RESOURCE_NOT_FOUND") });
    renderWithProviders(<PaymentsPage />, { user: fixtures.student, route: "/account/payments" });
    expect(await screen.findByText("পেমেন্ট ইতিহাস এখনো চালু হয়নি। কিছুক্ষণ পরে আবার দেখুন।")).toBeInTheDocument();
  });

  it("shows a paid order with its status in words", async () => {
    mockApi({
      "GET /api/v1/me/live-exam-orders": {
        content: [
          {
            orderId: "o1",
            tranId: "TRAN-1",
            status: "PAID",
            amount: 400,
            currency: "BDT",
            scope: "EXAM",
            moduleId: null,
            moduleTitleEn: null,
            moduleTitleBn: null,
            examId: "e1",
            examTitleEn: "CRPC Mock",
            examTitleBn: "সিআরপিসি মডেল টেস্ট",
            paymentChannel: "bKash",
            createdAt: "2026-11-01T06:00:00.000Z",
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    });
    renderWithProviders(<PaymentsPage />, { user: fixtures.student, route: "/account/payments" });
    expect((await screen.findAllByText("সিআরপিসি মডেল টেস্ট"))[0]).toBeInTheDocument();
    expect(screen.getAllByText("পরিশোধিত")[0]).toBeInTheDocument();
    // The transaction id is in the order's detail panel (Phase 4, S4.6), not in the table.
    expect(screen.queryByText("TRAN-1")).not.toBeInTheDocument();
  });
});

describe("notices", () => {
  const notice = {
    id: "n1",
    kind: "MCQ_RESULT",
    createdAt: "2026-11-01T06:00:00.000Z",
    readAt: null,
    exam: { id: "e1", moduleId: "m1", examType: "MCQ", titleEn: "CRPC Mock", titleBn: "সিআরপিসি মডেল টেস্ট", liveAt: null, endAt: null },
    score: 38,
    maximum: 50,
    passed: null,
  };

  it("marks one notice read when it is opened", async () => {
    const calls = mockApi({
      "GET /api/v1/me/notices": { ...emptyPage, content: [notice], totalElements: 1, totalPages: 1 },
      "POST /api/v1/me/notices/n1/read": { ...notice, readAt: "2026-11-01T07:00:00.000Z" },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, route: "/notices" });
    await userEvent.click(await screen.findByRole("link", { name: /সিআরপিসি মডেল টেস্ট/ }));
    await waitFor(() => expect(calls.calls.some((c) => c.path === "/api/v1/me/notices/n1/read")).toBe(true));
  });

  it("marks all notices read from the top button", async () => {
    const calls = mockApi({
      "GET /api/v1/me/notices": { ...emptyPage, content: [notice], totalElements: 1, totalPages: 1 },
      "POST /api/v1/me/notices/read-all": { updated: 1 },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, route: "/notices" });
    await userEvent.click(await screen.findByRole("button", { name: "এই ট্যাবের সব পড়া হয়েছে" }));
    await waitFor(() => expect(calls.calls.some((c) => c.path === "/api/v1/me/notices/read-all")).toBe(true));
  });

  it("shows the empty sentence", async () => {
    mockApi({ "GET /api/v1/me/notices": emptyPage });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, route: "/notices" });
    // With no unread notices the page opens on the Exams tab, and that tab's empty sentence shows (Phase 4, S4.5).
    expect(await screen.findByText("পরীক্ষার কোনো নোটিশ এখনো নেই।")).toBeInTheDocument();
  });

  it("shows the unread count on the bell", async () => {
    mockApi({ "GET /api/v1/me/notices/unread-count": { count: 3 } });
    renderWithProviders(<NoticeBell />, { user: fixtures.student, route: "/" });
    expect(await screen.findByRole("link", { name: "নোটিশ, ৩টি নতুন নোটিশ" })).toHaveAttribute("href", "/notices");
  });
});
