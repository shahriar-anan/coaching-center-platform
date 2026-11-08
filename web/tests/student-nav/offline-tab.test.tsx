import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { MyExamsPage } from "@/components/student/catalog/my-exams-page";
import { examPath } from "@/components/student/catalog/catalog-helpers";
import { fixtures, mockApi, renderWithProviders, resetRouter, routerState } from "../helpers";

const offline = {
  exams: [
    {
      examId: "e1",
      titleEn: "Revoked exam",
      titleBn: "Revoked exam",
      examType: "WRITTEN",
      grantedAt: "2026-10-01T00:00:00Z",
      expired: false,
      accessRemoved: true,
      payment: { planId: "p1", agreedTotal: 6300, paid: 2100, due: 4200, nextDueOn: "2026-12-08", status: "ACCESS_REVOKED" },
    },
    {
      examId: "e2",
      titleEn: "Live exam",
      titleBn: "Live exam",
      examType: "MCQ",
      grantedAt: "2026-11-01T00:00:00Z",
      expired: false,
      payment: { planId: "p2", agreedTotal: 1000, paid: 1000, due: 0, status: "COMPLETED" },
    },
    {
      examId: "e3",
      titleEn: "No charge exam",
      titleBn: "No charge exam",
      examType: "MCQ",
      grantedAt: "2026-11-02T00:00:00Z",
      expired: false,
    },
  ],
  packages: [],
};

const planDetail = {
  plan: { planId: "p1", agreedTotal: 6300, paid: 2100, due: 4200, nextDueOn: "2026-12-08", status: "ACCESS_REVOKED" },
  schedule: [
    { seq: 1, amount: 2100, dueOn: "2026-11-08", paid: 2100, uncovered: 0, state: "PAID" },
    { seq: 2, amount: 2100, dueOn: "2026-12-08", paid: 0, uncovered: 2100, state: "DUE" },
  ],
  payments: [
    {
      id: "pay1",
      planId: "p1",
      amount: 2100,
      method: "BKASH",
      paidOn: "2026-11-08",
      receiptNo: "RCPT-2026-000140",
      installmentNo: 1,
      installmentCount: 2,
    },
  ],
};

beforeEach(() => {
  resetRouter("/my-modules");
});

function renderOffline(route: string) {
  resetRouter(route);
  mockApi({
    "GET /api/v1/me/live-exam-access": { exams: [], modules: [] },
    "GET /api/v1/me/offline-purchases": offline,
    "GET /api/v1/me/installment-plans/p1": planDetail,
  });
  // renderWithProviders resets the router to "/" unless it is given the route, so the query string must be passed in.
  return renderWithProviders(<MyExamsPage />, { user: fixtures.student, locale: "en", route });
}

describe("My exams opens on the tab named in the URL (plan section 3)", () => {
  it("shows the Offline purchases tab for ?tab=offline", async () => {
    renderOffline("/my-modules?tab=offline");

    expect(await screen.findByText("Revoked exam")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /Offline purchases/ })).toHaveAttribute("aria-selected", "true");
  });

  it("writes the tab to the URL when the student chooses it", async () => {
    renderOffline("/my-modules");

    await userEvent.click(await screen.findByRole("tab", { name: /Offline purchases/ }));
    expect(routerState.replace).toHaveBeenCalledWith("/my-modules?tab=offline", expect.anything());
  });
});

describe("offline cards with a payment plan (plan section 8)", () => {
  it("does not link a card whose access was removed, and still shows its money", async () => {
    renderOffline("/my-modules?tab=offline");

    const title = await screen.findByText("Revoked exam");
    expect(title.closest("a")).toBeNull();
    const card = title.closest("li");
    if (!card) throw new Error("no card");
    expect(within(card).getByText("Paid ৳2,100.00")).toBeInTheDocument();
    expect(within(card).getByText("Due ৳4,200.00")).toBeInTheDocument();
    expect(within(card).getByText("Access ended")).toBeInTheDocument();
  });

  it("links a live card to its exam", async () => {
    renderOffline("/my-modules?tab=offline");

    const link = await screen.findByRole("link", { name: "Live exam" });
    expect(link).toHaveAttribute("href", examPath("e2"));
  });

  it("shows a View payments button only on cards with a plan", async () => {
    renderOffline("/my-modules?tab=offline");

    await screen.findByText("Revoked exam");
    expect(screen.getAllByRole("button", { name: "View payments" })).toHaveLength(2);
  });

  it("opens the schedule and the payments, with a receipt to download", async () => {
    renderOffline("/my-modules?tab=offline");

    await userEvent.click((await screen.findAllByRole("button", { name: "View payments" }))[0]);
    expect(await screen.findByText("Installment 2")).toBeInTheDocument();
    expect(screen.getByText("Receipt RCPT-2026-000140")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download receipt" })).toBeInTheDocument();
  });
});
