import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BatchClasses } from "@/components/staff/offline/batch-classes";
import { RoutinePreview } from "@/components/staff/offline/routine-preview";
import { RoutineUploadDialog } from "@/components/staff/offline/routine-upload-dialog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { batch, classRow, milon, previewRoutine, rahim, ROUTINE_ID } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

const UPLOAD = "/api/v1/offline-batches/b1/routines";

/** A file that passes the PDF check before upload: the name, the type and the %PDF- mark. */
function pdf(): File {
  return new File(["%PDF-1.7\n"], "20th BJS Foundation Batch- NOVEMBER.pdf", { type: "application/pdf" });
}

/** The preview with a third row dated before the day the routine counts from. */
function withEarlierRow() {
  const routine = previewRoutine();
  return previewRoutine({
    rowCount: 3,
    rows: [
      ...routine.rows,
      {
        ...routine.rows[1],
        index: 2,
        date: "2026-10-28",
        day: "Wednesday",
        subject: "Contract Act",
        flags: [{ code: "DATE_PAST", reason: "This date has passed." }],
      },
    ],
  });
}

function previewRoutes(routine = previewRoutine()) {
  return {
    [`GET /api/v1/offline-routines/${ROUTINE_ID}`]: routine,
    "GET /api/v1/offline-batches/b1": batch,
    "GET /api/v1/offline-instructors": [milon, rahim],
  };
}

describe("a routine counts from the day it is uploaded", () => {
  it("uploads only the file: there is no month to choose", async () => {
    const user = userEvent.setup();
    const api = mockApi({ [`POST ${UPLOAD}`]: previewRoutine() });
    renderWithProviders(<RoutineUploadDialog batchId="b1" open onOpenChange={() => undefined} replacing />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });

    expect(screen.getByText(/The new routine counts from today: it replaces the classes from today onward/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/Month/)).not.toBeInTheDocument();
    await user.upload(screen.getByLabelText(/Routine PDF/), pdf());
    await user.click(screen.getByRole("button", { name: "Upload and check" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "POST" && call.path === UPLOAD)).toBe(true));
    const body = api.calls.find((call) => call.path === UPLOAD)?.body;
    expect(body instanceof FormData ? body.get("month") : "not form data").toBeNull();
  });

  it("puts the rows from that day first and folds the earlier dates below", async () => {
    const user = userEvent.setup();
    mockApi(previewRoutes(withEarlierRow()));
    renderWithProviders(<RoutinePreview batchId="b1" routineId={ROUTINE_ID} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("Counts from 1 November, the day it was uploaded")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Earlier dates (1)" })).toBeInTheDocument();
    expect(screen.queryByText("Contract Act")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Show" }));
    expect(screen.getByText("Contract Act")).toBeInTheDocument();
    expect(screen.getByText("This date has passed: it is added as an earlier class.")).toBeInTheDocument();
  });
});

describe("changing the instructor", () => {
  it("on the preview: says who the routine names, and a name not in the list is refused (24 Nov)", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      ...previewRoutes(),
      [`PATCH /api/v1/offline-routines/${ROUTINE_ID}/rows/1`]: previewRoutine(),
    });
    renderWithProviders(<RoutinePreview batchId="b1" routineId={ROUTINE_ID} />, { user: fixtures.masterAdmin, locale: "en" });

    // The second row has an instructor (Rahim Sir): changing it changes that row only.
    await user.click((await screen.findAllByRole("button", { name: "Change" }))[1]);
    expect(screen.getByText("Instructor as per routine")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Rahim Sir/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /Milon Sir/ })).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: "Someone else" }));
    await user.type(screen.getByLabelText("Instructor name"), "Kamal Hossain");
    expect(screen.getAllByText(/Kamal Hossain is not in the instructor list/).length).toBeGreaterThan(0);
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/Kamal Hossain is not in the instructor list/);
    expect(api.calls.some((call) => call.method === "POST" && call.path === "/api/v1/offline-instructors")).toBe(false);
    expect(api.calls.some((call) => call.path.endsWith("/rows/1"))).toBe(false);

    // A name that is in the list picks that instructor.
    await user.clear(screen.getByLabelText("Instructor name"));
    await user.type(screen.getByLabelText("Instructor name"), "Rahim Sir");
    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(api.calls.find((call) => call.path.endsWith("/rows/1"))?.body).toEqual({ instructorId: "i2" }));
  });

  it("on the Classes tab: the same dialog, with No instructor allowed", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/offline-batches/b1/classes": [
        classRow({ id: "c1", status: "SCHEDULED", instructorId: "i1", instructorName: "Milon Hossain", instructorText: "Milon Sir" }),
        classRow({ id: "c2", status: "SCHEDULED", subject: "Chemistry", instructorId: "i2", instructorName: "Rahim Sir", instructorText: "Rahim Sir" }),
      ],
      "GET /api/v1/offline-instructors": [milon, rahim],
      "PATCH /api/v1/offline-classes/c1": classRow({ id: "c1", instructorId: "i2", instructorName: "Rahim Sir" }),
    });
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-batches/b1?tab=classes" });

    // The routine wrote "Milon Sir"; the class is taught by Milon Hossain, so the routine's name shows under it.
    expect(await screen.findByText("As per routine: Milon Sir")).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: "Change" })[0]);
    expect(screen.getByRole("radio", { name: "No instructor" })).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: /Rahim Sir/ }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() =>
      expect(api.calls.find((call) => call.method === "PATCH" && call.path === "/api/v1/offline-classes/c1")?.body).toEqual({
        version: 3,
        instructorId: "i2",
      }),
    );
  });
});
