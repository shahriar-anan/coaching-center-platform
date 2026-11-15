import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AdmissionDetailView } from "@/components/staff/offline/admission-detail";
import type { AdmissionDetail, AdmissionItem } from "@/lib/api/exam/offline-admin";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ADMISSION_ID, detail, page } from "./fixtures";

/** Three packages with their exams per type: 2 + 2, 1 + 0 and 0 + 0. */
const PACKAGES = [
  { id: "mod-a", titleEn: "Package A", titleBn: "প্যাকেজ এ", status: "PUBLISHED", mcqCount: 2, writtenCount: 2 },
  { id: "mod-b", titleEn: "Package B", titleBn: "প্যাকেজ বি", status: "PUBLISHED", mcqCount: 1, writtenCount: 0 },
  { id: "mod-c", titleEn: "Package C", titleBn: "প্যাকেজ সি", status: "PUBLISHED", mcqCount: 0, writtenCount: 0 },
];

function packageItem(letter: string, moduleId: string): AdmissionItem {
  return {
    id: `item-${moduleId}`,
    scope: "MODULE",
    examId: null,
    moduleId,
    titleEn: `Package ${letter}`,
    titleBn: `প্যাকেজ ${letter}`,
    grantId: null,
    addedAt: "2026-11-10T04:00:00Z",
  };
}

function examItem(number: number): AdmissionItem {
  return {
    id: `item-exam-${number}`,
    scope: "EXAM",
    examId: `exam-${number}`,
    moduleId: null,
    titleEn: `Mock exam ${number}`,
    titleBn: `মক এক্সাম ${number}`,
    grantId: null,
    addedAt: "2026-11-10T04:00:00Z",
  };
}

/** Three packages and nine exams: twelve items in all. */
const TWELVE_ITEMS: AdmissionItem[] = [
  packageItem("A", "mod-a"),
  packageItem("B", "mod-b"),
  packageItem("C", "mod-c"),
  ...Array.from({ length: 9 }, (_, index) => examItem(index + 1)),
];

/** Opens the Onboard dialog of a ready admission as the Master Admin, and returns the dialog and the user. */
async function openOnboard(items: AdmissionItem[], overrides: Partial<AdmissionDetail> = {}) {
  mockApi({
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}`]: detail({ state: "READY", readyProblems: [], items, ...overrides }),
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}/history`]: [],
    [`GET /api/v1/offline-admissions/${ADMISSION_ID}/next-roll`]: { suggestion: "192007" },
    "GET /api/v1/offline-batches": [],
    "GET /api/v1/live-exams": page([]),
    "GET /api/v1/live-exam-modules": page(PACKAGES),
  });
  renderWithProviders(<AdmissionDetailView id={ADMISSION_ID} />, {
    user: fixtures.masterAdmin,
    locale: "en",
    route: `/staff/admissions/${ADMISSION_ID}`,
  });
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Onboard" }));
  const dialog = await screen.findByRole("dialog");
  return { dialog, user };
}

/** The value beside a label in the summary list. */
function factValue(dialog: HTMLElement, label: string): HTMLElement {
  const value = within(dialog).getByText(label).nextElementSibling;
  if (!(value instanceof HTMLElement)) {
    throw new Error(`No value beside ${label}`);
  }
  return value;
}

describe("the onboard dialog lists the exams and packages as rows (plan 6.3)", () => {
  it("shows the count heading, and every item from the first six as its own row", async () => {
    const { dialog } = await openOnboard(TWELVE_ITEMS);

    expect(within(dialog).getByRole("heading", { name: "3 packages, 9 exams" })).toBeInTheDocument();
    const list = within(dialog).getByRole("list", { name: "Exams and packages" });
    expect(within(list).getAllByRole("listitem")).toHaveLength(6);
    expect(within(dialog).getByRole("button", { name: "Show all 12" })).toBeInTheDocument();
  });

  it("shows all twelve rows once Show all is pressed, and then offers Show fewer", async () => {
    const { dialog, user } = await openOnboard(TWELVE_ITEMS);

    await user.click(within(dialog).getByRole("button", { name: "Show all 12" }));

    const list = within(dialog).getByRole("list", { name: "Exams and packages" });
    const rows = within(list).getAllByRole("listitem");
    expect(rows).toHaveLength(12);
    for (const item of TWELVE_ITEMS) {
      const title = item.titleEn;
      expect(within(list).getByText(title)).toBeInTheDocument();
    }
    expect(within(dialog).getByRole("button", { name: "Show fewer" })).toBeInTheDocument();
  });

  it("marks each package with a Package pill and its exam count, and each exam with an Exam pill", async () => {
    const { dialog } = await openOnboard(TWELVE_ITEMS);
    const list = within(dialog).getByRole("list", { name: "Exams and packages" });

    expect(within(list).getAllByText("Package")).toHaveLength(3);
    expect(await within(list).findByText("4 exams")).toBeInTheDocument();
    expect(within(list).getByText("1 exam")).toBeInTheDocument();
    expect(within(list).getByText("0 exams")).toBeInTheDocument();
    expect(within(list).getAllByText("Exam").length).toBe(3);
  });

  it("shows one row and no Show all for a single exam", async () => {
    const { dialog } = await openOnboard([examItem(1)]);

    expect(within(dialog).getByRole("heading", { name: "1 exam" })).toBeInTheDocument();
    expect(within(dialog).getByRole("list", { name: "Exams and packages" })).toBeInTheDocument();
    expect(within(dialog).getAllByRole("listitem")).toHaveLength(1);
    expect(within(dialog).queryByRole("button", { name: /Show all/ })).not.toBeInTheDocument();
  });

  it("states the batch, the fee, the dates, the number of payments and the total", async () => {
    const { dialog } = await openOnboard([examItem(1)]);

    expect(factValue(dialog, "Batch")).toHaveTextContent("BJS Evening");
    expect(factValue(dialog, "Monthly fee")).toHaveTextContent("3,000");
    expect(factValue(dialog, "Number of payments")).toHaveTextContent(/^3$/);
    // 3,000 a month for three dues from 9 December 2026 to 9 February 2027.
    expect(factValue(dialog, "Total")).toHaveTextContent("9,000");
    expect(factValue(dialog, "Email")).toHaveTextContent("The student is emailed.");
  });
});

describe("an applicant who already has access to another batch (plan P14)", () => {
  it("says so in one line, naming every batch the student is already in", async () => {
    const { dialog } = await openOnboard([examItem(1)], {
      alreadyAccessTo: [
        { nameEn: "Batch 3 (Evening)", nameBn: "ব্যাচ ৩ (সন্ধ্যা)" },
        { nameEn: "Batch 4", nameBn: "ব্যাচ ৪" },
      ],
    });
    expect(within(dialog).getByRole("status")).toHaveTextContent("Rahim Uddin already has access to Batch 3 (Evening), Batch 4.");
  });

  it("shows no such line when the student has no other batch", async () => {
    const { dialog } = await openOnboard([examItem(1)]);
    expect(within(dialog).queryByText(/already has access to/)).toBeNull();
  });
});

describe("the roll number at onboarding (rules R4, A7)", () => {
  it("fills in the suggested roll, and Onboard uses it", async () => {
    const { dialog } = await openOnboard([examItem(1)]);
    const roll = within(dialog).getByRole("textbox", { name: /Roll number/ });
    expect(roll).toHaveValue("192007");
    expect(within(dialog).getByRole("button", { name: "Onboard student" })).toBeEnabled();
  });

  it("holds Onboard off for a roll that is not 4 to 12 digits", async () => {
    const { dialog, user } = await openOnboard([examItem(1)]);
    const roll = within(dialog).getByRole("textbox", { name: /Roll number/ });
    await user.clear(roll);
    await user.type(roll, "12");
    expect(within(dialog).getByText("Use 4 to 12 digits.")).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Onboard student" })).toBeDisabled();
  });
});
