import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PasteQuestions, parsePastedQuestions } from "@/components/staff/catalog/paste-questions";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";

const examId = "eeeeeeee-0000-0000-0000-000000000004";

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

const validBlock = ["প্রশ্ন: বাংলাদেশের রাজধানী কোথায়?", "ক) ঢাকা *", "খ) চট্টগ্রাম", "নম্বর: ২", "ব্যাখ্যা: ঢাকা রাজধানী।"].join("\n");

describe("paste format (F7)", () => {
  it("parses a block with marks written in Bengali digits and an explanation", () => {
    const [block] = parsePastedQuestions(validBlock);
    expect(block).toEqual({
      index: 1,
      question: {
        stem: "বাংলাদেশের রাজধানী কোথায়?",
        marks: 2,
        explanation: "ঢাকা রাজধানী।",
        options: [
          { text: "ঢাকা", correct: true },
          { text: "চট্টগ্রাম", correct: false },
        ],
      },
    });
  });

  it("splits questions on blank lines and reports each bad block on its own", () => {
    const text = [validBlock, "", "খ) শুধু অপশন", "", "প্রশ্ন: দুটি অপশন, কোনো সঠিক নয়", "ক) এক", "খ) দুই", "", "প্রশ্ন: দুটি সঠিক", "ক) এক *", "খ) দুই *"].join("\n");
    const blocks = parsePastedQuestions(text);
    expect(blocks.map((b) => ("error" in b ? b.error : "ok"))).toEqual(["ok", "noStem", "noCorrect", "manyCorrect"]);
  });

  it("marks below 1 are an error for that block", () => {
    const [block] = parsePastedQuestions(["প্রশ্ন: ক?", "ক) এক *", "খ) দুই", "নম্বর: 0"].join("\n"));
    expect(block).toEqual({ index: 1, error: "badMarks" });
  });
});

describe("paste dialog (F7)", () => {
  it("previews the blocks, then creates them one by one with progress", async () => {
    const api = mockApi({
      [`POST /api/v1/live-exams/${examId}/questions`]: { id: "q", stem: "", marks: 1, explanation: null, position: 1, options: [] },
    });
    renderWithProviders(<PasteQuestions exam={exam} nextPosition={1} open onOpenChange={() => undefined} />, { user: fixtures.masterAdmin });

    await userEvent.type(screen.getByLabelText("প্রশ্নের তালিকা"), validBlock);
    await userEvent.click(screen.getByRole("button", { name: "দেখে নিন" }));
    expect(await screen.findByText("১টি প্রশ্ন তৈরি হতে পারে · ০টিতে ভুল আছে")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "১টি প্রশ্ন তৈরি করুন" }));
    await waitFor(() => expect(api.calls.filter((call) => call.method === "POST")).toHaveLength(1));
    expect(api.calls.find((call) => call.method === "POST")?.body).toMatchObject({ stem: "বাংলাদেশের রাজধানী কোথায়?", marks: 2, position: 1 });
  });

  it("stops at the first failed create and names the question", async () => {
    mockApi({
      [`POST /api/v1/live-exams/${examId}/questions`]: () => apiError(422, "VALIDATION_FAILED", "Invalid."),
    });
    renderWithProviders(<PasteQuestions exam={exam} nextPosition={1} open onOpenChange={() => undefined} />, { user: fixtures.masterAdmin });

    await userEvent.type(screen.getByLabelText("প্রশ্নের তালিকা"), `${validBlock}\n\n${validBlock}`);
    await userEvent.click(screen.getByRole("button", { name: "দেখে নিন" }));
    await userEvent.click(await screen.findByRole("button", { name: "২টি প্রশ্ন তৈরি করুন" }));
    expect(await screen.findByText(/প্রশ্ন ১ তৈরি হয়নি/)).toBeInTheDocument();
  });
});

