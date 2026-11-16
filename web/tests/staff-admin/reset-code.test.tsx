import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PasswordResetCodeButton } from "@/components/staff/status/account-controls";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

describe("a password reset code for a student (16 Nov)", () => {
  it("gives the Master Admin a code to hand over, with how to use it", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "POST /api/v1/students/s-1/password-reset-code": { resetCode: "48213977" } });
    renderWithProviders(<PasswordResetCodeButton id="s-1" name="Rahim Uddin" status="ACTIVE" />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });

    await user.click(screen.getByRole("button", { name: "Password reset code" }));

    expect(await screen.findByText("48213977")).toBeInTheDocument();
    expect(screen.getByText(/I already have a code from the office/)).toBeInTheDocument();
    expect(screen.getByText(/works for 10 minutes/)).toBeInTheDocument();
    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/students/s-1/password-reset-code")).toBe(true));
  });

  it("is not offered to a System Admin, nor for an account that is not active", () => {
    mockApi({});
    const { unmount } = renderWithProviders(<PasswordResetCodeButton id="s-1" name="Rahim Uddin" status="ACTIVE" />, {
      user: fixtures.systemAdmin,
      locale: "en",
    });
    expect(screen.queryByRole("button", { name: "Password reset code" })).not.toBeInTheDocument();
    unmount();

    renderWithProviders(<PasswordResetCodeButton id="s-1" name="Rahim Uddin" status="PENDING_ACTIVATION" />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });
    expect(screen.queryByRole("button", { name: "Password reset code" })).not.toBeInTheDocument();
  });
});
