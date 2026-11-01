import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SubmissionForm } from "@/components/staff/marking/submission-form";
import { ApiError } from "@/lib/api/errors";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { paper } from "./fixtures";

// The API's error for the next save or ready flag. The hooks below throw it, so the form's text is what is under test.
const failure = vi.hoisted(() => ({ error: null as unknown }));

vi.mock("@/lib/api/exam/staff-marking", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/exam/staff-marking")>();
  const failing = () => ({
    isPending: false,
    mutateAsync: async () => {
      throw failure.error;
    },
  });
  return { ...actual, useSaveMarking: failing, useSetReady: failing };
});

// The PDF viewer loads pdf.js; these tests only need the form.
vi.mock("@/components/pdf/pdf-viewer", () => ({ PdfViewer: () => null }));

const SAVE = "খসড়া নম্বর সেভ করুন";
const READY = "প্রকাশের জন্য প্রস্তুত চিহ্নিত করুন";

function apiFailure(status: number, code: string): ApiError {
  return new ApiError({ code, message: code, timestamp: new Date().toISOString(), requestId: "test" }, status);
}

beforeEach(() => {
  window.localStorage.clear();
  mockApi({});
});

describe("a failed save says why (P3)", () => {
  // A marked PDF is stored, so the save needs no file and reaches the API.
  it.each([
    ["MARKS_OUT_OF_RANGE", 400, "নম্বর ০ থেকে ৫০ পর্যন্ত হতে হবে।"],
    ["VALIDATION_FAILED", 400, "নম্বর ও মূল্যায়ন করা খাতার PDF আবার দেখে নিয়ে চেষ্টা করুন।"],
    ["EVALUATION_NOT_ALLOWED", 409, "এই খাতা মূল্যায়ন করা যাবে না, কারণ এর উত্তরের PDF নেই।"],
    ["ACCESS_DENIED", 403, "এই খাতা মূল্যায়ন করার অনুমতি আপনার নেই।"],
    ["INTERNAL_ERROR", 500, "আবার চেষ্টা করুন। সমস্যা থেকে গেলে কিছুক্ষণ পরে আসুন।"],
  ])("shows its own text for %s", async (code, status, text) => {
    failure.error = apiFailure(status, code);
    renderWithProviders(
      <SubmissionForm paper={paper({ score: 40, evaluatedPdf: true, evaluatedFileName: "rahim-marked.pdf" })} canMark canPublish={false} />,
      { user: fixtures.instructor },
    );
    await userEvent.click(screen.getByRole("button", { name: SAVE }));
    expect(await screen.findByText(text)).toBeInTheDocument();
  });
});

describe("a failed ready flag (P3)", () => {
  it("says a draft mark is needed when the API refuses the flag, not that the paper has no PDF", async () => {
    failure.error = apiFailure(409, "EVALUATION_NOT_ALLOWED");
    renderWithProviders(<SubmissionForm paper={paper({ score: 40, evaluatedPdf: true })} canMark canPublish={false} />, {
      user: fixtures.instructor,
    });
    await userEvent.click(screen.getByRole("button", { name: READY }));
    expect(await screen.findByText("প্রস্তুত চিহ্ন দেওয়ার আগে খসড়া নম্বর সেভ করুন।")).toBeInTheDocument();
    expect(screen.queryByText("এই খাতা মূল্যায়ন করা যাবে না, কারণ এর উত্তরের PDF নেই।")).toBeNull();
  });
});
