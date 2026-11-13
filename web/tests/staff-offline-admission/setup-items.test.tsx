import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import SetupPage from "@/app/(staff)/staff/admissions/[id]/setup/page";
import type { AdmissionDetail, AdmissionItem } from "@/lib/api/exam/offline-admin";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { categoryTree, packageRow, pageOf } from "../staff-admin/picker-data";
import { ADMISSION_ID, detail, page } from "./fixtures";

const DETAIL = `GET /api/v1/offline-admissions/${ADMISSION_ID}`;
const ITEMS = `POST /api/v1/offline-admissions/${ADMISSION_ID}/items`;

/** The package list the picker reads: three published packages. */
const PACKAGES = [packageRow(1), packageRow(2), packageRow(3)];

/** A package the admission already holds. */
const HELD_PACKAGE: AdmissionItem = {
  id: "item-pkg-1",
  scope: "MODULE",
  examId: null,
  moduleId: "p1",
  titleEn: "Package 1",
  titleBn: "প্যাকেজ ১",
  grantId: null,
  addedAt: "2026-11-10T04:00:00Z",
};

function routes(admission: AdmissionDetail) {
  return mockApi({
    [DETAIL]: admission,
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}/history`]: [],
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/live-exams": page([]),
    "GET /api/v1/live-exam-modules": (req: { url: URL }) => pageOf(req.url, PACKAGES),
    "GET /api/v1/live-exam-categories": categoryTree,
    [ITEMS]: { admission: admission, overlaps: [], alreadyHeld: false },
  });
}

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

function renderSetupRoute() {
  renderWithProviders(<SetupPage params={resolved({ id: ADMISSION_ID })} />, {
    user: fixtures.systemAdmin,
    locale: "en",
    route: `/staff/admissions/${ADMISSION_ID}/setup`,
  });
}

describe("adding exams and packages on the setup", () => {
  it("ticks two packages in the picker and adds each one with its own request", async () => {
    const api = routes(detail({ state: "SUBMITTED", items: [] }));
    renderSetupRoute();

    await userEvent.click(await screen.findByRole("button", { name: "+ Add" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /Package 1/ }));
    await userEvent.click(within(dialog).getByRole("checkbox", { name: /Package 2/ }));
    await userEvent.click(within(dialog).getByRole("button", { name: "Add 2" }));

    await waitFor(() => expect(api.calls.filter((call) => call.method === "POST" && call.path === `/api/v1/offline-admissions/${ADMISSION_ID}/items`)).toHaveLength(2));
    const posts = api.calls.filter((call) => call.method === "POST" && call.path === `/api/v1/offline-admissions/${ADMISSION_ID}/items`);
    expect(posts.map((call) => call.body)).toEqual([
      { scope: "MODULE", moduleId: "p1" },
      { scope: "MODULE", moduleId: "p2" },
    ]);
  });

  it("shows the packages the admission already holds ticked and disabled, and does not add them again", async () => {
    const api = routes(detail({ state: "SUBMITTED", items: [HELD_PACKAGE] }));
    renderSetupRoute();

    await userEvent.click(await screen.findByRole("button", { name: "+ Add" }));
    const dialog = await screen.findByRole("dialog");
    const held = within(dialog).getByRole("checkbox", { name: /Package 1/ });
    expect(held).toHaveAttribute("aria-checked", "true");
    expect(held).toHaveAttribute("aria-disabled", "true");
    expect(within(dialog).getByText("Already added")).toBeInTheDocument();

    await userEvent.click(held);
    expect(within(dialog).getByRole("button", { name: "Add 0" })).toBeDisabled();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });
});
