import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ApplyPage } from "@/components/public-admission/apply-page";
import { ReopenPage } from "@/components/public-admission/reopen-page";
import type { PublicBatch } from "@/lib/api/exam/public-admission";
import { admissionView, completeForm } from "../offline-admission/admission-fixture";
import { ErrorBody, fixtures, mockApi, renderWithProviders } from "../helpers";

const BATCH: PublicBatch = {
  id: "b-2",
  nameEn: "Batch 4",
  nameBn: "ব্যাচ ৪",
  startsOn: null,
  endsOn: "2027-05-31",
  monthlyFee: 2500,
  descriptionEn: null,
  descriptionBn: null,
  photoUrl: null,
  admissionsOpen: true,
  routineAvailable: false,
};

/** The batch route answers the batch the URL names. */
function batchRoutes() {
  return { "GET /api/v1/public/offline-batches/b-2": BATCH };
}

describe("the apply form takes its batch from the card (plan P1)", () => {
  it("shows the batch from the URL at the top, with no batch list in the form", async () => {
    mockApi(batchRoutes());
    renderWithProviders(<ApplyPage batchId="b-2" />, { locale: "en", route: "/admission/apply/b-2" });

    const strip = within(await screen.findByRole("region", { name: "Batch you are applying for" }));
    expect(strip.getByText("Batch 4", { selector: "p" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Change batch" })).toHaveAttribute("href", "/admission");
    expect(screen.queryByRole("combobox")).toBeNull();
  });

  it("starts a signed-in student's application for the batch in the URL", async () => {
    const api = mockApi({
      ...batchRoutes(),
      "POST /api/v1/me/admission/apply": admissionView("AWAITING_FORM", {}, completeForm),
    });
    renderWithProviders(<ApplyPage batchId="b-2" />, { user: fixtures.student, locale: "en", route: "/admission/apply/b-2" });

    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/me/admission/apply")).toBe(true));
    expect(api.calls.find((call) => call.path === "/api/v1/me/admission/apply")?.body).toEqual({ batchId: "b-2" });
    expect(await screen.findByRole("textbox", { name: /Full name/ })).toHaveValue("Rahim Uddin");
  });
});

describe("the form has no email step (plan P10)", () => {
  it("shows the email as a form field and goes straight to the submit confirmation", async () => {
    const user = userEvent.setup();
    const api = mockApi(batchRoutes());
    renderWithProviders(<ApplyPage batchId="b-2" />, { locale: "en", route: "/admission/apply/b-2" });

    expect(await screen.findByRole("textbox", { name: /Email/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /code|verify|confirm your email/i })).toBeNull();
    await user.click(screen.getByRole("button", { name: "Submit" }));
    expect(await screen.findByRole("dialog", { name: "Submit this form?" })).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });
});

describe("the confirmation after the form is sent", () => {
  it("tells a visitor which email will hear back, after the application is started from the email", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      ...batchRoutes(),
      "POST /api/v1/public/offline-applications": { token: "tok-1", admission: admissionView("AWAITING_FORM") },
      "PUT /api/v1/public/offline-applications/tok-1/form": admissionView("AWAITING_FORM", {}, completeForm),
      "POST /api/v1/public/offline-applications/tok-1/submit": admissionView("SUBMITTED", { submittedAt: "2026-11-13T04:00:00Z" }, completeForm),
    });
    renderWithProviders(<ApplyPage batchId="b-2" />, { locale: "en", route: "/admission/apply/b-2" });

    await user.type(await screen.findByRole("textbox", { name: /Email/ }), "rahim@example.com");
    await user.click(screen.getByRole("button", { name: "Submit" }));
    const dialog = await screen.findByRole("dialog", { name: "Submit this form?" });
    await user.click(within(dialog).getByRole("button", { name: "Submit" }));

    expect(await screen.findByText("Your application for Batch 4 was sent. We will email rahim@example.com when it is reviewed.")).toBeInTheDocument();
    expect(api.calls.find((call) => call.path === "/api/v1/public/offline-applications")?.body).toEqual({
      batchId: "b-2",
      email: "rahim@example.com",
    });
    expect(api.calls.some((call) => call.path === "/api/v1/public/offline-applications/tok-1/submit")).toBe(true);
  });

  it("gives a signed-in student the same confirmation, with the email on the account", async () => {
    const user = userEvent.setup();
    mockApi({
      ...batchRoutes(),
      "POST /api/v1/me/admission/apply": admissionView("AWAITING_FORM", {}, completeForm),
      "POST /api/v1/me/admission/submit": admissionView("SUBMITTED", { submittedAt: "2026-11-13T04:00:00Z" }, completeForm),
    });
    renderWithProviders(<ApplyPage batchId="b-2" />, { user: fixtures.student, locale: "en", route: "/admission/apply/b-2" });

    await user.click(await screen.findByRole("button", { name: "Submit" }));
    const dialog = await screen.findByRole("dialog", { name: "Submit this form?" });
    await user.click(within(dialog).getByRole("button", { name: "Submit" }));

    expect(await screen.findByText("Your application for Batch 4 was sent. We will email rahim@example.com when it is reviewed.")).toBeInTheDocument();
  });
});

describe("the reopen link (plan Q4)", () => {
  it("shows the form again with the office's reason", async () => {
    mockApi({
      "GET /api/v1/public/offline-applications/reopen/link-1": {
        token: "tok-9",
        admission: admissionView("CHANGES_REQUESTED", { changesReason: "Fix the photo." }, completeForm),
      },
    });
    renderWithProviders(<ReopenPage linkToken="link-1" />, { locale: "en", route: "/admission/reopen/link-1" });

    expect(await screen.findByText("Fix the photo.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /Full name/ })).toHaveValue("Rahim Uddin");
  });

  it("says the link is no longer valid when the API refuses it", async () => {
    mockApi({
      "GET /api/v1/public/offline-applications/reopen/old": new ErrorBody(410, { code: "LINK_EXPIRED", message: "Expired." }),
    });
    renderWithProviders(<ReopenPage linkToken="old" />, { locale: "en", route: "/admission/reopen/old" });

    expect(await screen.findByText(/This link is no longer valid/)).toBeInTheDocument();
  });
});
