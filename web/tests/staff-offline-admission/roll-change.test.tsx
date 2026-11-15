import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AdmissionDetailView } from "@/components/staff/offline/admission-detail";
import { fixtures, mockApi, renderWithProviders, ErrorBody } from "../helpers";
import { ADMISSION_ID, detail, page } from "./fixtures";

type Call = { method: string; path: string; body: unknown };

/** The onboarded admission's Overview, with the roll number it has. */
function setup(rollNo: string | null = "192001", routes: Record<string, unknown> = {}) {
  return mockApi({
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: detail({ state: "ONBOARDED", rollNo, onboardedAt: "2026-11-12T04:00:00Z" }),
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}/history`]: [],
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/live-exams": page([]),
    "GET /api/v1/live-exam-modules": page([]),
    ...routes,
  });
}

async function openChangeRoll(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: "Change" }));
  return screen.findByRole("dialog");
}

describe("Change roll number (plan 7.3)", () => {
  it("shows the roll on the Overview, and Change to the Master Admin only", async () => {
    setup("192001");
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    expect(await screen.findByText("Roll number")).toBeInTheDocument();
    expect(screen.getByText("192001")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Change" })).toBeInTheDocument();
  });

  it("does not offer Change to a system admin, who cannot onboard", async () => {
    setup("192001");
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    expect(await screen.findByText("192001")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Change" })).not.toBeInTheDocument();
  });

  it("keeps Save off for a roll that is not 4 to 12 digits, and says why", async () => {
    const user = userEvent.setup();
    setup("192001");
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    const dialog = await openChangeRoll(user);
    const input = within(dialog).getByLabelText(/Roll number/);
    expect(input).toHaveValue("192001");
    expect(within(dialog).getByRole("button", { name: "Save roll number" })).toBeDisabled();

    await user.clear(input);
    await user.type(input, "123");
    expect(within(dialog).getByText("Use 4 to 12 digits.")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Save roll number" })).toBeDisabled();

    await user.clear(input);
    await user.type(input, "19200x");
    expect(within(dialog).getByText("Use 4 to 12 digits.")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Save roll number" })).toBeDisabled();
  });

  it("explains that published results keep the old roll", async () => {
    const user = userEvent.setup();
    setup("192001");
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    const dialog = await openChangeRoll(user);
    expect(within(dialog).getByText(/Published results keep the old roll number/)).toBeInTheDocument();
  });

  it("sends a valid roll with PATCH and refreshes the admission", async () => {
    const user = userEvent.setup();
    const api = setup("192001", {
      [`PATCH /api/v1/offline-admissions/${ADMISSION_ID}/roll`]: detail({ state: "ONBOARDED", rollNo: "192045" }),
    });
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    const dialog = await openChangeRoll(user);
    const input = within(dialog).getByLabelText(/Roll number/);
    await user.clear(input);
    await user.type(input, "192045");
    await user.click(within(dialog).getByRole("button", { name: "Save roll number" }));

    await waitFor(() => {
      const patch = (api.calls as Call[]).find((call) => call.method === "PATCH");
      expect(patch?.path).toBe(`/api/v1/offline-admissions/${ADMISSION_ID}/roll`);
      expect(patch?.body).toEqual({ rollNo: "192045" });
    });
    expect(await screen.findByText("Roll number changed.")).toBeInTheDocument();
  });

  it("shows the API's field error for the roll under its input", async () => {
    const user = userEvent.setup();
    setup("192001", {
      [`PATCH /api/v1/offline-admissions/${ADMISSION_ID}/roll`]: () =>
        new ErrorBody(409, {
          code: "VALIDATION_FAILED",
          message: "Check the highlighted fields.",
          fieldErrors: [{ field: "rollNo", message: "Another student already has this roll number." }],
        } as unknown as { code: string; message: string }),
    });
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    const dialog = await openChangeRoll(user);
    const input = within(dialog).getByLabelText(/Roll number/);
    await user.clear(input);
    await user.type(input, "192045");
    await user.click(within(dialog).getByRole("button", { name: "Save roll number" }));

    expect(await within(dialog).findByText("Another student already has this roll number.")).toBeInTheDocument();
    expect(input).toHaveAttribute("aria-invalid", "true");
  });
});
