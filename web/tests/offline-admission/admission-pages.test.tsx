import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AdmissionPage, AdmissionPrintPage } from "@/components/student/admission/admission-pages";
import { apiError, fixtures, mockApi, renderWithProviders, routerState } from "../helpers";
import { admissionView, completeForm } from "./admission-fixture";

describe("under review (plan 6.2)", () => {
  it("says the admission is under review, with the submitted date, a print link and, for a walk-in student, logout", async () => {
    const user = userEvent.setup();
    const logout = vi.fn(async () => undefined);
    const desk = admissionView("SUBMITTED", { submittedAt: "2026-11-10T04:00:00Z", submitCount: 1, source: "DESK" }, completeForm);
    mockApi({
      "GET /api/v1/me/admission": desk,
      "GET /api/v1/me/admissions": [desk],
    });
    renderWithProviders(<AdmissionPage />, {
      user: fixtures.student,
      locale: "en",
      route: "/admission",
      session: { logout },
    });

    expect(await screen.findByRole("heading", { name: "Your admission is under review" })).toBeInTheDocument();
    expect(screen.getByText(/Submitted on \d+ \w+ 2026/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View / print my form" })).toHaveAttribute("href", "/admission/print");

    // No form, no catalog or exams here.
    expect(screen.queryByLabelText(/^Full name/)).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /exam|module/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Log out" }));
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it("leads a student who applied from their account back to the rest of the site, not to logout", async () => {
    const own = admissionView("SUBMITTED", { submittedAt: "2026-11-10T04:00:00Z", submitCount: 1, source: "ACCOUNT" }, completeForm);
    mockApi({ "GET /api/v1/me/admission": own, "GET /api/v1/me/admissions": [own] });
    renderWithProviders(<AdmissionPage />, { user: fixtures.student, locale: "en", route: "/admission/form" });

    expect(await screen.findByRole("link", { name: "Go to home" })).toHaveAttribute("href", "/dashboard");
    expect(screen.queryByRole("button", { name: "Log out" })).not.toBeInTheDocument();
  });

  it("once onboarded with a join code out, says it is approved and leads to the join form", async () => {
    const ready = admissionView(
      "READY",
      { submittedAt: "2026-11-10T04:00:00Z", source: "ACCOUNT", joinCodePending: true, batchNameEn: "20th BJS Batch" },
      completeForm,
    );
    mockApi({ "GET /api/v1/me/admission": ready, "GET /api/v1/me/admissions": [ready] });
    renderWithProviders(<AdmissionPage />, { user: fixtures.student, locale: "en", route: "/admission/form" });

    expect(await screen.findByRole("heading", { name: "Your admission is approved" })).toBeInTheDocument();
    expect(screen.getByText("Batch: 20th BJS Batch")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Enter join code" })).toHaveAttribute("href", "/admission/join");
  });

  it("shows the same view once the form is ready for the next step", async () => {
    mockApi({ "GET /api/v1/me/admission": admissionView("READY", { submittedAt: "2026-11-10T04:00:00Z" }, completeForm) });
    renderWithProviders(<AdmissionPage />, { user: fixtures.student, locale: "en", route: "/admission" });

    expect(await screen.findByRole("heading", { name: "Your admission is under review" })).toBeInTheDocument();
  });

  it("sends a student with no admission home, not to a form", async () => {
    mockApi({ "GET /api/v1/me/admission": apiError(404, "RESOURCE_NOT_FOUND", "Resource not found.") });
    renderWithProviders(<AdmissionPage />, { user: fixtures.student, locale: "en", route: "/admission" });

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/dashboard"));
  });
});

describe("the print view (plan 6.2, the paper registration form)", () => {
  it("prints every field, the date, the photo box and both signature lines", async () => {
    const print = vi.fn();
    const original = window.print;
    window.print = print;
    mockApi({
      "GET /api/v1/me/admission": admissionView("CHANGES_REQUESTED", { submittedAt: "2026-11-10T04:00:00Z", submitCount: 1 }, completeForm),
    });
    renderWithProviders(<AdmissionPrintPage />, { user: fixtures.student, locale: "en", route: "/admission/print" });

    expect(await screen.findByText("SYMAM SCHOOL OF LAW")).toBeInTheDocument();
    expect(screen.getByText("Registration Form")).toBeInTheDocument();
    for (const label of [
      "Full name",
      "Father's name",
      "Mother's name",
      "Date of birth",
      "Email",
      "Gender",
      "University",
      "Session / Batch",
      "National ID number",
      "Mobile number",
      "Emergency mobile number",
      "Holding no. / hall / mess",
      "Present address",
      "Permanent address",
    ]) {
      expect(screen.getByText(label, { selector: "dt" })).toBeInTheDocument();
    }
    expect(screen.getByText("Rahim Uddin")).toBeInTheDocument();
    expect(screen.getByText("15/03/2001")).toBeInTheDocument();
    expect(screen.getByText(/✓ Male/)).toBeInTheDocument();
    expect(screen.getByText("Student's signature")).toBeInTheDocument();
    expect(screen.getByText("Officer signature")).toBeInTheDocument();
    expect(screen.getByText(/10 November 2026/)).toBeInTheDocument();

    await userEvent.setup().click(screen.getByRole("button", { name: "Print form" }));
    expect(print).toHaveBeenCalledTimes(1);
    window.print = original;
  });

  it("sends a student with no admission home", async () => {
    mockApi({ "GET /api/v1/me/admission": apiError(404, "RESOURCE_NOT_FOUND", "Resource not found.") });
    renderWithProviders(<AdmissionPrintPage />, { user: fixtures.student, locale: "en", route: "/admission/print" });

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/dashboard"));
  });
});
