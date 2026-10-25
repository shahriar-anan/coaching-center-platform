import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PendingOrders } from "@/components/staff/orders/pending-orders";
import { fixtures, renderWithProviders } from "../helpers";
import { apiError, mockApi } from "../helpers/mock-api";
import "./copy-setup";

const orderId = "aaaaaaaa-0000-4000-8000-000000000001";

const pending = {
  content: [
    {
      orderId,
      tranId: "TRAN-PENDING-1",
      status: "INITIATED",
      amount: 2500,
      scope: "MODULE",
      moduleTitleEn: "19th BJS",
      moduleTitleBn: "১৯তম বিজেএস",
      examTitleEn: null,
      examTitleBn: null,
      studentId: "cccccccc-0000-4000-8000-000000000001",
      studentName: "নাঈম হাসান",
      studentPhone: "01711000001",
      studentCode: "S18492017",
      createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe("PendingOrders", () => {
  it("shows Confirm and Reject to Master Admin", async () => {
    mockApi({ "GET /api/v1/live-exam-orders": pending });
    renderWithProviders(<PendingOrders />, { user: fixtures.masterAdmin });

    expect((await screen.findAllByText("TRAN-PENDING-1"))[0]).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "পেমেন্ট নিশ্চিত" })).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "ব্যর্থ বলে চিহ্নিত" })).toHaveLength(2);
  });

  it("lists orders to System Admin, with no decision buttons and a sentence on who can decide", async () => {
    mockApi({ "GET /api/v1/live-exam-orders": pending });
    renderWithProviders(<PendingOrders />, { user: fixtures.systemAdmin });

    expect((await screen.findAllByText("TRAN-PENDING-1"))[0]).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "পেমেন্ট নিশ্চিত" })).not.toBeInTheDocument();
    expect(screen.getByText("পেমেন্ট নিশ্চিত বা ব্যর্থ বলে চিহ্নিত করতে পারেন শুধু মাস্টার অ্যাডমিন।")).toBeInTheDocument();
  });

  it("asks for a reason, then confirms the order", async () => {
    const { calls } = mockApi({
      "GET /api/v1/live-exam-orders": pending,
      [`POST /api/v1/live-exam-orders/${orderId}/confirm`]: { orderId, status: "SUCCEEDED" },
    });
    renderWithProviders(<PendingOrders />, { user: fixtures.masterAdmin });
    const user = userEvent.setup();

    await screen.findAllByText("TRAN-PENDING-1");
    await user.click(screen.getAllByRole("button", { name: "পেমেন্ট নিশ্চিত" })[0]);
    await user.click(screen.getAllByRole("button", { name: "পেমেন্ট নিশ্চিত" }).at(-1)!);
    expect(await screen.findByText("কারণ লিখতে হবে।")).toBeInTheDocument();
    expect(calls.some((call) => call.method === "POST")).toBe(false);

    await user.type(screen.getByLabelText(/^কারণ/), "SSLCommerz-এ মিলেছে");
    await user.click(screen.getAllByRole("button", { name: "পেমেন্ট নিশ্চিত" }).at(-1)!);

    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")?.body).toEqual({ reason: "SSLCommerz-এ মিলেছে" }),
    );
  });

  it("explains an order that is no longer pending", async () => {
    mockApi({
      "GET /api/v1/live-exam-orders": pending,
      [`POST /api/v1/live-exam-orders/${orderId}/reject`]: apiError(409, "ORDER_NOT_PENDING", "Not pending."),
    });
    renderWithProviders(<PendingOrders />, { user: fixtures.masterAdmin });
    const user = userEvent.setup();

    await screen.findAllByText("TRAN-PENDING-1");
    await user.click(screen.getAllByRole("button", { name: "ব্যর্থ বলে চিহ্নিত" })[0]);
    await user.type(screen.getByLabelText(/^কারণ/), "পেমেন্ট হয়নি");
    await user.click(screen.getAllByRole("button", { name: "ব্যর্থ বলে চিহ্নিত" }).at(-1)!);

    expect(
      await screen.findByText("এই পেমেন্টের অবস্থা ইতিমধ্যে বদলে গেছে। তালিকা আবার লোড হচ্ছে।"),
    ).toBeInTheDocument();
  });

  it("shows the empty state when nothing is waiting", async () => {
    mockApi({ "GET /api/v1/live-exam-orders": { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } });
    renderWithProviders(<PendingOrders />, { user: fixtures.masterAdmin });

    expect(await screen.findByText("এখন কোনো অপেক্ষমাণ পেমেন্ট নেই।")).toBeInTheDocument();
  });
});
