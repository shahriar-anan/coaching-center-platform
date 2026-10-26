import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ExamMetaForm } from "@/components/staff/catalog/exam-meta-form";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { ErrorBody } from "../helpers/mock-api";

const examId = "eeeeeeee-0000-0000-0000-000000000002";
const clashId = "eeeeeeee-0000-0000-0000-000000000009";
const patchPath = `PATCH /api/v1/live-exams/${examId}`;

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

/** The 409 body with the clashing exam in `details` (G3). The mock's error type covers only code and message. */
function clash409(details: Record<string, unknown>) {
  return new ErrorBody(409, { code: "EXAM_WINDOW_CONFLICT", message: "Clash.", details } as unknown as { code: string; message: string });
}

describe("five-minute clash names the other exam (G3)", () => {
  it("shows the clashing exam, its time and a link to it, in Bangla", async () => {
    const api = mockApi({
      [patchPath]: () =>
        clash409({
          id: clashId,
          titleEn: "Evidence Act",
          titleBn: "সাক্ষ্য আইন",
          liveAt: "2026-11-12T06:00:00Z",
          endAt: "2026-11-12T06:40:00Z",
        }),
    });
    renderWithProviders(<ExamMetaForm exam={exam} locked={false} section="schedule" />, { user: fixtures.masterAdmin });

    await userEvent.clear(screen.getByLabelText(/সময় \(মিনিট\)/));
    await userEvent.type(screen.getByLabelText(/সময় \(মিনিট\)/), "45");
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("১২ নভেম্বর");
    expect(alert).toHaveTextContent("শেষ হওয়ার অন্তত ৫ মিনিট পরে শুরু করতে হবে।");
    expect(within(alert).getByRole("link", { name: "সাক্ষ্য আইন" })).toHaveAttribute("href", `/staff/exams/${clashId}`);
    await waitFor(() => expect(api.calls.filter((call) => call.method === "PATCH")).toHaveLength(1));
  });

  it("falls back to the generic clash line when the details are missing", async () => {
    mockApi({
      [patchPath]: () => clash409({}),
    });
    renderWithProviders(<ExamMetaForm exam={exam} locked={false} section="schedule" />, { user: fixtures.masterAdmin });

    await userEvent.clear(screen.getByLabelText(/সময় \(মিনিট\)/));
    await userEvent.type(screen.getByLabelText(/সময় \(মিনিট\)/), "45");
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("এই সময়ে এই মডিউলের অন্য একটি পরীক্ষা আছে।");
    expect(screen.queryByRole("link", { name: "সাক্ষ্য আইন" })).not.toBeInTheDocument();
  });
});
