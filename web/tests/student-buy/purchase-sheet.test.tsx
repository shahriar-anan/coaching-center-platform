import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PurchaseSheet } from "@/components/student/purchase/purchase-sheet";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const sheet = () => (
  <PurchaseSheet
    open
    onOpenChange={() => undefined}
    item={{ kind: "exam", id: "e1", title: "CPC MCQ", price: 300, door: "mcq" }}
  />
);

/** The order the API answers with when a gateway page is not needed: the sheet only needs the request body. */
const started = { orderId: "o1", tranId: "t1", status: "INITIATED", amount: 300, redirectUrl: null, scope: "EXAM", moduleId: "m1", examId: "e1" };

function postedBodies(calls: { method: string; path: string; body: unknown }[]) {
  return calls.filter((call) => call.method === "POST" && call.path === "/api/v1/live-exam-orders").map((call) => call.body);
}

describe("the purchase sheet amounts (S2.6)", () => {
  it("charges the effective price, with the list price struck through, and names the package the exam is in", () => {
    renderWithProviders(
      <PurchaseSheet
        open
        onOpenChange={() => undefined}
        item={{
          kind: "exam",
          id: "e1",
          title: "CPC MCQ",
          price: 300,
          listPrice: 400,
          discountPercent: 25,
          discountEndsAt: "2099-11-30T17:59:59Z",
          packages: [{ id: "m1", title: "Package one" }],
          door: "mcq",
        }}
      />,
      { user: fixtures.student, locale: "en" },
    );

    expect(screen.getByText("In package: Package one")).toBeInTheDocument();
    expect(document.querySelector("s")?.textContent).toContain("400");
    expect(screen.getByText(/25% off/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /300/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /400/ })).not.toBeInTheDocument();
  });

  it("shows one plain price and no package line when the exam is not discounted and in no package", () => {
    renderWithProviders(
      <PurchaseSheet
        open
        onOpenChange={() => undefined}
        item={{ kind: "exam", id: "e2", title: "Evidence MCQ", price: 250, door: "mcq" }}
      />,
      { user: fixtures.student, locale: "en" },
    );

    expect(document.querySelector("s")).toBeNull();
    expect(screen.queryByText(/In package/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /250/ })).toBeInTheDocument();
  });
});

describe("the receipt address on the purchase sheet (Phase 4B)", () => {
  it("asks for an optional receipt address, and skip clears it and hides the question until it is asked for again", async () => {
    renderWithProviders(sheet(), { user: fixtures.student, locale: "en" });
    await userEvent.type(screen.getByLabelText("Send receipt to (optional)"), "reader@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Skip" }));
    expect(screen.queryByLabelText("Send receipt to (optional)")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Add a receipt address" }));
    expect(screen.getByLabelText("Send receipt to (optional)")).toHaveValue("");
  });

  it("sends no receipt address after a skip", async () => {
    const { calls } = mockApi({ "POST /api/v1/live-exam-orders": started });
    renderWithProviders(sheet(), { user: fixtures.student, locale: "en" });
    await userEvent.type(screen.getByLabelText("Send receipt to (optional)"), "reader@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Skip" }));
    await userEvent.click(screen.getByRole("button", { name: /securely/ }));
    await waitFor(() => expect(postedBodies(calls)).toHaveLength(1));
    expect(postedBodies(calls)[0]).toEqual({ examId: "e1" });
  });

  it("sends the typed address with the order when one is filled in", async () => {
    const { calls } = mockApi({ "POST /api/v1/live-exam-orders": started });
    renderWithProviders(sheet(), { user: fixtures.student, locale: "en" });
    await userEvent.type(screen.getByLabelText("Send receipt to (optional)"), "  Reader@Example.com ");
    await userEvent.click(screen.getByRole("button", { name: /securely/ }));
    await waitFor(() => expect(postedBodies(calls)).toHaveLength(1));
    expect(postedBodies(calls)[0]).toEqual({ examId: "e1", receiptEmail: "Reader@Example.com" });
  });

  it("shows the field error for an address that is not an email, and sends no request", async () => {
    const { calls } = mockApi({ "POST /api/v1/live-exam-orders": started });
    renderWithProviders(sheet(), { user: fixtures.student, locale: "en" });
    await userEvent.type(screen.getByLabelText("Send receipt to (optional)"), "not-an-email");
    await userEvent.click(screen.getByRole("button", { name: /securely/ }));
    expect(await screen.findByText("Enter a valid email address, such as name@example.com.")).toBeInTheDocument();
    expect(screen.getByLabelText("Send receipt to (optional)")).toHaveAttribute("aria-invalid", "true");
    expect(postedBodies(calls)).toHaveLength(0);
  });
});
