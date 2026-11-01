import { screen, within } from "@testing-library/react";
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

  expect(await screen.findByText("Welcome to Symam Law, Student!")).toBeInTheDocument();
  expect(window.localStorage.getItem("exam-portal.welcome")).toBeNull();
  first.unmount();

  renderWithQuery(<HomePage />);
  expect(screen.queryByText("Welcome to Symam Law, Student!")).not.toBeInTheDocument();
});

it("hides the welcome card when no registration flag is set", async () => {
  renderWithQuery(<HomePage />);
  expect(await screen.findByRole("link", { name: /My orders/ })).toBeInTheDocument();
  expect(screen.queryByText(/Welcome to Symam Law/)).not.toBeInTheDocument();
});

it("shows the section labels, and an upcoming exam as a row in Coming up with its status pill", async () => {
  const routineRow = {
    id: "r1",
    titleEn: "Mock test one",
    titleBn: "মক টেস্ট ওয়ান",
    examType: "MCQ",
    liveAt: "2099-01-01T10:00:00Z",
    endAt: "2099-01-01T10:30:00Z",
    myStatus: "UPCOMING",
    retakeAvailable: false,
  };
  apiRequest.mockImplementation((path: string) =>
    Promise.resolve(
      path.startsWith("/api/v1/me/exam-routine")
        ? { content: [routineRow], page: 0, size: 100, totalElements: 1, totalPages: 1 }
        : { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
    ),
  );

  renderWithQuery(<HomePage />);

  expect(await screen.findByRole("heading", { name: "Next exam" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "Coming up" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: /My orders/ })).toHaveAttribute("href", "/my-modules");
  const row = await screen.findByRole("link", { name: /Mock test one/ });
  expect(row).toHaveAttribute("href", "/exam/r1");
  expect(within(row).getByText("Upcoming")).toBeInTheDocument();
});

it("shows four doors before the next exam, and has no Live now list", async () => {
  const liveRow = {
    id: "r2",
    titleEn: "Running now",
    titleBn: "চলছে",
    examType: "MCQ",
    liveAt: "2020-01-01T10:00:00Z",
    endAt: "2099-01-01T10:30:00Z",
    myStatus: "OPEN",
    retakeAvailable: false,
  };
  apiRequest.mockImplementation((path: string) =>
    Promise.resolve(
      path.startsWith("/api/v1/me/exam-routine")
        ? { content: [liveRow], page: 0, size: 100, totalElements: 1, totalPages: 1 }
        : { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 },
    ),
  );

  renderWithQuery(<HomePage />);

  const next = await screen.findByRole("heading", { name: "Next exam" });
  const packages = screen.getByRole("link", { name: /Exam packages/ });
  expect(packages.compareDocumentPosition(next) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  // The separate list of exams that are live now is gone; the one next exam still shows.
  expect(screen.queryByRole("heading", { name: "Live now" })).not.toBeInTheDocument();
});
