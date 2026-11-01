import { useState } from "react";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { StudentPicker, type FoundStudent } from "@/components/staff/student-picker";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import "./copy-setup";

const rafi: FoundStudent = { id: "s1", fullName: "Rafi Ahmed", phone: "01711000001", email: null, studentCode: "STU-001" };
const rahim: FoundStudent = { id: "s2", fullName: "Rahim Uddin", phone: "01711000002", email: null, studentCode: "STU-002" };

/** A picker that keeps its list, as a screen does. `onChange` is a spy so a test can read every change. */
function Harness({ initial = [], max, onChange }: { initial?: FoundStudent[]; max?: number; onChange?: (next: FoundStudent[]) => void }) {
  const [value, setValue] = useState<FoundStudent[]>(initial);
  return (
    <StudentPicker
      value={value}
      max={max}
      onChange={(next) => {
        onChange?.(next);
        setValue(next);
      }}
    />
  );
}

function studentsApi() {
  return mockApi({
    "GET /api/v1/students": { content: [rafi, rahim], page: 0, size: 10, totalElements: 2, totalPages: 1 },
  });
}

describe("StudentPicker", () => {
  it("finds a student, adds them to the list, and shows them with a Remove button", async () => {
    studentsApi();
    const onChange = vi.fn();
    renderWithProviders(<Harness onChange={onChange} />, { user: fixtures.masterAdmin, locale: "en" });

    await userEvent.type(screen.getByRole("searchbox", { name: "Search" }), "Ra");
    await userEvent.click((await screen.findAllByRole("button", { name: "Add to list" }))[0]);

    expect(onChange).toHaveBeenLastCalledWith([rafi]);
    expect(screen.getByRole("button", { name: "Remove Rafi Ahmed from the list" })).toBeInTheDocument();
    expect(screen.getByText("1 in the list")).toBeInTheDocument();
    expect(screen.getByText("In the list")).toBeInTheDocument();
  });

  it("removes a student from the list", async () => {
    studentsApi();
    const onChange = vi.fn();
    renderWithProviders(<Harness initial={[rafi]} onChange={onChange} />, { user: fixtures.masterAdmin, locale: "en" });

    await userEvent.click(screen.getByRole("button", { name: "Remove Rafi Ahmed from the list" }));

    expect(onChange).toHaveBeenLastCalledWith([]);
    expect(screen.getByText("No students yet. Find a student and add them to the list.")).toBeInTheDocument();
  });

  it("stops adding at the maximum, says why, and shows the count against it", async () => {
    studentsApi();
    renderWithProviders(<Harness initial={[rafi]} max={1} />, { user: fixtures.masterAdmin, locale: "en" });

    await userEvent.type(screen.getByRole("searchbox", { name: "Search" }), "Ra");
    await screen.findByText("Rahim Uddin");

    expect(screen.getByRole("button", { name: "Add to list" })).toBeDisabled();
    expect(screen.getByText("You can add up to 1 students. Remove one to add another.")).toBeInTheDocument();
    expect(screen.getByText("1 of 1 in the list")).toBeInTheDocument();
  });

  it("keeps Add enabled below the maximum", async () => {
    studentsApi();
    renderWithProviders(<Harness max={2} />, { user: fixtures.masterAdmin, locale: "en" });

    await userEvent.type(screen.getByRole("searchbox", { name: "Search" }), "Ra");

    const [add] = await screen.findAllByRole("button", { name: "Add to list" });
    expect(add).toBeEnabled();
    expect(screen.queryByText(/You can add up to/)).not.toBeInTheDocument();
  });
});
