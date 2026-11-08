import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { DirectEnrolment } from "@/components/staff/enrol/direct-enrolment";
import { addMonths, todayInDhaka } from "@/components/staff/enrol/payment-model";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";
import { categoryTree, examRow, packageRow, pageOf } from "./picker-data";
import "./copy-setup";

const rafi = { id: "s1", fullName: "Rafi Ahmed", phone: "01711000001", email: null, studentCode: "STU-001" };
const EMPTY_PAGE = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
const QUOTE = { listPrice: 6000, discountPercent: null, standardPrice: 6000, free: false, moduleOnly: false };
const GRANTED = { id: "g1", paymentPlanId: "plan-1", receiptNo: "RCPT-2026-000140" };

/** The routes the Direct enrolment screen reads, plus the quote and the grant. */
function setup(routes: Record<string, unknown> = {}) {
  return mockApi({
    "GET /api/v1/live-exam-categories": categoryTree,
    "GET /api/v1/live-exam-modules": (req: { url: URL }) => pageOf(req.url, [packageRow(1)]),
    "GET /api/v1/live-exam-modules/p1": packageRow(1, { titleEn: "BCS Preliminary", titleBn: "বিসিএস প্রিলি" }),
    "GET /api/v1/live-exam-modules/p1/enrolments": EMPTY_PAGE,
    "GET /api/v1/live-exams": (req: { url: URL }) => pageOf(req.url, [examRow(1), examRow(5)]),
    "GET /api/v1/live-exams/e5": examRow(5),
    "GET /api/v1/live-exams/e5/enrolments": EMPTY_PAGE,
    "GET /api/v1/students": { content: [rafi], page: 0, size: 10, totalElements: 1, totalPages: 1 },
    "GET /api/v1/live-exam-grants/quote": QUOTE,
    "POST /api/v1/live-exam-grants": GRANTED,
    ...routes,
  });
}

/** Finds Rafi through the student search and chooses him. */
async function chooseRafi() {
  await userEvent.type(screen.getByPlaceholderText("Student code, phone, email or name"), "Ra");
  await userEvent.click(await screen.findByRole("button", { name: "Choose" }));
}

/** Asks for the confirmation, then confirms. */
async function reviewAndConfirm() {
  await userEvent.click(screen.getByRole("button", { name: "Review and enrol" }));
  await userEvent.click(await screen.findByRole("button", { name: "Confirm and enrol" }));
}

function grantPosts(calls: { method: string; path: string; body: unknown }[]) {
  return calls.filter((call) => call.method === "POST" && call.path === "/api/v1/live-exam-grants").map((call) => call.body);
}

describe("Direct enrolment: the target", () => {
  it("starts with the exam from the URL chosen, and shows its title", async () => {
    const api = setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("Mock exam 5")).toBeInTheDocument();
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "GET" && call.path === "/api/v1/live-exams/e5")).toBe(true);
  });

  it("starts with the package from the URL chosen, and shows its title", async () => {
    setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} initialPackageId="p1" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("BCS Preliminary")).toBeInTheDocument();
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
  });

  it("shows the chosen line before the title loads, without showing the id", async () => {
    setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(screen.getByText("Chosen")).toBeInTheDocument();
    expect(screen.queryByText("e5")).not.toBeInTheDocument();
    await screen.findByText("Mock exam 5");
  });

  it("lists the packages by default, and the exams after the switch", async () => {
    setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByRole("radio", { name: /Package 1/ })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Exam" }));

    expect(await screen.findByRole("radio", { name: /^Mock exam 1(?!\d)/ })).toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /Package 1/ })).not.toBeInTheDocument();
  });

  it("asks for a package or an exam before a student can be searched for", async () => {
    setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("Choose a package or an exam first, then find the student to enrol.")).toBeInTheDocument();
    expect(screen.queryByPlaceholderText("Student code, phone, email or name")).not.toBeInTheDocument();
  });
});

describe("Direct enrolment: one student and the payment", () => {
  it("shows the payment fields at once for the student named in the URL", async () => {
    setup();
    renderWithProviders(<DirectEnrolment initialStudent={rafi} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByRole("textbox", { name: /Agreed total/ })).toHaveValue("6000");
    expect(screen.getByRole("button", { name: "Full payment" })).toBeInTheDocument();
    expect(screen.getByText("Rafi Ahmed")).toBeInTheDocument();
  });

  it("takes a full payment: the whole total, a generated cash reference, and one confirmation", async () => {
    const api = setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await chooseRafi();
    const reference = (await screen.findByRole("textbox", { name: /Transaction reference/ })) as HTMLInputElement;
    expect(reference.value).toMatch(/^CASH-E5-\d{8}-1$/);
    await reviewAndConfirm();

    await waitFor(() => expect(grantPosts(api.calls)).toHaveLength(1));
    const today = todayInDhaka();
    expect(grantPosts(api.calls)[0]).toEqual({
      scope: "EXAM",
      examId: "e5",
      studentId: "s1",
      reason: "",
      payment: {
        agreedTotal: 6000,
        amount: 6000,
        method: "CASH",
        reference: expect.stringMatching(/^CASH-E5-\d{8}-1$/),
        paidOn: today,
        note: null,
        schedule: [{ amount: 6000, dueOn: today }],
      },
    });
  });

  it("takes installments: the schedule the form shows is the one sent", async () => {
    const api = setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await chooseRafi();
    await userEvent.click(await screen.findByRole("button", { name: "Installments" }));
    const total = screen.getByRole("textbox", { name: /Agreed total/ });
    await userEvent.clear(total);
    await userEvent.type(total, "6300");
    await userEvent.type(screen.getByRole("textbox", { name: /Paid today/ }), "2100");
    expect(screen.getByRole("textbox", { name: /Amount per installment/ })).toHaveValue("2100");

    await reviewAndConfirm();

    await waitFor(() => expect(grantPosts(api.calls)).toHaveLength(1));
    const today = todayInDhaka();
    const next = addMonths(today, 1);
    const payment = (grantPosts(api.calls)[0] as { payment: Record<string, unknown> }).payment;
    expect(payment).toMatchObject({ agreedTotal: 6300, amount: 2100, paidOn: today });
    expect(payment.schedule).toEqual([
      { amount: 2100, dueOn: today },
      { amount: 2100, dueOn: next },
      { amount: 2100, dueOn: addMonths(next, 1) },
    ]);
  });

  it("offers one student at a time: no bulk list, no add-to-list, no enrol-N button", async () => {
    setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await chooseRafi();
    await screen.findByRole("button", { name: "Review and enrol" });

    expect(screen.queryByRole("button", { name: "Add to list" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Enrol \d+ student/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/in the list/)).not.toBeInTheDocument();
  });

  it("takes No charge with a reason, and sends no payment", async () => {
    const api = setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await chooseRafi();
    await userEvent.click(await screen.findByRole("button", { name: "No charge" }));
    await userEvent.type(screen.getByRole("textbox", { name: /Reason/ }), "Scholarship");
    await reviewAndConfirm();

    await waitFor(() => expect(grantPosts(api.calls)).toHaveLength(1));
    expect(grantPosts(api.calls)[0]).toEqual({ scope: "EXAM", examId: "e5", studentId: "s1", reason: "Scholarship" });
  });

  it("refuses No charge without a reason, and posts nothing", async () => {
    const api = setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await chooseRafi();
    await userEvent.click(await screen.findByRole("button", { name: "No charge" }));
    await userEvent.click(screen.getByRole("button", { name: "Review and enrol" }));

    expect(await screen.findByText("Write a reason for the enrolment.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Confirm and enrol" })).not.toBeInTheDocument();
    expect(grantPosts(api.calls)).toHaveLength(0);
  });

  it("keeps the form with the duplicate-grant message when the student already has access", async () => {
    setup({ "POST /api/v1/live-exam-grants": apiError(409, "GRANT_ALREADY_ACTIVE") });
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await chooseRafi();
    await reviewAndConfirm();

    expect(await screen.findByText(/already has access to this package or exam/)).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /Agreed total/ })).toBeInTheDocument();
  });

  it("shows the receipt and plan links after a save, and clears the student but keeps the exam", async () => {
    setup();
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await chooseRafi();
    await reviewAndConfirm();

    expect(await screen.findByText("Receipt RCPT-2026-000140")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open the receipt" })).toHaveAttribute("href", expect.stringContaining("/staff/installments/plan-1"));
    expect(screen.getByRole("link", { name: "Open the installment plan" })).toHaveAttribute(
      "href",
      expect.stringContaining("/staff/installments/plan-1"),
    );
    expect(screen.getByPlaceholderText("Student code, phone, email or name")).toBeInTheDocument();
    expect(screen.getAllByText("Mock exam 5").length).toBeGreaterThan(0);
  });

  it("warns about an unfinished plan and about access held another way, and still allows the save", async () => {
    setup({
      "GET /api/v1/live-exam-grants/quote": { ...QUOTE, unfinishedPlanId: "plan-9", accessHeld: true },
    });
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await chooseRafi();

    expect(await screen.findByRole("link", { name: "Open the unfinished plan" })).toHaveAttribute(
      "href",
      expect.stringContaining("/staff/installments/plan-9"),
    );
    expect(screen.getByText(/another route/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Review and enrol" })).toBeEnabled();
  });

  it("offers only No charge for a free exam", async () => {
    setup({ "GET /api/v1/live-exam-grants/quote": { listPrice: null, discountPercent: null, standardPrice: null, free: true, moduleOnly: false } });
    renderWithProviders(<DirectEnrolment initialStudent={null} initialExamId="e5" />, { user: fixtures.masterAdmin, locale: "en" });
    await screen.findByText("Mock exam 5");

    await chooseRafi();

    expect(await screen.findByText("This exam is free, so no payment is taken. The only choice is No charge.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Full payment" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Installments" })).not.toBeInTheDocument();
  });
});
