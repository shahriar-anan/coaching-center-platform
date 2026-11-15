import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import AdmissionPage from "@/app/(site)/admission/form/page";
import { admissionView, completeForm } from "./admission-fixture";

describe("confirm email while under review", () => {
  it("shows the confirm-email card to a desk student whose email is not verified", async () => {
    mockApi({
      "GET /api/v1/me/admission": admissionView("SUBMITTED", { submittedAt: "2026-11-10T04:00:00Z", submitCount: 1 }, completeForm),
    });
    renderWithProviders(<AdmissionPage />, {
      user: { ...fixtures.student, emailVerifiedAt: null },
      locale: "en",
      route: "/admission/form",
    });

    expect(await screen.findByRole("heading", { name: "Confirm your email" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send me a code" })).toBeInTheDocument();
  });

  it("hides the card once the email is verified", async () => {
    mockApi({
      "GET /api/v1/me/admission": admissionView("SUBMITTED", { submittedAt: "2026-11-10T04:00:00Z", submitCount: 1 }, completeForm),
    });
    renderWithProviders(<AdmissionPage />, {
      user: { ...fixtures.student, emailVerifiedAt: "2026-11-10T04:00:00Z" },
      locale: "en",
      route: "/admission/form",
    });

    expect(await screen.findByRole("heading", { name: "Your admission is under review" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Confirm your email" })).not.toBeInTheDocument();
  });

  it("sends a code to the form email, then confirms with the code", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/me/admission": admissionView("SUBMITTED", { submittedAt: "2026-11-10T04:00:00Z", submitCount: 1 }, completeForm),
      "POST /api/v1/auth/verify-email/resend": { message: "sent" },
      "POST /api/v1/auth/verify-email": undefined,
    });
    renderWithProviders(<AdmissionPage />, {
      user: { ...fixtures.student, emailVerifiedAt: null },
      locale: "en",
      route: "/admission/form",
    });

    await user.click(await screen.findByRole("button", { name: "Send me a code" }));
    await user.type(await screen.findByLabelText("Code from the email"), "123456");
    await user.click(screen.getByRole("button", { name: "Confirm" }));

    expect(await screen.findByText("Your email is confirmed.")).toBeInTheDocument();
    const resend = api.calls.find((call) => call.path === "/api/v1/auth/verify-email/resend");
    expect(resend?.body).toEqual({ email: completeForm.email });
    const verify = api.calls.find((call) => call.path === "/api/v1/auth/verify-email");
    expect(verify?.body).toEqual({ code: "123456" });
  });
});
