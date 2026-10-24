import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";

const session = vi.hoisted(() => ({
  status: "anonymous" as "anonymous" | "authenticated" | "loading",
  emailVerifiedAt: null as string | null,
}));

const api = vi.hoisted(() => ({
  register: vi.fn(),
  forgotPassword: vi.fn(),
  resetPassword: vi.fn(),
  resendVerification: vi.fn(),
  changePassword: vi.fn(),
}));

const replace = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api/client", () => ({ api }));

vi.mock("@/i18n/locale-context", () => ({
  useLocale: () => ({ t: (key: string) => key, locale: "en", setLocale: () => undefined }),
}));

vi.mock("@/components/locale-switcher", () => ({ LocaleSwitcher: () => null }));

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn() }),
}));

vi.mock("@/lib/auth/session-context", () => ({
  useSession: () => ({
    session:
      session.status === "authenticated"
        ? {
            status: "authenticated",
            user: {
              email: "student@example.com",
              emailVerifiedAt: session.emailVerifiedAt,
              role: "STUDENT",
            },
          }
        : { status: session.status },
  }),
}));

import AccountPage from "@/app/account/page";
import ForgotPasswordPage from "@/app/forgot-password/page";
import RegisterPage from "@/app/register/page";
import ResetPasswordPage from "@/app/reset-password/page";

beforeEach(() => {
  session.status = "anonymous";
  session.emailVerifiedAt = null;
  api.register.mockReset();
  api.forgotPassword.mockReset();
  api.resetPassword.mockReset();
  api.resendVerification.mockReset();
  api.changePassword.mockReset();
  replace.mockReset();
});

it("registers without signing in and shows that the student can sign in", async () => {
  api.register.mockResolvedValue({ id: "1" });
  const user = userEvent.setup();
  render(<RegisterPage />);

  await user.type(screen.getByLabelText("auth.fullName"), "Naeem");
  await user.type(screen.getByLabelText("auth.phone"), "01711000001");
  await user.type(screen.getByLabelText("auth.email"), "naeem@example.com");
  await user.type(screen.getByLabelText("auth.password"), "password1");
  await user.click(screen.getByRole("button", { name: "auth.registerSubmit" }));

  expect(api.register).toHaveBeenCalledWith({
    fullName: "Naeem",
    phone: "01711000001",
    email: "naeem@example.com",
    password: "password1",
  });
  expect(screen.getByText("auth.registerSuccess")).toBeInTheDocument();
  expect(screen.getByText("auth.registerSuccessHint")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "auth.submit" })).toHaveAttribute("href", "/login");
  expect(screen.queryByText("auth.emailUnverified")).not.toBeInTheDocument();
});

it("shows a duplicate phone error and keeps the other fields", async () => {
  api.register.mockRejectedValue(
    new ApiError({
      code: "DUPLICATE_PHONE",
      message: "Phone number is already in use.",
      timestamp: "2026-10-24T00:00:00Z",
      requestId: "req-1",
    }),
  );
  const user = userEvent.setup();
  render(<RegisterPage />);
  await user.type(screen.getByLabelText("auth.fullName"), "Naeem");
  await user.type(screen.getByLabelText("auth.phone"), "01711000001");
  await user.type(screen.getByLabelText("auth.email"), "naeem@example.com");
  await user.type(screen.getByLabelText("auth.password"), "password1");
  await user.click(screen.getByRole("button", { name: "auth.registerSubmit" }));

  expect(screen.getByRole("alert")).toHaveTextContent("errors.duplicatePhone");
  expect(screen.getByLabelText("auth.email")).toHaveValue("naeem@example.com");
});

it("always shows the same forgot-password sentence and uses a send-code label", async () => {
  api.forgotPassword.mockResolvedValue({ message: "sent" });
  const user = userEvent.setup();
  render(<ForgotPasswordPage />);

  expect(screen.getByRole("button", { name: "auth.forgotSubmit" })).toHaveTextContent("auth.forgotSubmit");
  await user.type(screen.getByLabelText("auth.email"), "missing@example.com");
  await user.click(screen.getByRole("button", { name: "auth.forgotSubmit" }));

  expect(api.forgotPassword).toHaveBeenCalledWith("missing@example.com");
  expect(screen.getByText("auth.forgotSent")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "auth.backToLogin" })).toHaveAttribute("href", "/login");
});

it("resets with the code and the new password, then offers login", async () => {
  api.resetPassword.mockResolvedValue(undefined);
  const user = userEvent.setup();
  render(<ResetPasswordPage />);

  await user.type(screen.getByLabelText("auth.code"), "K7M2Q9RX");
  await user.type(screen.getByLabelText("auth.newPassword"), "newpassword1");
  await user.click(screen.getByRole("button", { name: "auth.resetSubmit" }));

  expect(api.resetPassword).toHaveBeenCalledWith("K7M2Q9RX", "newpassword1");
  expect(screen.getByText("auth.resetSuccess")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "auth.submit" })).toHaveAttribute("href", "/login");
});

it("shows verification state, resends, and changes the password", async () => {
  session.status = "authenticated";
  api.resendVerification.mockResolvedValue({ message: "sent" });
  api.changePassword.mockResolvedValue(undefined);
  const user = userEvent.setup();
  render(<AccountPage />);

  expect(screen.getByText("auth.emailUnverified")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "auth.resendVerification" }));
  expect(api.resendVerification).toHaveBeenCalledWith("student@example.com");
  expect(screen.getByText("auth.resendSent")).toBeInTheDocument();

  await user.type(screen.getByLabelText("auth.currentPassword"), "password1");
  await user.type(screen.getByLabelText("auth.newPassword"), "newpassword1");
  await user.click(screen.getByRole("button", { name: "auth.changePassword" }));
  expect(api.changePassword).toHaveBeenCalledWith("password1", "newpassword1");
  expect(screen.getByText("auth.passwordChanged")).toBeInTheDocument();
});

it("sends an anonymous account visitor to login", () => {
  render(<AccountPage />);
  expect(replace).toHaveBeenCalledWith("/login");
});
