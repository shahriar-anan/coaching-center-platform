import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import AccountPage from "@/app/(site)/account/page";
import { DashboardPage as HomePage } from "@/components/student/home/home-page";
import { StudentShell } from "@/components/shell/student-shell";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { admission, due, schedule } from "./fixture";

// The links read the student's admissions list, and the fees of the onboarded one by its id (admission-access-plan.md).
const FEES = "GET /api/v1/me/admissions/adm-1/fees";
const ADMISSION = "GET /api/v1/me/admissions";
const UNREAD = "GET /api/v1/me/notices/unread-count";

/** Two dues: the first paid, the second owing 2,000. The next payment is the second. */
const onboardedFees = schedule({ dues: [due(1, "2026-11-15", 3000, 3000), due(2, "2026-12-15", 3000, 1000)] });

const noAdmission: unknown[] = [];

function renderHome(admissionAnswer: unknown, feesAnswer: unknown = onboardedFees) {
  const api = mockApi({
    [ADMISSION]: admissionAnswer,
    [FEES]: feesAnswer,
    [UNREAD]: { count: 0 },
  });
  renderWithProviders(<HomePage />, { user: fixtures.student, locale: "en", route: "/dashboard" });
  return api;
}

function renderShell(admissionAnswer: unknown) {
  const api = mockApi({
    [ADMISSION]: admissionAnswer,
    [FEES]: onboardedFees,
    [UNREAD]: { count: 0 },
  });
  renderWithProviders(
    <StudentShell>
      <p>page content</p>
    </StudentShell>,
    { user: fixtures.student, locale: "en", route: "/" },
  );
  return api;
}

function renderAccount(admissionAnswer: unknown) {
  const api = mockApi({
    [ADMISSION]: admissionAnswer,
    [FEES]: onboardedFees,
    [UNREAD]: { count: 0 },
  });
  renderWithProviders(<AccountPage />, { user: fixtures.student, locale: "en", route: "/account" });
  return api;
}

const homeCard = () => screen.queryByRole("link", { name: /My admission: next payment/ });
const feesRequested = (api: { calls: { path: string }[] }) => api.calls.some((call) => call.path.endsWith("/fees"));

describe("home card (plan 6.4): only for an onboarded admission", () => {
  it("stays for the batch the student is in while they apply for another", async () => {
    renderHome([admission("SUBMITTED", { id: "adm-2", source: "ACCOUNT" }), admission("ONBOARDED")]);

    expect(await screen.findByRole("link", { name: /My admission: next payment ৳2,000\.00 on 15 Dec/ })).toBeInTheDocument();
  });

  it("shows the next payment amount and date", async () => {
    renderHome([admission("ONBOARDED")]);

    const card = await screen.findByRole("link", { name: /My admission: next payment ৳2,000\.00 on 15 Dec/ });
    expect(card).toHaveAttribute("href", "/offline-activities");
  });

  it("is not shown to an online student, and makes no fee request", async () => {
    const api = renderHome(noAdmission);

    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/me/admissions")).toBe(true));
    expect(homeCard()).not.toBeInTheDocument();
    expect(feesRequested(api)).toBe(false);
  });

  it("is gone once the admission is closed, and makes no fee request", async () => {
    const api = renderHome([admission("CLOSED")]);

    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/me/admissions")).toBe(true));
    expect(homeCard()).not.toBeInTheDocument();
    expect(feesRequested(api)).toBe(false);
  });
});

describe("account menu entries", () => {
  async function openMenu() {
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: /Menu for/ }));
  }

  // 17 Nov: the menu lists Dashboard and My offline batches for every student, as the side menu does; the batches page has the
  // batches on offer for a student with none.
  it("lists Dashboard and My offline batches for a student with an admission", async () => {
    renderShell([admission("ONBOARDED")]);

    await openMenu();
    expect(await screen.findByRole("menuitem", { name: "Dashboard" })).toHaveAttribute("href", "/dashboard");
    expect(screen.getByRole("menuitem", { name: "My offline batches" })).toHaveAttribute("href", "/offline-activities?tab=mine");
  });

  it("lists them for an online student too", async () => {
    renderShell(noAdmission);

    await openMenu();
    expect(await screen.findByRole("menuitem", { name: "My offline batches" })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "My admission" })).not.toBeInTheDocument();
  });
});

describe("account page row: only for an onboarded admission", () => {
  it("is shown for an onboarded admission", async () => {
    renderAccount([admission("ONBOARDED")]);

    expect(await screen.findByRole("link", { name: "My admission" })).toHaveAttribute("href", "/offline-activities");
  });

  it("is not shown for an online student", async () => {
    renderAccount(noAdmission);

    await waitFor(() => expect(screen.getByRole("link", { name: /Payment history/ })).toBeInTheDocument());
    expect(screen.queryByRole("link", { name: "My admission" })).not.toBeInTheDocument();
  });

  it("is not shown once the admission is closed", async () => {
    renderAccount([admission("CLOSED")]);

    await waitFor(() => expect(screen.getByRole("link", { name: /Payment history/ })).toBeInTheDocument());
    expect(screen.queryByRole("link", { name: "My admission" })).not.toBeInTheDocument();
  });
});
