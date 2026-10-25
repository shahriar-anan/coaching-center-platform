import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ReceiptView } from "@/app/(site)/account/payments/_components/receipt-view";
import { renderWithProviders } from "../helpers";

const order = {
  orderId: "o1",
  tranId: "TRX123",
  status: "PAID" as const,
  amount: 400,
  currency: "BDT",
  scope: "EXAM" as const,
  moduleId: "m1",
  moduleTitleEn: "BJS",
  moduleTitleBn: "বিজেএস",
  examId: "e1",
  examTitleEn: "BJS Mock 1",
  examTitleBn: "বিজেএস মক ১",
  paymentChannel: "bKash",
  createdAt: "2026-11-01T04:30:00Z",
};

describe("payment receipt", () => {
  it("shows the site, the item, the amount and the transaction number", () => {
    renderWithProviders(<ReceiptView order={order} studentName="নাঈম হাসান" />);
    expect(screen.getByText("এক্সাম পোর্টাল")).toBeInTheDocument();
    expect(screen.getByText("নাঈম হাসান")).toBeInTheDocument();
    expect(screen.getByText("বিজেএস মক ১")).toBeInTheDocument();
    expect(screen.getByText("৳৪০০")).toBeInTheDocument();
    expect(screen.getByText("TRX123")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "রসিদ ছাপুন" })).toBeInTheDocument();
  });
});
