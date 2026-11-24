import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BatchClasses } from "@/components/staff/offline/batch-classes";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { classRow, milon, rahim } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

function setup() {
  return mockApi({
    "GET /api/v1/offline-batches/b1/classes": [
      classRow({ id: "c1", subject: "Physics", status: "HELD", instructorId: "i1", instructorName: "Milon Hossain" }),
      classRow({ id: "c2", subject: "Chemistry", status: "PAID", instructorId: "i2", instructorName: "Rahim Sir" }),
      classRow({ id: "c3", subject: "Biology", status: "SCHEDULED", instructorId: "i1", instructorName: "Milon Hossain" }),
    ],
    "GET /api/v1/offline-instructors": [milon, rahim],
  });
}

/** The list item that holds a class, found by its subject. */
async function rowOf(subject: string): Promise<HTMLElement> {
  const row = (await screen.findByText(subject)).closest("li");
  if (!row) {
    throw new Error(`no row for ${subject}`);
  }
  return row;
}

describe("classes tab (plan 8.1; 24 Nov: a class carries no pay)", () => {
  it("offers Edit on a held class, and no payment, not-taken or amount", async () => {
    setup();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    const held = await rowOf("Physics");
    expect(within(held).getByText("Held")).toBeInTheDocument();
    expect(within(held).getByRole("button", { name: "Edit" })).toBeInTheDocument();
    expect(within(held).queryByRole("button", { name: "Record payment" })).not.toBeInTheDocument();
    expect(within(held).queryByRole("button", { name: "Mark not taken" })).not.toBeInTheDocument();
    expect(screen.queryByText("Amount")).not.toBeInTheDocument();
  });

  it("keeps a class paid before 24 Nov read only", async () => {
    setup();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    const paid = await rowOf("Chemistry");
    expect(within(paid).getByText("Paid")).toBeInTheDocument();
    expect(within(paid).getByText("Read only")).toBeInTheDocument();
    expect(within(paid).queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  });

  it("does not offer the rate for a class in the Edit dialog", async () => {
    const user = userEvent.setup();
    setup();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click(within(await rowOf("Biology")).getByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText(/Subject/)).toHaveValue("Biology");
    expect(within(dialog).queryByLabelText(/Rate for this class/)).not.toBeInTheDocument();
  });
});

describe("deleting a class (16 Nov)", () => {
  it("offers Delete on an open class but not a paid one, and deletes after the confirmation", async () => {
    const user = userEvent.setup();
    const routes = mockApi({
      "GET /api/v1/offline-batches/b1/classes": [
        classRow({ id: "c3", subject: "Biology", status: "SCHEDULED", instructorId: "i1", instructorName: "Milon Hossain" }),
        classRow({ id: "c2", subject: "Chemistry", status: "PAID", instructorId: "i2", instructorName: "Rahim Sir" }),
      ],
      "GET /api/v1/offline-instructors": [milon, rahim],
      "DELETE /api/v1/offline-classes/c3": undefined,
    });
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.masterAdmin, locale: "en" });

    const paid = await rowOf("Chemistry");
    expect(within(paid).queryByRole("button", { name: "Delete" })).not.toBeInTheDocument();
    const open = await rowOf("Biology");
    await user.click(within(open).getByRole("button", { name: "Delete" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/uploaded routine PDF does not change/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Delete class" }));

    await vi.waitFor(() =>
      expect(routes.calls.some((call) => call.method === "DELETE" && call.path === "/api/v1/offline-classes/c3")).toBe(true),
    );
  });

  it("does not offer Delete to a role without routine rights", async () => {
    setup();
    renderWithProviders(<BatchClasses batchId="b1" />, { user: fixtures.instructor, locale: "en" });
    const open = await rowOf("Biology");
    expect(within(open).queryByRole("button", { name: "Delete" })).not.toBeInTheDocument();
  });
});
