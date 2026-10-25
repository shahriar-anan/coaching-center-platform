import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import ForgotPasswordPage from "@/app/(auth)/forgot-password/page";
import { apiError, mockApi, renderWithProviders } from "../helpers";

describe("forgot password", () => {
  it("always shows the same sentence, even for an unknown email", async () => {
    mockApi({ "POST /api/v1/auth/forgot-password": { message: "sent" } });
    renderWithProviders(<ForgotPasswordPage />);
    await userEvent.type(screen.getByLabelText(/^ইমেইল/), "nobody@example.com");
    await userEvent.click(screen.getByRole("button", { name: "কোড পাঠান" }));
    expect(
      await screen.findByText("এই ইমেইলে কোনো অ্যাকাউন্ট থাকলে সেখানে একটি কোড পাঠানো হয়েছে।"),
    ).toBeInTheDocument();
  });

  it("shows the rate-limit sentence when too many requests were made", async () => {
    mockApi({ "POST /api/v1/auth/forgot-password": () => apiError(429, "EMAIL_RATE_LIMITED") });
    renderWithProviders(<ForgotPasswordPage />);
    await userEvent.type(screen.getByLabelText(/^ইমেইল/), "rahim@example.com");
    await userEvent.click(screen.getByRole("button", { name: "কোড পাঠান" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("অনেকবার অনুরোধ হয়েছে।");
  });
});
