import { screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { PurchaseResult } from "@/components/student/purchase/purchase-result";
import { renderWithQuery } from "./helpers";

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
  window.sessionStorage.clear();
});

it("shows the paid state and the way into the module", async () => {
  window.sessionStorage.setItem(
    "exam-portal.order.tran-1",
    JSON.stringify({ scope: "MODULE", examId: null, moduleId: "m1", door: "mcq", title: "১৯তম বিজেএস" }),
  );
  apiRequest.mockResolvedValue({ status: "PAID", examName: "১৯তম বিজেএস" });

  renderWithQuery(<PurchaseResult />);

  expect(await screen.findByRole("heading", { name: "১৯তম বিজেএস is now unlocked" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Go to module" })).toHaveAttribute("href", "/mcq/m/m1");
  expect(apiRequest).toHaveBeenCalledWith(
    "/api/v1/live-exam-orders/status?tranId=tran-1",
    expect.anything(),
  );
});

it("says no money was taken when the payment failed", async () => {
  apiRequest.mockResolvedValue({ status: "FAILED", examName: "Practice exam" });

  renderWithQuery(<PurchaseResult />);

  expect(await screen.findByRole("heading", { name: "Payment not completed" })).toBeInTheDocument();
  expect(screen.getByText("No money was taken. You can try again.")).toBeInTheDocument();
});

it("shows the cancelled state with a retry", async () => {
  apiRequest.mockResolvedValue({ status: "CANCELLED", examName: "Practice exam" });

  renderWithQuery(<PurchaseResult />);

  expect(await screen.findByRole("heading", { name: "Payment cancelled" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Try again" })).toBeInTheDocument();
});

it("shows pending while the payment is confirmed", async () => {
  apiRequest.mockResolvedValue({ status: "PENDING", examName: "Practice exam" });

  renderWithQuery(<PurchaseResult />);

  expect(await screen.findByRole("heading", { name: "Confirming your payment…" })).toBeInTheDocument();
  await waitFor(() => expect(apiRequest).toHaveBeenCalled());
});

it("asks a signed-out visitor to log in and makes no status call", () => {
  state.status = "anonymous";

  renderWithQuery(<PurchaseResult />);

  expect(screen.getByRole("heading", { name: "Log in to see this payment" })).toBeInTheDocument();
  expect(apiRequest).not.toHaveBeenCalled();
});

it("explains a missing payment reference without calling the API", () => {
  state.query = "";

  renderWithQuery(<PurchaseResult />);

  expect(
    screen.getByText("We couldn't find the payment details. Check My modules instead."),
  ).toBeInTheDocument();
  expect(apiRequest).not.toHaveBeenCalled();
});
