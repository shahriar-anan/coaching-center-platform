import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { JoinPage } from "@/components/public-admission/join-page";
import { admissionView, completeForm } from "../offline-admission/admission-fixture";
import { ErrorBody, fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

describe("joining a batch with the code from the email (plan B, C)", () => {
  it("sends the code, then opens My admission", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "POST /api/v1/me/admission/join": admissionView("ONBOARDED", { batchNameEn: "Batch 3 (Evening)" }, completeForm),
    });
    renderWithProviders(<JoinPage />, { user: fixtures.student, locale: "en", route: "/admission/join" });

    await user.type(screen.getByRole("textbox", { name: /Join code/ }), "k7q2-9pxa");
    await user.click(screen.getByRole("button", { name: "Join the batch" }));

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/offline-activities"));
    expect(api.calls.find((call) => call.path === "/api/v1/me/admission/join")?.body).toEqual({ code: "K7Q2-9PXA" });
  });

  it("asks for the code before sending anything", async () => {
    const user = userEvent.setup();
    const api = mockApi({});
    renderWithProviders(<JoinPage />, { user: fixtures.student, locale: "en", route: "/admission/join" });

    await user.click(screen.getByRole("button", { name: "Join the batch" }));
    expect(await screen.findByText("Fill in this field.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });

  it("says a wrong, expired or used code is refused, and keeps the box", async () => {
    const user = userEvent.setup();
    mockApi({
      "POST /api/v1/me/admission/join": new ErrorBody(400, { code: "INVALID_CODE", message: "Invalid code." }),
    });
    renderWithProviders(<JoinPage />, { user: fixtures.student, locale: "en", route: "/admission/join" });

    const box = screen.getByRole("textbox", { name: /Join code/ });
    await user.type(box, "WRONG-CODE");
    await user.click(screen.getByRole("button", { name: "Join the batch" }));

    expect(await screen.findByText("This code is wrong, has expired or was already used. Ask the office for a new one.")).toBeInTheDocument();
    expect(box).toHaveValue("WRONG-CODE");
    expect(routerState.replace).not.toHaveBeenCalledWith("/offline-activities");
  });
});
