import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ModuleDetails } from "@/components/staff/catalog/module-details";
import type { StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const CATEGORY = "cccccccc-0000-0000-0000-000000000001";
const MODULE_ID = "aaaaaaaa-0000-0000-0000-000000000009";

// A package that is already published, with a finished final save.
const published: StaffModule = {
  id: MODULE_ID,
  categoryId: CATEGORY,
  titleEn: "Published package",
  titleBn: "প্রকাশিত প্যাকেজ",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "PUBLISHED",
  finallySaved: true,
  position: 1,
  price: 2500,
  pendingChanges: false,
};

describe("editing a published package's details", () => {
  it("after a title save, the Master Admin is offered Publish changes on the same screen", async () => {
    const staged = { ...published, titleEn: "Published package (edited)", pendingChanges: true };
    mockApi({
      [`PATCH /api/v1/live-exam-modules/${MODULE_ID}`]: staged,
      [`GET /api/v1/live-exam-modules/${MODULE_ID}/pending-changes`]: {
        revisionId: "rev-1",
        titleEn: "Published package (edited)",
        changes: [],
        impact: { notifiesStudents: false },
      },
    });
    const user = userEvent.setup();
    renderWithProviders(<ModuleDetails module={published} />, { user: fixtures.masterAdmin, locale: "en" });

    const title = screen.getByLabelText(/English title/);
    await user.clear(title);
    await user.type(title, "Published package (edited)");
    const form = title.closest("form") as HTMLElement;
    await user.click(form.querySelector("button[type=submit]") as HTMLElement);

    await waitFor(() => expect(screen.getByRole("button", { name: "Publish changes" })).toBeInTheDocument());
  });

  it("a System Admin gets no Publish changes button on the same screen, only the waiting note", async () => {
    const staged = { ...published, pendingChanges: true };
    renderWithProviders(<ModuleDetails module={staged} />, { user: fixtures.systemAdmin, locale: "en" });
    expect(screen.queryByRole("button", { name: "Publish changes" })).not.toBeInTheDocument();
  });
});
