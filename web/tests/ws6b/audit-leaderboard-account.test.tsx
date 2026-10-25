import { Suspense } from "react";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import AuditPage from "@/app/(staff)/staff/audit/page";
import LeaderboardPage from "@/app/(staff)/staff/exams/[examId]/leaderboard/page";
import AccountPage from "@/app/(staff)/staff/account/page";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";

const EXAM = "eeeeeeee-0000-4000-8000-000000000001";

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

describe("staff audit log", () => {
  it("lists activity with translated actions for Master Admin", async () => {
    const api = mockApi({
      "GET /api/v1/audit-log": {
        content: [
          {
            id: "a1",
            actorUserId: "22222222-2222-2222-2222-222222222222",
            action: "PHONE_CHANGED",
            entityType: "USER",
            entityId: "11111111-1111-1111-1111-111111111111",
            ip: null,
            createdAt: "2026-10-25T04:00:00Z",
          },
          {
            id: "a2",
            actorUserId: null,
            action: "SOMETHING_NEW",
            entityType: "USER",
            entityId: null,
            ip: null,
            createdAt: "2026-10-25T03:00:00Z",
          },
        ],
        page: 1,
        size: 20,
        totalElements: 22,
        totalPages: 2,
      },
    });
    renderWithProviders(<AuditPage />, { user: fixtures.masterAdmin, route: "/staff/audit?page=1" });

    expect((await screen.findAllByText("ফোন নম্বর বদলানো হয়েছে")).length).toBeGreaterThan(0);
    expect((await screen.findAllByText("SOMETHING_NEW")).length).toBeGreaterThan(0);
    expect(api.calls.find((call) => call.path === "/api/v1/audit-log")?.search).toContain("page=1");
  });

  it("refuses System Admin with the not-found page", async () => {
    mockApi({});
    renderWithProviders(<AuditPage />, { user: fixtures.systemAdmin, route: "/staff/audit" });
    expect(await screen.findByText("পাতাটি পাওয়া যায়নি")).toBeInTheDocument();
  });
});

describe("staff leaderboard", () => {
  it("shows ranks, shared ranks and pass words, read only", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${EXAM}`]: {
        id: EXAM,
        moduleId: "bbbbbbbb-0000-4000-8000-000000000001",
        examType: "MCQ",
        titleEn: "Penal Code",
        titleBn: "পেনাল কোড",
        liveAt: "2026-10-24T06:00:00Z",
        endAt: "2026-10-24T06:40:00Z",
        status: "PUBLISHED",
      },
      [`GET /api/v1/live-exams/${EXAM}/leaderboard`]: {
        rows: [
          { name: "Rahim", rank: 1, score: 9, maximum: 10, passed: true },
          { name: "Nazma", rank: 2, score: 8, maximum: 10, passed: true },
          { name: "Tanvir", rank: 2, score: 8, maximum: 10, passed: true },
          { name: "Sabrina", rank: 8, score: 3, maximum: 10, passed: false },
        ],
      },
    });
    renderWithProviders(
      <Suspense fallback={null}>
        <LeaderboardPage params={resolved({ examId: EXAM })} />
      </Suspense>,
      { user: fixtures.instructor, route: `/staff/exams/${EXAM}/leaderboard` },
    );

    expect((await screen.findAllByText("Rahim")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("২").length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText("অনুত্তীর্ণ").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("উত্তীর্ণ").length).toBeGreaterThanOrEqual(3);
  });

  it("says the date is to be announced for an undated exam", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${EXAM}`]: {
        id: EXAM,
        moduleId: "bbbbbbbb-0000-4000-8000-000000000001",
        examType: "MCQ",
        titleEn: "Open paper",
        titleBn: "খোলা পরীক্ষা",
        liveAt: null,
        endAt: null,
        status: "PUBLISHED",
      },
      [`GET /api/v1/live-exams/${EXAM}/leaderboard`]: { rows: [] },
    });
    renderWithProviders(
      <Suspense fallback={null}>
        <LeaderboardPage params={resolved({ examId: EXAM })} />
      </Suspense>,
      { user: fixtures.masterAdmin, route: `/staff/exams/${EXAM}/leaderboard` },
    );
    expect((await screen.findAllByText("তারিখ পরে জানানো হবে")).length).toBeGreaterThan(0);
  });
});

describe("staff account", () => {
  it("changes the password with the current one, and says so", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "POST /api/v1/auth/change-password": () => undefined,
    });
    renderWithProviders(<AccountPage />, { user: fixtures.systemAdmin, route: "/staff/account" });

    await user.type(screen.getByLabelText(/বর্তমান পাসওয়ার্ড/), "old-password");
    await user.type(screen.getByLabelText(/নতুন পাসওয়ার্ড/), "new-password-1");
    await user.click(screen.getByRole("button", { name: "পাসওয়ার্ড বদলান" }));

    expect(await screen.findByText("পাসওয়ার্ড বদলানো হয়েছে।")).toBeInTheDocument();
    const sent = api.calls.find((call) => call.path === "/api/v1/auth/change-password");
    expect(sent?.body).toEqual({ currentPassword: "old-password", newPassword: "new-password-1" });
  });

  it("refuses a new password shorter than 8 characters before calling the API", async () => {
    const user = userEvent.setup();
    const api = mockApi({});
    renderWithProviders(<AccountPage />, { user: fixtures.instructor, route: "/staff/account" });

    await user.type(screen.getByLabelText(/বর্তমান পাসওয়ার্ড/), "old-password");
    await user.type(screen.getByLabelText(/নতুন পাসওয়ার্ড/), "short");
    await user.click(screen.getByRole("button", { name: "পাসওয়ার্ড বদলান" }));

    await waitFor(() => expect(screen.getByLabelText(/নতুন পাসওয়ার্ড/)).toHaveAttribute("aria-invalid", "true"));
    expect(api.calls.some((call) => call.path === "/api/v1/auth/change-password")).toBe(false);
  });
});
