import { screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { HomePage } from "@/components/student/home/home-page";
import { renderWithQuery } from "./helpers";

const apiRequest = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api/client", () => ({
  apiRequest: (...args: unknown[]) => apiRequest(...args),
}));

vi.mock("@/lib/auth/session-context", () => ({
  useSession: () => ({
    session: {
      status: "authenticated",
      user: {
        id: "s1",
        phone: "01700000001",
        email: "student@example.test",
        emailVerifiedAt: "2026-10-01T00:00:00Z",
        role: "STUDENT",
        status: "ACTIVE",
        fullName: "Student",
        studentCode: null,
        phoneRequired: false,
      },
    },
    login: vi.fn(),
  }),
}));

beforeEach(() => {
  window.localStorage.clear();
  apiRequest.mockReset();
  apiRequest.mockResolvedValue({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
});

it("shows the welcome card once after registration, then clears the flag", async () => {
  window.localStorage.setItem("exam-portal.welcome", "1");

  const first = renderWithQuery(<HomePage />);

  expect(await screen.findByText("Welcome to Exam Portal, Student!")).toBeInTheDocument();
  expect(window.localStorage.getItem("exam-portal.welcome")).toBeNull();
  first.unmount();

  renderWithQuery(<HomePage />);
  expect(screen.queryByText("Welcome to Exam Portal, Student!")).not.toBeInTheDocument();
});

it("hides the welcome card when no registration flag is set", async () => {
  renderWithQuery(<HomePage />);
  expect(await screen.findByText("You haven't bought any exams yet")).toBeInTheDocument();
  expect(screen.queryByText(/Welcome to Exam Portal/)).not.toBeInTheDocument();
});
