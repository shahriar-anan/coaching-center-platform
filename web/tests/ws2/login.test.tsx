import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { renderWithProviders } from "../helpers";
import LoginPage from "@/app/(auth)/login/page";
import GoogleReturnPage from "@/app/(auth)/login/google/page";

const session = vi.hoisted(() => ({ login: vi.fn() }));

vi.mock("@/lib/auth/session-context", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth/session-context")>();
  return {
    ...actual,
    useSession: () => ({
      session: { status: "anonymous" },
      sessionExpired: false,
      login: session.login,
      logout: vi.fn(),
      refreshMe: vi.fn(),
      acceptToken: vi.fn(),
    }),
  };
});

import { WrongAudienceError } from "@/lib/auth/session-context";

function apiError(code: string) {
  return new ApiError({ code, message: code, timestamp: "", requestId: "" });
}

beforeEach(() => {
  session.login.mockReset();
});

describe("login", () => {
  it("passes the identifier, password and ?next= to login", async () => {
    session.login.mockResolvedValue({ role: "STUDENT" });
    renderWithProviders(<LoginPage />, { route: "/login?next=%2Fexam%2Fabc" });
    await userEvent.type(screen.getByLabelText(/ইমেইল বা ফোন/), "01711000001");
    await userEvent.type(screen.getByLabelText(/পাসওয়ার্ড/, { selector: "input" }), "password1");
    await userEvent.click(screen.getByRole("button", { name: "লগইন করুন" }));
    expect(session.login).toHaveBeenCalledWith("01711000001", "password1", { next: "/exam/abc" });
  });

  it("shows one generic sentence for a wrong password and keeps the typed identifier", async () => {
    session.login.mockRejectedValue(apiError("INVALID_CREDENTIALS"));
    renderWithProviders(<LoginPage />);
    const identifier = screen.getByLabelText(/ইমেইল বা ফোন/);
    await userEvent.type(identifier, "rahim@example.com");
    await userEvent.type(screen.getByLabelText(/পাসওয়ার্ড/, { selector: "input" }), "wrong-pass");
    await userEvent.click(screen.getByRole("button", { name: "লগইন করুন" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("ইমেইল/ফোন অথবা পাসওয়ার্ড সঠিক নয়।");
    expect(identifier).toHaveValue("rahim@example.com");
  });

  it("shows the wait sentence when the account is locked", async () => {
    session.login.mockRejectedValue(apiError("LOGIN_LOCKED"));
    renderWithProviders(<LoginPage />);
    await userEvent.type(screen.getByLabelText(/ইমেইল বা ফোন/), "01711000001");
    await userEvent.type(screen.getByLabelText(/পাসওয়ার্ড/, { selector: "input" }), "password1");
    await userEvent.click(screen.getByRole("button", { name: "লগইন করুন" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("অনেকবার ভুল চেষ্টা হয়েছে");
  });

  it("does not call login when a field is empty and marks it required", async () => {
    renderWithProviders(<LoginPage />);
    await userEvent.click(screen.getByRole("button", { name: "লগইন করুন" }));
    expect(session.login).not.toHaveBeenCalled();
    expect(screen.getAllByText("আবশ্যক").length).toBeGreaterThan(0);
  });

  it("sends a staff account on the student site to the admin login with a message", async () => {
    session.login.mockRejectedValue(new WrongAudienceError("staff"));
    renderWithProviders(<LoginPage />);
    await userEvent.type(screen.getByLabelText(/ইমেইল বা ফোন/), "teacher@example.com");
    await userEvent.type(screen.getByLabelText(/পাসওয়ার্ড/, { selector: "input" }), "password1");
    await userEvent.click(screen.getByRole("button", { name: "লগইন করুন" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("অ্যাডমিন ও শিক্ষকেরা অ্যাডমিন প্যানেলে লগইন করুন");
  });

  it("keeps Google disabled with the coming-soon label until it is configured", () => {
    renderWithProviders(<LoginPage />);
    expect(screen.getByRole("button", { name: "গুগল দিয়ে লগইন" })).toBeDisabled();
    expect(screen.getByText("শীঘ্রই")).toBeInTheDocument();
  });
});

describe("google return", () => {
  it("shows the sentence for a known error code", () => {
    renderWithProviders(<GoogleReturnPage />, { route: "/login/google?error=GOOGLE_ROLE_NOT_ALLOWED" });
    expect(screen.getByRole("alert")).toHaveTextContent("এই লগইন শুধু শিক্ষার্থীদের জন্য।");
  });

  it("falls back to the generic sentence for an unknown code", () => {
    renderWithProviders(<GoogleReturnPage />, { route: "/login/google?error=SOMETHING_ELSE" });
    expect(screen.getByRole("alert")).toHaveTextContent("গুগল দিয়ে লগইন হয়নি।");
  });
});
