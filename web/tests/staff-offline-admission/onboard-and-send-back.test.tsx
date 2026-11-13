import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AdmissionDetailView } from "@/components/staff/offline/admission-detail";
import { SendBackDialog } from "@/components/staff/offline/send-back-dialog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ADMISSION_ID, detail, page } from "./fixtures";

function readyRoutes() {
  return mockApi({
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: detail({ state: "READY", readyProblems: [] }),
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/live-exams": page([]),
    "GET /api/v1/live-exam-modules": page([]),
  });
}

describe("onboarding is the Master Admin's alone (plan D2)", () => {
  it("shows the Onboard button to the Master Admin on a ready admission", async () => {
    readyRoutes();
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    expect(await screen.findByRole("button", { name: "Onboard" })).toBeInTheDocument();
  });

  it("does not show the Onboard button to a System Admin, who is told the Master Admin will onboard", async () => {
    readyRoutes();
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.systemAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}`,
    });

    expect(await screen.findByText("Waiting for the Master Admin to onboard.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Onboard" })).not.toBeInTheDocument();
  });
});

describe("send back needs a reason (plan 6.3, principle 6)", () => {
  it("does not send an empty reason, and says why", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn(async () => undefined);
    renderWithProviders(<SendBackDialog open onOpenChange={() => undefined} onSubmit={onSubmit} />, {
      user: fixtures.systemAdmin,
      locale: "en",
    });

    await user.click(screen.getByRole("button", { name: "Send back" }));

    expect(await screen.findByText("Give a reason before sending the form back.")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("fills the reason from a one-tap reason, then sends the text the student will read", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn<(reason: string) => Promise<void>>(async () => undefined);
    renderWithProviders(<SendBackDialog open onOpenChange={() => undefined} onSubmit={onSubmit} />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });

    await user.click(screen.getByRole("button", { name: "NID wrong" }));
    expect(screen.getByRole("textbox")).toHaveValue(
      "The NID number is wrong. Please check it against your card.",
    );

    await user.click(screen.getByRole("button", { name: "Send back" }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0]?.[0]).toContain("NID number is wrong");
  });

  it("accepts free text in place of a one-tap reason", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn<(reason: string) => Promise<void>>(async () => undefined);
    renderWithProviders(<SendBackDialog open onOpenChange={() => undefined} onSubmit={onSubmit} />, {
      user: fixtures.systemAdmin,
      locale: "en",
    });

    await user.type(screen.getByRole("textbox"), "Hall number is missing");
    await user.click(screen.getByRole("button", { name: "Send back" }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith("Hall number is missing"));
  });
});

describe("the history is the timeline the API builds (plan 6.3)", () => {
  it("shows each step in the order the API sends, with who did it and the detail it gives", async () => {
    mockApi({
      [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: detail({ state: "ONBOARDED", onboardedAt: "2026-11-12T04:00:00Z" }),
      [`GET /api/v1/offline-admissions/${ADMISSION_ID}/history`]: [
        { at: "2026-11-12T04:00:00Z", action: "OFFLINE_ADMISSION_ONBOARDED", actorName: "Master Admin", detail: null },
        { at: "2026-11-11T04:00:00Z", action: "OFFLINE_FEE_PAYMENT_RECORDED", actorName: "Staff Member", detail: "BDT 3000.00 CASH" },
        { at: "2026-11-10T04:00:00Z", action: "OFFLINE_ADMISSION_CHANGES_REQUESTED", actorName: null, detail: "NID wrong" },
        { at: "2026-11-09T04:00:00Z", action: "SOMETHING_NEW", actorName: null, detail: null },
      ],
      "GET /api/v1/offline-batches": [],
      "GET /api/v1/live-exams": page([]),
      "GET /api/v1/live-exam-modules": page([]),
    });
    renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
      user: fixtures.masterAdmin,
      locale: "en",
      route: `/staff/admissions/${ADMISSION_ID}?tab=history`,
    });

    const items = within(await screen.findByRole("list", { name: "History" })).getAllByRole("listitem");
    expect(items).toHaveLength(4);
    expect(items[0]).toHaveTextContent("Onboarded");
    expect(items[0]).toHaveTextContent("By Master Admin");
    expect(items[1]).toHaveTextContent("Payment recorded");
    expect(items[1]).toHaveTextContent("Staff Member");
    expect(items[1]).toHaveTextContent("BDT 3000.00 CASH");
    expect(items[2]).toHaveTextContent("Sent back");
    expect(items[2]).toHaveTextContent("NID wrong");
    expect(items[3]).toHaveTextContent("Changed");
  });
});
