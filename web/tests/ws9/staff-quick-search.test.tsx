import { describe, expect, it } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StaffQuickSearch } from "@/components/shell/staff-quick-search";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const page = (content: unknown[]) => ({ content, page: 0, size: 5, totalElements: content.length, totalPages: 1 });

describe("StaffQuickSearch (U8)", () => {
  it("offers students and modules that match, each linking to its page", async () => {
    mockApi({
      "GET /api/v1/students": page([{ id: "s-1", fullName: "রহিম উদ্দিন", phone: "01700000001", studentCode: "STU-0001" }]),
      "GET /api/v1/live-exam-modules": page([{ id: "m-1", titleEn: "HSC Physics", titleBn: "এইচএসসি পদার্থ", status: "PUBLISHED" }]),
    });
    renderWithProviders(<StaffQuickSearch />, { user: fixtures.masterAdmin, locale: "en", route: "/staff" });

    await userEvent.type(screen.getByRole("combobox", { name: "Quick search" }), "ra");

    const student = await screen.findByRole("option", { name: /রহিম উদ্দিন/ });
    expect(student).toHaveAttribute("href", "/staff/students/s-1");
    const moduleHit = await screen.findByRole("option", { name: /HSC Physics/ });
    expect(moduleHit).toHaveAttribute("href", "/staff/modules/m-1");
    expect(moduleHit).toHaveTextContent("Published");
    expect(moduleHit).not.toHaveTextContent("PUBLISHED");
  });

  it("says so when nothing matches", async () => {
    mockApi({
      "GET /api/v1/students": page([]),
      "GET /api/v1/live-exam-modules": page([]),
    });
    renderWithProviders(<StaffQuickSearch />, { user: fixtures.masterAdmin, locale: "en", route: "/staff" });

    await userEvent.type(screen.getByRole("combobox", { name: "Quick search" }), "zz");
    await waitFor(() => expect(screen.getByText("No matches")).toBeInTheDocument());
  });

  it("does not search below two characters", async () => {
    let calls = 0;
    mockApi({
      "GET /api/v1/students": () => {
        calls += 1;
        return page([]);
      },
      "GET /api/v1/live-exam-modules": page([]),
    });
    renderWithProviders(<StaffQuickSearch />, { user: fixtures.masterAdmin, locale: "en", route: "/staff" });

    await userEvent.type(screen.getByRole("combobox", { name: "Quick search" }), "r");
    await new Promise((resolve) => window.setTimeout(resolve, 400));
    expect(calls).toBe(0);
  });

  it("moves focus to the field when / is pressed outside a text field", async () => {
    mockApi({ "GET /api/v1/students": page([]), "GET /api/v1/live-exam-modules": page([]) });
    renderWithProviders(
      <>
        <StaffQuickSearch />
        <button type="button">অন্য বোতাম</button>
      </>,
      { user: fixtures.masterAdmin, locale: "en", route: "/staff" },
    );
    screen.getByRole("button", { name: "অন্য বোতাম" }).focus();
    await userEvent.keyboard("/");
    expect(screen.getByRole("combobox", { name: "Quick search" })).toHaveFocus();
  });
});
