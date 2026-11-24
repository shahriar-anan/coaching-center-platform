import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { InstructorList } from "@/components/staff/instructor-pay/instructor-list";
import { fixtures, apiError, mockApi, renderWithProviders } from "../helpers";
import { BATCH, MILON } from "./fixtures";

const REFUSAL = "3 offline classes still use this instructor (not cancelled), so the instructor cannot be archived.";

describe("offline instructors page", () => {
  it("shows the API's message when archive is refused because classes are live", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/offline-instructors": [MILON],
      "GET /api/v1/offline-instructor-payments/figures": {},
      "POST /api/v1/offline-instructors/ins-milon/archive": apiError(409, "VALIDATION_FAILED", REFUSAL),
    });
    renderWithProviders(<InstructorList />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/offline-instructors",
    });

    await screen.findAllByRole("button", { name: "Archive" });
    await user.click(screen.getAllByRole("button", { name: "Archive" })[0] as HTMLElement);
    const dialog = await screen.findByRole("dialog", { name: "Archive Milon?" });
    await user.click(within(dialog).getByRole("button", { name: "Archive" }));

    expect(await within(dialog).findByText(REFUSAL)).toBeInTheDocument();
    // The archive was asked for, and the dialog stayed open with the refusal.
    expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith("/archive"))).toBe(true);
    expect(screen.getByRole("dialog", { name: "Archive Milon?" })).toBeInTheDocument();
  });

  it("shows each instructor's batches, what they were paid this month and their last payment (24 Nov)", async () => {
    mockApi({
      "GET /api/v1/offline-instructors": [MILON],
      "GET /api/v1/offline-instructor-payments/figures": {
        [MILON.id]: { paidThisMonth: 2500, lastPaidOn: "2026-11-20", batches: [{ id: BATCH.id, nameEn: BATCH.nameEn, nameBn: BATCH.nameBn }] },
      },
    });
    renderWithProviders(<InstructorList />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: "/staff/offline-instructors",
    });

    expect((await screen.findAllByRole("link", { name: "Milon" })).length).toBeGreaterThan(0);
    expect((await screen.findAllByText(BATCH.nameEn)).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/2,500/).length).toBeGreaterThan(0);
    expect(screen.queryByText("Owed now")).not.toBeInTheDocument();
  });
});
