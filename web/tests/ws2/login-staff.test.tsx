import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "../helpers";
import LoginPage from "@/app/(auth)/login/page";
import { StaffAuthShell } from "@/components/shell/staff-auth-shell";
import { SurfaceProvider } from "@/lib/url/surface-context";

vi.mock("@/lib/auth/session-context", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth/session-context")>();
  return {
    ...actual,
    useSession: () => ({
      session: { status: "anonymous" },
      sessionExpired: false,
      login: vi.fn(),
      logout: vi.fn(),
      refreshMe: vi.fn(),
      acceptToken: vi.fn(),
    }),
  };
});

function staffLogin(locale: "bn" | "en" = "bn") {
  return renderWithProviders(
    <SurfaceProvider surface="staff">
      <StaffAuthShell>
        <LoginPage />
      </StaffAuthShell>
    </SurfaceProvider>,
    { route: "/login", locale },
  );
}

describe("admin panel sign-in (admin host)", () => {
  it("is headed for administrators, staff and instructors", () => {
    staffLogin();
    expect(screen.getByRole("heading", { level: 1, name: "অ্যাডমিন প্যানেলে লগইন" })).toBeInTheDocument();
    for (const role of ["মাস্টার অ্যাডমিন", "সিস্টেম অ্যাডমিন", "শিক্ষক"]) {
      expect(screen.getByText(role)).toBeInTheDocument();
    }
    expect(screen.getByLabelText(/ইমেইল বা ফোন/)).toBeInTheDocument();
    expect(screen.getByLabelText(/পাসওয়ার্ড/, { selector: "input" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "লগইন করুন" })).toBeInTheDocument();
  });

  it("has no sign-up link and no Google button, but keeps password recovery and activation", () => {
    staffLogin();
    expect(screen.queryByRole("link", { name: "অ্যাকাউন্ট খুলুন" })).not.toBeInTheDocument();
    expect(screen.queryByText(/গুগল/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "পাসওয়ার্ড ভুলে গেছেন?" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "কোড দিয়ে অ্যাকাউন্ট চালু করুন" })).toBeInTheDocument();
  });

  it("says the Master Admin password cannot be reset here, and only on the admin sign-in", () => {
    staffLogin();
    expect(screen.getByText(/মাস্টার অ্যাডমিনের পাসওয়ার্ড এখানে রিসেট করা যায় না/)).toBeInTheDocument();
  });

  it("has no student menu and no footer, and links to the student site", () => {
    staffLogin();
    expect(screen.queryByRole("navigation", { name: "মূল মেনু" })).not.toBeInTheDocument();
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "শিক্ষার্থীদের সাইটে যান" })).toBeInTheDocument();
  });

  it("is written in English when the language is English", () => {
    staffLogin("en");
    expect(screen.getByRole("heading", { level: 1, name: "Sign in to the admin panel" })).toBeInTheDocument();
    expect(screen.getByText("Master Admin")).toBeInTheDocument();
  });
});

describe("student sign-in without the admin surface", () => {
  it("still offers sign-up and keeps the student heading", () => {
    renderWithProviders(<LoginPage />, { route: "/login" });
    expect(screen.getByRole("heading", { level: 1, name: "লগইন করুন" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "অ্যাকাউন্ট খুলুন" })).toBeInTheDocument();
    expect(screen.queryByText(/মাস্টার অ্যাডমিনের পাসওয়ার্ড এখানে রিসেট করা যায় না/)).not.toBeInTheDocument();
  });
});
