import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SubmissionForm } from "@/components/staff/marking/submission-form";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { paper } from "./fixtures";

// The PDF viewer loads pdf.js; these tests only need the form.
vi.mock("@/components/pdf/pdf-viewer", () => ({ PdfViewer: () => null }));

const SAVE = "খসড়া নম্বর সেভ করুন";
const PUBLISH = "ফলাফল প্রকাশ করুন";

beforeEach(() => {
  window.localStorage.clear();
});

describe("submission form by role (moved from people/, WS9 MRK)", () => {
  it("gives an instructor the draft form and no publish button", () => {
    mockApi({});
    renderWithProviders(<SubmissionForm paper={paper()} canMark canPublish={false} />, { user: fixtures.instructor });
    expect(screen.getByRole("button", { name: SAVE })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: PUBLISH })).toBeNull();
    expect(screen.getByText(/শিক্ষার্থী এখনো এটি দেখতে পারবেন না/)).toBeInTheDocument();
  });

  it("gives System Admin the facts with the examiner's name, and no form", () => {
    mockApi({});
    renderWithProviders(
      <SubmissionForm paper={paper({ evaluatorName: "Teacher Karim" })} canMark={false} canPublish={false} />,
      { user: fixtures.systemAdmin },
    );
    expect(screen.queryByRole("button", { name: SAVE })).toBeNull();
    expect(screen.getByText("Teacher Karim")).toBeInTheDocument();
  });

  it("keeps Master Admin's publish disabled until a draft mark exists", () => {
    mockApi({});
    const { unmount } = renderWithProviders(<SubmissionForm paper={paper()} canMark canPublish />, {
      user: fixtures.masterAdmin,
    });
    expect(screen.getByRole("button", { name: PUBLISH })).toBeDisabled();
    unmount();

    renderWithProviders(<SubmissionForm paper={paper({ evaluatedPdf: true })} canMark canPublish />, {
      user: fixtures.masterAdmin,
    });
    expect(screen.getByRole("button", { name: PUBLISH })).toBeEnabled();
  });

  it("shows the no-answer line and no form when the paper has no answer", () => {
    mockApi({});
    renderWithProviders(
      <SubmissionForm paper={paper({ answerPdf: false, status: "FAILED_TO_SUBMIT" })} canMark canPublish />,
      { user: fixtures.masterAdmin },
    );
    expect(screen.getByText("খাতা জমা হয়নি, তাই নম্বর দেওয়া যাবে না।")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: SAVE })).toBeNull();
    expect(within(document.body).queryByRole("button", { name: PUBLISH })).toBeNull();
  });
});

describe("saved mark and comment (G1)", () => {
  it("prefills the marks and the comment from the saved draft, and names the stored marked PDF", () => {
    mockApi({});
    renderWithProviders(
      <SubmissionForm
        paper={paper({ score: 40, comment: "ভালো লিখেছে", evaluatedPdf: true, evaluatedFileName: "rahim-marked.pdf" })}
        canMark
        canPublish={false}
      />,
      { user: fixtures.instructor },
    );
    expect(screen.getByRole("spinbutton", { name: /প্রাপ্ত নম্বর/ })).toHaveValue(40);
    expect(screen.getByRole("textbox", { name: /শিক্ষকের মন্তব্য/ })).toHaveValue("ভালো লিখেছে");
    expect(screen.getByText("আগে জমা দেওয়া ফাইল: rahim-marked.pdf")).toBeInTheDocument();
  });

  it("says before the start that each save needs the marked PDF again", () => {
    mockApi({});
    renderWithProviders(<SubmissionForm paper={paper()} canMark canPublish={false} />, { user: fixtures.instructor });
    const rule = screen.getByText(/প্রতিবার সেভ করার সময় মূল্যায়ন করা খাতার PDF আবার দিতে হবে/);
    const marks = screen.getByRole("spinbutton", { name: /প্রাপ্ত নম্বর/ });
    expect(rule.compareDocumentPosition(marks) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("refuses marks above the maximum before any request is sent", async () => {
    const api = mockApi({});
    renderWithProviders(<SubmissionForm paper={paper()} canMark canPublish={false} />, { user: fixtures.instructor });
    await userEvent.type(screen.getByRole("spinbutton", { name: /প্রাপ্ত নম্বর/ }), "99");
    await userEvent.click(screen.getByRole("button", { name: SAVE }));
    expect(screen.getByText("নম্বর ০ থেকে ৫০ পর্যন্ত হতে হবে।")).toBeInTheDocument();
    expect(api.calls.filter((call) => call.method === "POST")).toHaveLength(0);
  });

  it("restores unsaved changes left from an earlier visit", async () => {
    mockApi({});
    window.localStorage.setItem("draft:marking:p1", JSON.stringify({ marks: "30", comment: "দারুণ" }));
    renderWithProviders(<SubmissionForm paper={paper()} canMark canPublish={false} />, { user: fixtures.instructor });
    await userEvent.click(screen.getByRole("button", { name: "সেভ না করা পরিবর্তন ফিরিয়ে আনুন" }));
    expect(screen.getByRole("spinbutton", { name: /প্রাপ্ত নম্বর/ })).toHaveValue(30);
    expect(screen.getByRole("textbox", { name: /শিক্ষকের মন্তব্য/ })).toHaveValue("দারুণ");
  });
});

describe("saved comment snippets (UF4)", () => {
  it("saves the comment on this device, inserts it with one click, and deletes it", async () => {
    mockApi({});
    renderWithProviders(<SubmissionForm paper={paper()} canMark canPublish={false} />, { user: fixtures.instructor });
    const comment = screen.getByRole("textbox", { name: /শিক্ষকের মন্তব্য/ });

    await userEvent.type(comment, "ভালো হয়েছে");
    await userEvent.click(screen.getByRole("button", { name: "মন্তব্য সংরক্ষণ করুন" }));
    expect(screen.getByRole("button", { name: "ভালো হয়েছে" })).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem("marking:comment-snippets") ?? "[]")).toEqual(["ভালো হয়েছে"]);

    await userEvent.clear(comment);
    await userEvent.click(screen.getByRole("button", { name: "ভালো হয়েছে" }));
    expect(comment).toHaveValue("ভালো হয়েছে");

    await userEvent.click(screen.getByRole("button", { name: "“ভালো হয়েছে” মুছুন" }));
    expect(screen.queryByRole("button", { name: "ভালো হয়েছে" })).toBeNull();
  });

  it("keeps at most twenty snippets", () => {
    window.localStorage.setItem(
      "marking:comment-snippets",
      JSON.stringify(Array.from({ length: 20 }, (_, index) => `মন্তব্য ${index}`)),
    );
    mockApi({});
    renderWithProviders(<SubmissionForm paper={paper({ comment: "নতুন মন্তব্য" })} canMark canPublish={false} />, {
      user: fixtures.instructor,
    });
    expect(screen.getByRole("button", { name: "মন্তব্য সংরক্ষণ করুন" })).toBeDisabled();
    expect(screen.getByText(/সর্বোচ্চ ২০টি মন্তব্য রাখা যাবে/)).toBeInTheDocument();
  });
});
