import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import PaymentsPage from "@/app/(staff)/staff/payments/page";
import { ExamCoverUploader } from "@/components/staff/catalog/exam-cover-uploader";
import { PaymentsTable } from "@/components/staff/ops/payments-table";
import type { StaffOrderRow } from "@/lib/api/exam/staff-ops";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

const exam = { id: "e1", moduleId: "m1", titleEn: "CRPC paper", titleBn: "সিআরপিসি", coverUrl: null };

const png = new File([new Uint8Array([137, 80, 78, 71])], "cover.png", { type: "image/png" });

describe("exam cover uploader (Phase 4, S4.7)", () => {
  it("offers an upload and says the change is live at once", () => {
    renderWithProviders(<ExamCoverUploader exam={exam as never} />, { locale: "en" });

    expect(screen.getByRole("heading", { name: "Cover image" })).toBeInTheDocument();
    expect(screen.getByText("The cover goes live at once, as a package cover does.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upload cover" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove cover" })).not.toBeInTheDocument();
  });

  it("uploads the picked image to the exam's cover route", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "POST /api/v1/live-exams/e1/cover": { ...exam, coverUrl: "/api/v1/public/live-exams/e1/cover?v=1" } });
    renderWithProviders(<ExamCoverUploader exam={exam as never} />, { locale: "en" });

    await user.upload(screen.getByLabelText("Upload cover"), png);

    const posted = api.calls.find((call) => call.method === "POST" && call.path === "/api/v1/live-exams/e1/cover");
    expect(posted).toBeDefined();
  });

  it("shows Replace and Remove once an exam has a cover", () => {
    renderWithProviders(<ExamCoverUploader exam={{ ...exam, coverUrl: "/api/v1/public/live-exams/e1/cover?v=1" } as never} />, {
      locale: "en",
    });

    expect(screen.getByRole("button", { name: "Replace cover" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove cover" })).toBeInTheDocument();
  });
});

const orderRow = (overrides: Partial<StaffOrderRow> = {}): StaffOrderRow => ({
  orderId: "o1",
  tranId: "TRAN-9",
  status: "SUCCEEDED",
  amount: 1500,
  currency: "BDT",
  scope: "MODULE",
  moduleId: "m1",
  moduleTitleEn: "BJS",
  moduleTitleBn: "বিজেএস",
  examId: null,
  examTitleEn: null,
  examTitleBn: null,
  paymentChannel: "bKash",
  valId: "VAL-1",
  accessGranted: true,
  studentId: "s1",
  studentName: "Rahim",
  studentPhone: "+8801700000000",
  studentEmail: "rahim@example.com",
  studentCode: "S-1",
  createdAt: "2026-10-28T04:00:00Z",
  paidAt: "2026-10-28T04:05:00Z",
  receiptNo: "RCPT-2026-000123",
  updatedAt: "2026-10-28T04:05:00Z",
  ...overrides,
});

describe("staff order list columns (Phase 4, S4.7)", () => {
  it("has six columns, and the Date cell carries the paid time and the receipt number of a paid order", () => {
    renderWithProviders(<PaymentsTable rows={[orderRow()]} />, { locale: "en" });

    const table = screen.getByRole("table");
    const headers = within(table).getAllByRole("columnheader");
    expect(headers).toHaveLength(6);
    expect(within(table).getByRole("columnheader", { name: "Date" })).toBeInTheDocument();
    expect(within(table).queryByRole("columnheader", { name: "Paid on" })).not.toBeInTheDocument();
    expect(within(table).queryByRole("columnheader", { name: "Receipt number" })).not.toBeInTheDocument();
    expect(within(table).queryByRole("columnheader", { name: "Phone" })).not.toBeInTheDocument();
    expect(within(table).queryByRole("columnheader", { name: "Method" })).not.toBeInTheDocument();
    expect(within(table).getByText("RCPT-2026-000123")).toBeInTheDocument();
  });

  it("shows the Paid line only for a paid order", () => {
    const pending = orderRow({
      orderId: "o2",
      tranId: "TRAN-PENDING-2",
      status: "INITIATED",
      paidAt: null,
      receiptNo: null,
    });
    renderWithProviders(<PaymentsTable rows={[orderRow(), pending]} />, { locale: "en" });

    const table = screen.getByRole("table");
    expect(within(table).getAllByText(/^Paid /)).toHaveLength(1);
    expect(within(table).getByText(/^Paid /)).toHaveTextContent(/^Paid \S/);
    expect(within(table).getAllByText("RCPT-2026-000123")).toHaveLength(1);
  });
});

describe("staff payments status tabs", () => {
  it("sets the status URL param when a status tab is pressed", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/live-exam-orders": { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<PaymentsPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/payments" });

    const all = screen.getByRole("button", { name: "All" });
    expect(all).toHaveAttribute("aria-pressed", "true");

    await user.click(screen.getByRole("button", { name: "Paid" }));

    expect(routerState.replace).toHaveBeenLastCalledWith("/staff/payments?status=SUCCEEDED", { scroll: false });
  });
});
