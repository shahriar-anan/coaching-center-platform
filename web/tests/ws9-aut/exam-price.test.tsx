import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ExamMetaForm } from "@/components/staff/catalog/exam-meta-form";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const examId = "eeeeeeee-0000-0000-0000-000000000002";
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

function patchBodies(calls: { method: string; body: unknown }[]) {
  return calls.filter((call) => call.method === "PATCH").map((call) => call.body);
}

describe("exam price section (R2, U5)", () => {
  it("Master Admin types a price and it is sent with the price section's save", async () => {
    const api = mockApi({
      [patchPath]: (req: { body: unknown }) => ({ ...exam, price: (req.body as { price: number }).price }),
    });
    renderWithProviders(<ExamMetaForm exam={exam} locked={false} section="price" />, { user: fixtures.masterAdmin });

    await userEvent.type(screen.getByLabelText("মূল্য (৳)"), "400");
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    await waitFor(() => expect(patchBodies(api.calls)).toHaveLength(1));
    expect(patchBodies(api.calls)[0]).toEqual({ price: 400 });
  });

  it("Master Admin clears the price by leaving the field blank", async () => {
    const api = mockApi({ [patchPath]: () => exam });
    renderWithProviders(<ExamMetaForm exam={{ ...exam, price: 400 }} locked={false} section="price" />, { user: fixtures.masterAdmin });

    await userEvent.clear(screen.getByLabelText("মূল্য (৳)"));
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    await waitFor(() => expect(patchBodies(api.calls)).toHaveLength(1));
    expect(patchBodies(api.calls)[0]).toEqual({ price: null });
  });

  it("rejects a price with three decimal places before any request", async () => {
    const api = mockApi({});
    renderWithProviders(<ExamMetaForm exam={exam} locked={false} section="price" />, { user: fixtures.masterAdmin });

    await userEvent.type(screen.getByLabelText("মূল্য (৳)"), "10.505");
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    expect(await screen.findByText("মূল্য শূন্যের বেশি হতে হবে, দশমিকের পরে সর্বোচ্চ দুই ঘর।")).toBeInTheDocument();
    expect(patchBodies(api.calls)).toHaveLength(0);
  });

  it("System Admin sees the price as text with who can change it, and no save", () => {
    mockApi({});
    renderWithProviders(<ExamMetaForm exam={{ ...exam, price: 400 }} locked={false} section="price" />, { user: fixtures.systemAdmin });

    expect(screen.queryByLabelText("মূল্য (৳)")).not.toBeInTheDocument();
    expect(screen.getByText("মূল্য শুধু মাস্টার অ্যাডমিন ঠিক করতে পারেন।")).toBeInTheDocument();
    expect(screen.getByText("৳৪০০")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "সেভ করুন" })).not.toBeInTheDocument();
  });

  it("System Admin sees 'price not set' for a blank price, not zero", () => {
    mockApi({});
    renderWithProviders(<ExamMetaForm exam={exam} locked={false} section="price" />, { user: fixtures.systemAdmin });
    expect(screen.getByText("মূল্য এখনো ঠিক হয়নি")).toBeInTheDocument();
  });

  it("System Admin's schedule save never sends the price", async () => {
    const api = mockApi({ [patchPath]: () => exam });
    renderWithProviders(<ExamMetaForm exam={{ ...exam, price: 400 }} locked={false} section="schedule" />, { user: fixtures.systemAdmin });

    await userEvent.clear(screen.getByLabelText(/সময় \(মিনিট\)/));
    await userEvent.type(screen.getByLabelText(/সময় \(মিনিট\)/), "45");
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    await waitFor(() => expect(patchBodies(api.calls)).toHaveLength(1));
    expect(patchBodies(api.calls)[0]).not.toHaveProperty("price");
    expect(patchBodies(api.calls)[0]).toMatchObject({ durationMinutes: 45 });
  });
});
