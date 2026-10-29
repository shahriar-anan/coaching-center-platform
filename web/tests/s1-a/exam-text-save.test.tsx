import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it } from "vitest";
import { ExamMetaForm } from "@/components/staff/catalog/exam-meta-form";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const examId = "eeeeeeee-0000-0000-0000-000000000003";
const patchPath = `PATCH /api/v1/live-exams/${examId}`;

const exam: StaffExam = {
  id: examId,
  moduleId: "aaaaaaaa-0000-0000-0000-000000000001",
  examType: "MCQ",
  paperLanguage: null,
  titleEn: "CrPC",
  titleBn: "সিআরপিসি",
  descriptionEn: null,
  descriptionBn: null,
  rulesEn: null,
  rulesBn: null,
  liveAt: null,
  endAt: null,
  durationMinutes: 40,
  passPercent: 40,
  maximum: 50,
  status: "DRAFT",
  finallySaved: false,
  price: null,
  questionPdf: null,
  questions: [],
};

beforeAll(() => {
  // jsdom has no layout; ProseMirror measures text and asks for the element under a click.
  if (!document.elementFromPoint) {
    document.elementFromPoint = () => null;
  }
  const rect = () => ({ x: 0, y: 0, top: 0, left: 0, bottom: 0, right: 0, width: 0, height: 0, toJSON: () => ({}) }) as DOMRect;
  const none = () => [] as unknown as DOMRectList;
  for (const proto of [Element.prototype, Text.prototype, Range.prototype]) {
    const measured = proto as unknown as { getClientRects?: () => DOMRectList; getBoundingClientRect?: () => DOMRect };
    measured.getClientRects ??= none;
    measured.getBoundingClientRect ??= rect;
  }
});

describe("description and rules: the box's own Save", () => {
  it("saves the section from the box and then says the language is saved", async () => {
    const api = mockApi({
      [patchPath]: (req: { body: unknown }) => ({ ...exam, ...(req.body as Partial<StaffExam>) }),
    });
    renderWithProviders(<ExamMetaForm exam={exam} canEdit onDirtyChange={() => undefined} section="text" />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });

    const box = await screen.findByRole("textbox", { name: "Description (English)" });
    await userEvent.click(box);
    await userEvent.keyboard("Read all rules");

    expect(await screen.findByText("English: unsaved changes")).toBeInTheDocument();
    // The box's Save comes before the section footer's Save in the page.
    await userEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

    await waitFor(() => expect(api.calls.filter((call) => call.method === "PATCH")).toHaveLength(1));
    const body = api.calls.find((call) => call.method === "PATCH")?.body as { descriptionEn?: string };
    expect(body.descriptionEn).toContain("Read all rules");
    // The description now shows as students read it, with an Edit button; the rules box has nothing saved and stays open.
    await waitFor(() => expect(screen.queryByRole("textbox", { name: "Description (English)" })).not.toBeInTheDocument());
    expect(screen.getByText(/Read all rules/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Rules (English)" })).toBeInTheDocument();
  });
});
