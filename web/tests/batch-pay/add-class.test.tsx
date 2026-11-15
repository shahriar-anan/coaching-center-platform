import { fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BatchClasses } from "@/components/staff/offline/batch-classes";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { classRow, milon, rahim } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

type Call = { method: string; path: string; body: unknown };

/** The classes list answers from a value the test can change, so a class saved by the dialog shows on the next read. */
function classesApi() {
  let classes = [classRow({ id: "c1", subject: "Physics", status: "SCHEDULED", instructorId: "i1", instructorName: "Milon Hossain" })];
  const api = mockApi({
    "GET /api/v1/offline-batches/b1/classes": () => classes,
    "GET /api/v1/offline-instructors": [milon, rahim],
    "POST /api/v1/offline-batches/b1/classes": ({ body }: { body: unknown }) => {
      const input = body as { heldOn: string; startsAt: string; endsAt: string; subject: string; topic?: string; kind: string; instructorId?: string };
      const created = classRow({
        id: "c2",
        heldOn: input.heldOn,
        startsAt: `${input.startsAt}:00`,
        endsAt: `${input.endsAt}:00`,
        subject: input.subject,
        topic: input.topic ?? null,
        kind: input.kind as "EXAM",
        instructorId: input.instructorId ?? null,
        instructorName: input.instructorId ? "Milon Hossain" : null,
        status: "SCHEDULED",
      });
      classes = [...classes, created];
      return created;
    },
  });
  return api;
}

describe("Add class (plan 7.8)", () => {
  it("offers Add class to a user who can upload a routine, on a batch that is not archived", async () => {
    classesApi();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByRole("button", { name: "Add class" })).toBeInTheDocument();
  });

  it("hides Add class on an archived batch", async () => {
    classesApi();
    renderWithProviders(<BatchClasses batchId="b1" archived />, { user: fixtures.masterAdmin, locale: "en" });

    await screen.findByText("Physics");
    expect(screen.queryByRole("button", { name: "Add class" })).not.toBeInTheDocument();
  });

  it("hides Add class from a user who cannot upload a routine", async () => {
    classesApi();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.instructor, locale: "en" });

    await screen.findByText("Physics");
    expect(screen.queryByRole("button", { name: "Add class" })).not.toBeInTheDocument();
  });

  it("shows the same fields as the Edit class dialog, with the kind and the instructor", async () => {
    const user = userEvent.setup();
    classesApi();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click(await screen.findByRole("button", { name: "Add class" }));
    const dialog = await screen.findByRole("dialog");

    expect(within(dialog).getByRole("combobox", { name: "Kind" })).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Date")).toHaveValue(dhakaToday());
    expect(within(dialog).getByLabelText("Start")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("End")).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Subject/)).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Topic")).toBeInTheDocument();
    expect(within(dialog).getByRole("combobox", { name: "Instructor" })).toHaveValue("");
  });

  it("ends the class an hour after its start, then saves it and refreshes the list", async () => {
    const user = userEvent.setup();
    const api = classesApi();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click(await screen.findByRole("button", { name: "Add class" }));
    const dialog = await screen.findByRole("dialog");
    await user.selectOptions(within(dialog).getByRole("combobox", { name: "Kind" }), "EXAM");
    fireEvent.change(within(dialog).getByLabelText("Start"), { target: { value: "09:00" } });
    expect(within(dialog).getByLabelText("End")).toHaveValue("10:00");
    await user.type(within(dialog).getByLabelText(/Subject/), "Constitution");
    await user.type(within(dialog).getByLabelText("Topic"), "Part 2");
    await user.selectOptions(within(dialog).getByRole("combobox", { name: "Instructor" }), "i1");

    await user.click(within(dialog).getByRole("button", { name: "Add class" }));

    const post = (api.calls as Call[]).find((call) => call.method === "POST" && call.path === "/api/v1/offline-batches/b1/classes");
    expect(post?.body).toEqual({
      heldOn: dhakaToday(),
      startsAt: "09:00",
      endsAt: "10:00",
      subject: "Constitution",
      topic: "Part 2",
      kind: "EXAM",
      instructorId: "i1",
    });
    expect(await screen.findByText("Class added.")).toBeInTheDocument();
    expect(await screen.findByText("Constitution")).toBeInTheDocument();
  });

  it("does not save without a subject or a start time", async () => {
    const user = userEvent.setup();
    const api = classesApi();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click(await screen.findByRole("button", { name: "Add class" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Add class" }));

    expect(await within(dialog).findByText("Enter the subject.")).toBeInTheDocument();
    expect(within(dialog).getByText("Enter the start and end time.")).toBeInTheDocument();
    expect((api.calls as Call[]).some((call) => call.method === "POST")).toBe(false);
  });
});
