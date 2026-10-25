import { Suspense } from "react";
import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import PaymentsPage from "@/app/(staff)/staff/payments/page";
import PaymentDetailPage from "@/app/(staff)/staff/payments/[tranId]/page";
import { PaymentDetail } from "@/components/staff/ops/payment-detail";
import type { StaffOrderRow } from "@/lib/api/exam/staff-ops";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

const pendingModule: StaffOrderRow = {
  orderId: "aaaaaaaa-0000-4000-8000-000000000001",
  tranId: "TRAN-PENDING-1",
  status: "INITIATED",
  amount: 2500,
  currency: "BDT",
  scope: "MODULE",
  moduleId: "bbbbbbbb-0000-4000-8000-000000000001",
  moduleTitleEn: "19th BJS",
  moduleTitleBn: "১৯তম বিজেএস",
  examId: null,
  examTitleEn: null,
  examTitleBn: null,
  paymentChannel: null,
  valId: null,
  accessGranted: false,
  studentId: "cccccccc-0000-4000-8000-000000000001",
  studentName: "Rahim Uddin",
  studentPhone: "+8801711000001",
  studentEmail: "rahim@example.com",
  studentCode: "STU-0001",
  createdAt: "2026-10-25T04:00:00Z",
  updatedAt: "2026-10-25T04:00:00Z",
};

const paidExam: StaffOrderRow = {
  ...pendingModule,
  orderId: "aaaaaaaa-0000-4000-8000-000000000002",
  tranId: "TRAN-PAID-2",
  status: "SUCCEEDED",
  amount: 400,
  scope: "EXAM",
  examId: "dddddddd-0000-4000-8000-000000000001",
  examTitleEn: "CrPC",
  examTitleBn: "সিআরপিসি",
  paymentChannel: "BKASH-BKash",
  valId: "VAL-123",
  accessGranted: true,
};

describe("staff payments list", () => {
  it("shows rows with the status word and the access line, and sends the URL filters", async () => {
    const api = mockApi({
      "GET /api/v1/live-exam-orders": {
        content: [pendingModule, paidExam],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      },
    });
    renderWithProviders(<PaymentsPage />, {
      user: fixtures.systemAdmin,
      route: "/staff/payments?status=SUCCEEDED&q=rahim",
    });

    expect((await screen.findAllByText("Rahim Uddin")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("অপেক্ষমাণ").length).toBeGreaterThan(0);
    expect(screen.getAllByText("পরিশোধিত").length).toBeGreaterThan(0);
    expect(screen.getAllByText("নিশ্চিত হলে অ্যাক্সেস খুলবে").length).toBeGreaterThan(0);
    expect(screen.getAllByText("শুধু এই পরীক্ষা").length).toBeGreaterThan(0);
    expect(screen.getAllByText("BKASH-BKash").length).toBeGreaterThan(0);

    const sent = api.calls.find((call) => call.path === "/api/v1/live-exam-orders");
    expect(sent?.search).toContain("status=SUCCEEDED");
    expect(sent?.search).toContain("q=rahim");
  });

  it("is open to System Admin, and shows no action that changes an order", async () => {
    mockApi({
      "GET /api/v1/live-exam-orders": { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(<PaymentsPage />, { user: fixtures.systemAdmin, route: "/staff/payments" });
    expect(await screen.findByText("এই খোঁজে কোনো পেমেন্ট নেই")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /paid|পরিশোধিত করুন|যাচাই/ })).toBeNull();
  });

  it("refuses an instructor with the not-found page", async () => {
    mockApi({});
    renderWithProviders(<PaymentsPage />, { user: fixtures.instructor, route: "/staff/payments" });
    expect(await screen.findByText("পাতাটি পাওয়া যায়নি")).toBeInTheDocument();
  });
});

describe("payment detail", () => {
  it("opens the direct-enrolment link for Master Admin only", () => {
    const { unmount } = renderWithProviders(<PaymentDetail row={pendingModule} canOpenRoster />);
    expect(screen.getByRole("link", { name: "পেমেন্ট ছাড়া দিতে সরাসরি ভর্তি খুলুন" })).toHaveAttribute(
      "href",
      "/staff/modules/bbbbbbbb-0000-4000-8000-000000000001?tab=roster",
    );
    unmount();

    renderWithProviders(<PaymentDetail row={pendingModule} canOpenRoster={false} />);
    expect(screen.queryByRole("link", { name: "পেমেন্ট ছাড়া দিতে সরাসরি ভর্তি খুলুন" })).toBeNull();
  });

  it("names the grant on a paid exam and says so when there is none", () => {
    renderWithProviders(<PaymentDetail row={paidExam} canOpenRoster={false} />);
    expect(screen.getByText(/অ্যাক্সেস খোলা আছে: সিআরপিসি/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "শিক্ষার্থীর পাতা খুলুন" })).toHaveAttribute(
      "href",
      "/staff/students/cccccccc-0000-4000-8000-000000000001",
    );
  });

  it("shows the not-found message for an unknown transaction id", async () => {
    mockApi({
      "GET /api/v1/live-exam-orders": { content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 },
    });
    renderWithProviders(
      <Suspense fallback={null}>
        <PaymentDetailPage params={resolved({ tranId: "NOPE" })} />
      </Suspense>,
      { user: fixtures.masterAdmin, route: "/staff/payments/NOPE" },
    );
    await waitFor(() =>
      expect(screen.getByText("এই ট্রানজেকশন আইডি দিয়ে কোনো পেমেন্ট পাওয়া যায়নি।")).toBeInTheDocument(),
    );
  });
});
