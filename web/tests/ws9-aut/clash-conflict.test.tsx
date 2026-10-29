import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ExamMetaForm } from "@/components/staff/catalog/exam-meta-form";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ErrorBody } from "../helpers/mock-api";

const examId = "eeeeeeee-0000-0000-0000-000000000002";
const patchPath = `PATCH /api/v1/live-exams/${examId}`;

/** The Bangla text of the window checks (catalog keys examMeta.windowShort and the EXAM_WINDOW_INVALID error). */
const WINDOW_SHORT = "সময়সীমা পরীক্ষার সময়ের চেয়ে কম। শেষ সময় পরে করুন, অথবা সময় কমান।";
/** The pass mark field, by its label. */
const PASS = /পাস নম্বর/;
const WINDOW_INVALID = "শেষ সময় শুরুর পরে হতে হবে, এবং পরীক্ষার সময়ের চেয়ে কম হবে না।";

// A 40-minute window (07:00 to 07:40 UTC) for a 40-minute exam: the window fits.
const exam: StaffExam = {
  id: examId,
  moduleId: "aaaaaaaa-0000-0000-0000-000000000001",
  examType: "MCQ",
  paperLanguage: "BN",
  titleEn: "CrPC",
  titleBn: "সিআরপিসি",
  descriptionEn: null,
  descriptionBn: null,
  rulesEn: null,
  rulesBn: null,
  liveAt: "2026-11-12T07:00:00Z",
  endAt: "2026-11-12T07:40:00Z",
  durationMinutes: 40,
  passPercent: 40,
  maximum: 50,
  status: "DRAFT",
  finallySaved: false,
  price: null,
  questionPdf: null,
  questions: [],
};

/**
 * The window rule (D17 replaced the five-minute clash): the end must come at least the duration after the start. The
 * form checks it before saving; the server refuses it too (EXAM_WINDOW_INVALID). Neither names another exam.
 */
describe("window shorter than the duration (D17)", () => {
  it("refuses it in the form, before any request, and says so under the end field", async () => {
    const api = mockApi({ [patchPath]: () => exam });
    renderWithProviders(<ExamMetaForm exam={exam} canEdit section="schedule" onDirtyChange={() => undefined} />, {
      user: fixtures.masterAdmin,
    });

    await userEvent.clear(screen.getByLabelText(/সময় \(মিনিট\)/));
    await userEvent.type(screen.getByLabelText(/সময় \(মিনিট\)/), "45");
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    expect(await screen.findByText(WINDOW_SHORT)).toBeInTheDocument();
    expect(api.calls.filter((call) => call.method === "PATCH")).toHaveLength(0);
  });

  it("shows the server's refusal under the end field when the server finds the window too short", async () => {
    const api = mockApi({
      [patchPath]: () => new ErrorBody(400, { code: "EXAM_WINDOW_INVALID", message: "Window too short." }),
    });
    renderWithProviders(<ExamMetaForm exam={exam} canEdit section="schedule" onDirtyChange={() => undefined} />, {
      user: fixtures.masterAdmin,
    });

    // The form is dirty (the pass mark changed), so the save goes to the server, which refuses the window.
    await userEvent.clear(screen.getByLabelText(PASS));
    await userEvent.type(screen.getByLabelText(PASS), "50");
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    expect((await screen.findAllByText(WINDOW_INVALID)).length).toBeGreaterThan(0);
    await waitFor(() => expect(api.calls.filter((call) => call.method === "PATCH")).toHaveLength(1));
  });

  it("saves a window that fits, and shows no window message", async () => {
    const api = mockApi({ [patchPath]: () => ({ ...exam, passPercent: 50 }) });
    renderWithProviders(<ExamMetaForm exam={exam} canEdit section="schedule" onDirtyChange={() => undefined} />, {
      user: fixtures.masterAdmin,
    });

    await userEvent.clear(screen.getByLabelText(PASS));
    await userEvent.type(screen.getByLabelText(PASS), "50");
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    await waitFor(() => expect(api.calls.filter((call) => call.method === "PATCH")).toHaveLength(1));
    expect(screen.queryByText(WINDOW_SHORT)).not.toBeInTheDocument();
    expect(screen.queryByText(WINDOW_INVALID)).not.toBeInTheDocument();
  });
});
