import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AdmissionDetailView } from "@/components/staff/offline/admission-detail";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ADMISSION_ID, detail, page } from "../staff-offline-admission/fixtures";

const ROUTE = `/staff/admissions/${ADMISSION_ID}`;

/** The routes an admission screen reads. Anything else answers 404 and the screen shows what it can. */
function baseRoutes(overrides: Parameters<typeof detail>[0]) {
  return {
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: detail({ readyProblems: [], ...overrides }),
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}/history`]: [],
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/live-exams": page([]),
    "GET /api/v1/live-exam-modules": page([]),
  };
}

describe("the account a website application will use (plan Q3)", () => {
  it("says a new account will be made when no existing account matches", async () => {
    mockApi(baseRoutes({ state: "READY", source: "PUBLIC", matchedName: null, matchedEmail: null, matchedHasBatch: false }));
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    expect(await screen.findByText("A new account will be made")).toBeInTheDocument();
  });

  it("names the matched account and its email, before Onboard", async () => {
    mockApi(
      baseRoutes({
        state: "READY",
        source: "PUBLIC",
        matchedName: "Rahim U.", matchedEmail: "rahim@old.example", matchedHasBatch: false,
      }),
    );
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    expect(await screen.findByText("Matches the account of Rahim U. (rahim@old.example)")).toBeInTheDocument();
  });
});

describe("the onboard dialog says which email gets which code (plan P8)", () => {
  it("names the email that gets the activation code, and that it lasts 7 days", async () => {
    const user = userEvent.setup();
    mockApi(baseRoutes({ state: "READY", source: "PUBLIC", matchedName: null, matchedEmail: null, matchedHasBatch: false }));
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    await user.click(await screen.findByRole("button", { name: "Onboard" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("The activation code goes to rahim@example.com. The student uses it to make an account. The code lasts 7 days.")).toBeInTheDocument();
  });

  it("names the join code and the matched account's email when the application matched an account", async () => {
    const user = userEvent.setup();
    mockApi(
      baseRoutes({
        state: "READY",
        source: "PUBLIC",
        matchedName: "Rahim U.", matchedEmail: "rahim@old.example", matchedHasBatch: false,
      }),
    );
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    await user.click(await screen.findByRole("button", { name: "Onboard" }));
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText("The join code goes to rahim@old.example. The student joins the batch from their account. The code lasts 7 days."),
    ).toBeInTheDocument();
  });
});

describe("after onboarding, the code can be sent again (plan P8)", () => {
  it("shows when the code was sent and posts a new one", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      ...baseRoutes({ state: "ONBOARDED", source: "PUBLIC", joinCodeSentAt: "2026-11-13T04:00:00Z", canResendCode: true }),
      [`POST /api/v1/offline-admissions/${ADMISSION_ID}/resend-code`]: detail({ state: "ONBOARDED", joinCodeSentAt: "2026-11-13T05:00:00Z" }),
    });
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, { user: fixtures.masterAdmin, locale: "en", route: ROUTE });

    expect(await screen.findByText(/Code sent on/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Send a new code" }));
    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "POST" && call.path === `/api/v1/offline-admissions/${ADMISSION_ID}/resend-code`)).toBe(true),
    );
  });
});
