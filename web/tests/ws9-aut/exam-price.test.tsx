import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ExamMetaForm } from "@/components/staff/catalog/exam-meta-form";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const examId = "eeeeeeee-0000-0000-0000-000000000002";

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

describe("exam price (R2)", () => {
  it("Master Admin types a price and it is sent with the save", async () => {
    const api = mockApi({
      [`PATCH /api/v1/live-exams/${examId}`]: (req) => ({ ...exam, price: (req.body as { price: number }).price, status: "DRAFT" }),
    });
    renderWithProviders(<ExamMetaForm exam={exam} locked={false} />, { user: fixtures.masterAdmin });

    await userEvent.type(screen.getByLabelText("মূল্য (৳)"), "400");
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "PATCH")).toBe(true));
    const patch = api.calls.find((call) => call.method === "PATCH");
    expect(patch?.body).toMatchObject({ price: 400 });
  });

  it("Master Admin clears the price by leaving the field blank", async () => {
    const priced = { ...exam, price: 400 };
    const api = mockApi({
      [`PATCH /api/v1/live-exams/${examId}`]: () => exam,
    });
    renderWithProviders(<ExamMetaForm exam={priced} locked={false} />, { user: fixtures.masterAdmin });

    await userEvent.clear(screen.getByLabelText("মূল্য (৳)"));
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "PATCH")).toBe(true));
    expect(api.calls.find((call) => call.method === "PATCH")?.body).toMatchObject({ price: null });
  });

  it("rejects a price with three decimal places before any request", async () => {
    const api = mockApi({});
    renderWithProviders(<ExamMetaForm exam={exam} locked={false} />, { user: fixtures.masterAdmin });

    await userEvent.type(screen.getByLabelText("মূল্য (৳)"), "10.505");
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));

    expect(await screen.findByText("মূল্য শূন্যের বেশি হতে হবে, দশমিকের পরে সর্বোচ্চ দুই ঘর।")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "PATCH")).toBe(false);
  });

  it("System Admin sees the price as text, with who can change it, and never sends the field", async () => {
    const api = mockApi({
      [`PATCH /api/v1/live-exams/${examId}`]: () => exam,
    });
    renderWithProviders(<ExamMetaForm exam={{ ...exam, price: 400 }} locked={false} />, { user: fixtures.systemAdmin });

    expect(screen.queryByLabelText("মূল্য (৳)")).not.toBeInTheDocument();
    expect(screen.getByText("মূল্য শুধু মাস্টার অ্যাডমিন ঠিক করতে পারেন।")).toBeInTheDocument();
    expect(screen.getByText("৳৪০০")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));
    await waitFor(() => expect(api.calls.some((call) => call.method === "PATCH")).toBe(true));
    expect(api.calls.find((call) => call.method === "PATCH")?.body).not.toHaveProperty("price");
  });

  it("System Admin sees 'price not set' for a blank price, not zero", async () => {
    mockApi({});
    renderWithProviders(<ExamMetaForm exam={exam} locked={false} />, { user: fixtures.systemAdmin });
    expect(screen.getByText("মূল্য এখনো ঠিক হয়নি")).toBeInTheDocument();
  });
});
