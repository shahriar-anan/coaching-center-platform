import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BatchDetail } from "@/components/staff/offline/batch-detail";
import type { BatchStudent } from "@/lib/api/exam/offline-admin";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { batch } from "./fixtures";

vi.mock("@/i18n/messages", async (importOriginal) => {
  const mod = await importOriginal<typeof import("@/i18n/messages")>();
  const { withBatchPay } = await import("./catalog");
  return withBatchPay(mod);
});

function student(overrides: Partial<BatchStudent> & { admissionId: string; fullName: string }): BatchStudent {
  return {
    studentId: `stu-${overrides.admissionId}`,
    mobile: "01711000000",
    status: "ONBOARDED",
    roll: null,
    ...overrides,
  };
}

function setup(students: BatchStudent[]) {
  mockApi({
    "GET /api/v1/offline-batches/b1": batch,
    "GET /api/v1/offline-batches/b1/students": students,
  });
  renderWithProviders(<BatchDetail id="b1" />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/offline-batches/b1?tab=students" });
}

describe("students tab roll numbers (plan 7.4)", () => {
  it("shows a Roll column with each student's roll", async () => {
    setup([student({ admissionId: "a1", fullName: "Rahim Uddin", roll: "192001" })]);

    // The table shows on a wide screen; the cards on a phone repeat the same data, so the check is on the table.
    const table = await screen.findByRole("table");
    expect(within(table).getByRole("columnheader", { name: "Roll" })).toBeInTheDocument();
    const row = within(table).getByRole("link", { name: "Rahim Uddin" }).closest("tr");
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getByText("192001")).toBeInTheDocument();
  });

  it("warns about onboarded students with no roll and links to the first of them", async () => {
    setup([
      student({ admissionId: "a1", fullName: "Rahim Uddin", roll: "192001" }),
      student({ admissionId: "a2", fullName: "Karim Ali", roll: null }),
      student({ admissionId: "a3", fullName: "Salma Begum" }),
      // Not onboarded yet, so it is not counted.
      student({ admissionId: "a4", fullName: "Nasir Ahmed", status: "READY" }),
    ]);

    expect(await screen.findByText("2 students have no roll number. Results cannot be matched to them.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open Karim Ali" })).toHaveAttribute("href", "/staff/admissions/a2");
  });

  it("uses the singular wording for one student", async () => {
    setup([
      student({ admissionId: "a1", fullName: "Rahim Uddin", roll: "192001" }),
      student({ admissionId: "a2", fullName: "Karim Ali", roll: null }),
    ]);

    expect(await screen.findByText("1 student has no roll number. Results cannot be matched to them.")).toBeInTheDocument();
  });

  it("shows no warning when every onboarded student has a roll", async () => {
    setup([student({ admissionId: "a1", fullName: "Rahim Uddin", roll: "192001" })]);

    await screen.findAllByRole("link", { name: "Rahim Uddin" });
    expect(screen.queryByText(/no roll number/)).not.toBeInTheDocument();
  });
});
