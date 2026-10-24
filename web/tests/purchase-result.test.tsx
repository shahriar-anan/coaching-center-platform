import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { PurchaseResult } from "@/app/purchase/result/purchase-result";

const state = vi.hoisted(() => ({
  query: "tranId=tran-1",
  status: "authenticated" as "authenticated" | "anonymous" | "loading",
}));

const apiRequest = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api/client", () => ({
  apiRequest: (...args: unknown[]) => apiRequest(...args),
}));

vi.mock("@/lib/auth/session-context", () => ({
  useSession: () => ({
    session:
      state.status === "authenticated"
        ? { status: "authenticated", user: { role: "STUDENT", fullName: "Student" } }
        : { status: state.status },
    login: vi.fn(),
  }),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(state.query),
}));

beforeEach(() => {
  state.query = "tranId=tran-1";
  state.status = "authenticated";
  apiRequest.mockReset();
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

it("polls while pending and then shows the paid status", async () => {
  apiRequest
    .mockResolvedValueOnce({ status: "PENDING", examName: "Practice exam" })
    .mockResolvedValueOnce({ status: "PAID", examName: "Practice exam" });

  render(<PurchaseResult />);
  await act(async () => {
    await Promise.resolve();
  });

  expect(screen.getByRole("heading", { name: "Pending" })).toBeInTheDocument();
  expect(apiRequest).toHaveBeenCalledWith("/api/v1/live-exam-orders/status?tranId=tran-1");

  await act(async () => {
    await vi.advanceTimersByTimeAsync(3000);
  });

  expect(screen.getByRole("heading", { name: "Paid" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Practice exam" })).toHaveAttribute(
    "href",
    "/live-exams?name=Practice%20exam",
  );
  expect(apiRequest).toHaveBeenCalledTimes(2);

  await act(async () => {
    await vi.advanceTimersByTimeAsync(3000);
  });
  expect(apiRequest).toHaveBeenCalledTimes(2);
});

it("shows failed and stops polling", async () => {
  apiRequest.mockResolvedValue({ status: "FAILED", examName: "Practice exam" });
  render(<PurchaseResult />);
  await act(async () => {
    await Promise.resolve();
  });
  expect(screen.getByRole("heading", { name: "Failed" })).toBeInTheDocument();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(3000);
  });
  expect(apiRequest).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole("heading", { name: "Paid" })).not.toBeInTheDocument();
});

it("stays pending when the wait ends", async () => {
  apiRequest.mockResolvedValue({ status: "PENDING", examName: "Practice exam" });
  render(<PurchaseResult />);
  await act(async () => {
    await Promise.resolve();
  });
  await act(async () => {
    await vi.advanceTimersByTimeAsync(60_000);
  });
  expect(screen.getByRole("heading", { name: "Pending" })).toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Failed" })).not.toBeInTheDocument();
  expect(screen.queryByRole("heading", { name: "Paid" })).not.toBeInTheDocument();
});

it("does not call the status endpoint without a signed-in owner", async () => {
  state.status = "anonymous";
  render(<PurchaseResult />);
  expect(screen.getByRole("heading", { name: "Sign in to see this payment" })).toBeInTheDocument();
  expect(apiRequest).not.toHaveBeenCalled();
});

it("does not call the status endpoint when the payment reference is missing", async () => {
  state.query = "";
  render(<PurchaseResult />);
  expect(screen.getByText("The payment reference is missing.")).toBeInTheDocument();
  expect(apiRequest).not.toHaveBeenCalled();
});
