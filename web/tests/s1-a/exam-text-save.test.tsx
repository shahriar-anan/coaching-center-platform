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

const written: StaffExam = {
  ...exam,
  descriptionEn: "<p>English description</p>",
  descriptionBn: "<p>বাংলা বিবরণ</p>",
  rulesEn: "<p>English rules</p>",
  rulesBn: "<p>বাংলা নিয়ম</p>",
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

function patches(calls: { method: string; body: unknown }[]) {
  return calls.filter((call) => call.method === "PATCH");
}

function renderText(source: StaffExam) {
  const api = mockApi({
    [patchPath]: (req: { body: unknown }) => ({ ...source, ...(req.body as Partial<StaffExam>) }),
  });
  renderWithProviders(<ExamMetaForm exam={source} canEdit onDirtyChange={() => undefined} section="text" />, {
    user: fixtures.masterAdmin,
    locale: "en",
  });
  return api;
}

describe("description and rules section", () => {
  it("saves from the box's own Save and stays open while a text is still missing", async () => {
    const api = renderText(exam);

    const box = await screen.findByRole("textbox", { name: "Description (English)" });
    await userEvent.click(box);
    await userEvent.keyboard("Read all rules");

    expect(await screen.findByText("English: unsaved changes")).toBeInTheDocument();
    // The box's Save comes before the section footer's Save in the page.
    await userEvent.click(screen.getAllByRole("button", { name: "Save" })[0]);

    await waitFor(() => expect(patches(api.calls)).toHaveLength(1));
    expect((patches(api.calls)[0].body as { descriptionEn?: string }).descriptionEn).toContain("Read all rules");
    // Three texts are still missing, so the editors stay.
    await waitFor(() => expect(screen.queryByText("English: unsaved changes")).not.toBeInTheDocument());
    expect(screen.getByRole("textbox", { name: "Description (English)" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  });

  it("opens as a preview of all four texts when they are all saved, and Edit reopens the editors", async () => {
    renderText(written);

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    for (const text of ["English description", "বাংলা বিবরণ", "English rules", "বাংলা নিয়ম"]) {
      expect(screen.getByText(text)).toBeInTheDocument();
    }
    expect(screen.getByText("This is how students will read the description and rules.")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    expect(await screen.findByRole("textbox", { name: "Description (English)" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Rules (English)" })).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByRole("textbox")).not.toBeInTheDocument());
    expect(screen.getByText("English description")).toBeInTheDocument();
  });

  it("turns into the preview once the last missing text is saved", async () => {
    const api = renderText({ ...written, rulesBn: null });

    await userEvent.click(await screen.findByRole("button", { name: "বাংলা •" }));
    const box = await screen.findByRole("textbox", { name: "Rules (বাংলা)" });
    await userEvent.click(box);
    await userEvent.keyboard("শেষ নিয়ম");

    const save = screen.getAllByRole("button", { name: "Save" }).find((button) => !button.hasAttribute("disabled"));
    await userEvent.click(save as HTMLElement);

    await waitFor(() => expect(patches(api.calls)).toHaveLength(1));
    await waitFor(() => expect(screen.queryByRole("textbox")).not.toBeInTheDocument());
    expect(screen.getByText(/শেষ নিয়ম/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
  });
});
