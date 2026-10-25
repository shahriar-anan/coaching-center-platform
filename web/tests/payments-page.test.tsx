import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import PaymentsPage from "@/app/(admin)/payments/page";
import { LocaleProvider } from "@/i18n/locale-context";
import { setAccessToken } from "@/lib/auth/token-store";
import type { PaymentView } from "@/lib/api/types";

vi.mock("@/lib/auth/session-context", () => ({
  useUser: () => ({
    id: "user-1",
    phone: "+8801700000000",
    email: "admin@example.com",
    emailVerifiedAt: null,
    role: "MASTER_ADMIN",
    status: "ACTIVE",
    fullName: "Admin",
    studentCode: null,
  }),
}));

const urls: string[] = [];

function payment(overrides: Partial<PaymentView> = {}): PaymentView {
  return {
    id: "payment-1",
    studentId: "student-1",
    payableType: "COURSE_ENROLLMENT",
    payableId: "enrollment-1",
    amount: 100,
    method: "BKASH",
    transactionRef: "trx-1",
    status: "PENDING",
    studentName: "Buyer",
    studentPhone: "01700000002",
    titleEn: "Course One",
    titleBn: "কোর্স",
    createdAt: "2026-10-04T10:00:00Z",
    ...overrides,
  };
}

function page(row: PaymentView) {
  return {
    content: [row],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  };
}

beforeEach(() => {
  urls.length = 0;
  setAccessToken("token");
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo, init?: RequestInit) => {
      const url = String(input);
      urls.push(`${init?.method ?? "GET"} ${url}`);
      const pendingCourse = url.includes("status=PENDING") && url.includes("payableType=COURSE_ENROLLMENT");
      const body = pendingCourse ? page(payment()) : page(payment({ status: "SUCCESS", transactionRef: "trx-paid" }));
      if ((init?.method ?? "GET") === "POST") {
        return new Response(JSON.stringify({ paymentId: "payment-1", paymentStatus: "SUCCESS" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }),
  );
});

it("filters course and book payments and confirms from the detail dialog", async () => {
  const user = userEvent.setup();
  render(
    <LocaleProvider initialLocale="en">
      <PaymentsPage />
    </LocaleProvider>,
  );

  expect(await screen.findByRole("button", { name: /trx-1/ })).toBeTruthy();
  expect(urls.some((url) => url.includes("status=PENDING") && url.includes("payableType=COURSE_ENROLLMENT"))).toBe(
    true,
  );

  await user.click(screen.getByRole("tab", { name: "Book payments" }));
  await waitFor(() => {
    expect(urls.some((url) => url.includes("payableType=BOOK_ORDER"))).toBe(true);
  });

  await user.click(screen.getByRole("button", { name: "Paid" }));
  await waitFor(() => {
    expect(urls.some((url) => url.includes("status=SUCCESS") && url.includes("payableType=BOOK_ORDER"))).toBe(true);
  });

  await user.click(screen.getByRole("tab", { name: "Course payments" }));
  await user.click(screen.getByRole("button", { name: "Pending" }));
  const card = await screen.findByRole("button", { name: /trx-1/ });
  await user.click(card);

  const dialog = await screen.findByRole("dialog");
  expect(dialog.textContent).toContain("Buyer");
  expect(dialog.textContent).toContain("trx-1");
  await user.click(screen.getByRole("button", { name: "Mark successful" }));
  await waitFor(() => {
    expect(urls.some((url) => url.startsWith("POST") && url.includes("/payments/payment-1/verify"))).toBe(true);
  });
});
