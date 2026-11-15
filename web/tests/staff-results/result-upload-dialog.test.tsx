import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { Me } from "@/lib/api/exam/types";
import { describe, expect, it, vi } from "vitest";
import { ResultUploadDialog } from "@/components/staff/offline/result-upload-dialog";
import type { ResultPreview, ResultSheet } from "@/lib/api/exam/offline-results";
import { ErrorBody, apiError, fixtures, mockApi, renderWithProviders } from "../helpers";
import { BATCH_ID, CLASS_STAGED, batch, preview, resultRow, sheet, sheetView } from "./fixtures";

const EXAM_LABEL = "Bangla · Grammar, 11 Nov 2026";
const PREVIEW = `POST /api/v1/offline-classes/${CLASS_STAGED}/results/preview`;
const SAVE = `POST /api/v1/offline-classes/${CLASS_STAGED}/results`;
const PUBLISH = `POST /api/v1/offline-classes/${CLASS_STAGED}/results/publish`;
const VIEW = `GET /api/v1/offline-classes/${CLASS_STAGED}/results`;

function xlsx(name = "Exam-01.xlsx") {
  return new File(["sheet"], name, { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
}

/** A refusal with field errors, in the API's shape. */
function refused(status: number, fieldErrors: { field: string; message: string }[], code = "VALIDATION_FAILED") {
  return new ErrorBody(status, { code, message: "The request was refused.", fieldErrors } as unknown as { code: string; message: string });
}

type Options = {
  staged?: ResultSheet | null;
  /** Routes that replace the default answers. */
  routes?: Record<string, unknown>;
  locale?: "en" | "bn";
};

/** Renders the dialog with the check, save and publish answers. Returns the mock calls and the close spy. */
function renderDialog(role: Me, checkAnswer: ResultPreview | (() => ResultPreview) = preview(), options: Options = {}) {
  const onOpenChange = vi.fn();
  const api = mockApi({
    [`GET /api/v1/offline-batches/${BATCH_ID}`]: batch,
    [VIEW]: sheetView({ classId: CLASS_STAGED, batchId: BATCH_ID, staged: options.staged ?? null }),
    [PREVIEW]: () => (typeof checkAnswer === "function" ? checkAnswer() : checkAnswer),
    [SAVE]: sheetView({ classId: CLASS_STAGED, batchId: BATCH_ID, staged: null }),
    [PUBLISH]: sheetView({ classId: CLASS_STAGED, batchId: BATCH_ID }),
    ...options.routes,
  });
  renderWithProviders(<ResultUploadDialog classId={CLASS_STAGED} examLabel={EXAM_LABEL} open onOpenChange={onOpenChange} />, {
    user: role,
    locale: options.locale ?? "en",
  });
  return { api, onOpenChange, user: userEvent.setup() };
}

/** Picks the type, chooses the file and runs the check (English labels). */
async function checkWith(user: ReturnType<typeof userEvent.setup>, type: string) {
  await user.click(screen.getByRole("radio", { name: type }));
  await user.upload(screen.getByLabelText(/Result file/), xlsx());
  await user.click(screen.getByRole("button", { name: "Check file" }));
}

describe("the upload dialog, step 1: choose", () => {
  it("shows the three exam types as radio cards, each naming its columns", async () => {
    renderDialog(fixtures.masterAdmin);

    const radios = screen.getAllByRole("radio");
    expect(radios).toHaveLength(3);
    expect(screen.getByRole("radio", { name: "MCQ" })).toHaveAccessibleDescription("Needs the Roll, Number, Right, Wrong and Negative columns.");
    expect(screen.getByRole("radio", { name: "MCQ + Written" })).toHaveAccessibleDescription(
      "Needs the Roll, Number, Right, Wrong, Negative and Written columns.",
    );
    expect(screen.getByRole("radio", { name: "Written" })).toHaveAccessibleDescription("Needs the Roll, Number and Written columns.");
  });

  it("explains an empty pass mark, and refuses a file that is not .xlsx before any check", async () => {
    const { api, user } = renderDialog(fixtures.masterAdmin);

    expect(screen.getByText("Leave empty to show no pass or fail.")).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "MCQ" }));
    // The input filters by accept=".xlsx", so the change is fired directly to reach the dialog's own extension check.
    fireEvent.change(screen.getByLabelText(/Result file/), { target: { files: [new File(["x"], "old.xls")] } });
    await user.click(screen.getByRole("button", { name: "Check file" }));

    expect(await screen.findByText("Save the file as .xlsx and upload it again.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.path.endsWith("/preview"))).toBe(false);
  });

  it("shows a file error from the check under the file input, not in the form alert", async () => {
    const { user } = renderDialog(fixtures.masterAdmin, preview(), {
      routes: { [PREVIEW]: refused(400, [{ field: "file", message: "The sheet has no Written column." }]) },
    });

    await checkWith(user, "MCQ");

    const message = await screen.findByText("The sheet has no Written column.");
    expect(message.closest("[role='alert']")).toBeNull();
    expect(screen.getByLabelText(/Result file/)).toBeInTheDocument();
  });

  it("shows an exam type error under the type cards", async () => {
    const { user } = renderDialog(fixtures.masterAdmin, preview(), {
      routes: { [PREVIEW]: refused(400, [{ field: "examType", message: "The sheet has no Number column for this type." }]) },
    });

    await checkWith(user, "Written");

    const group = screen.getByRole("group", { name: "Exam type" });
    expect(await within(group).findByText("The sheet has no Number column for this type.")).toBeInTheDocument();
  });

  it("shows a pass mark error under the pass mark", async () => {
    const { user } = renderDialog(fixtures.masterAdmin, preview(), {
      routes: { [PREVIEW]: refused(400, [{ field: "passMark", message: "The pass mark cannot be above the full marks." }]) },
    });

    await checkWith(user, "MCQ");

    expect(await screen.findByText("The pass mark cannot be above the full marks.")).toBeInTheDocument();
  });

  it("lists the other field errors in the form alert, one line each", async () => {
    const { user } = renderDialog(fixtures.masterAdmin, preview(), {
      routes: {
        [PREVIEW]: refused(400, [
          { field: "rows", message: "The file has more than 2,000 rows." },
          { field: "rollNo", message: "The roll numbers are not text." },
        ]),
      },
    });

    await checkWith(user, "MCQ");

    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText("The file has more than 2,000 rows.")).toBeInTheDocument();
    expect(within(alert).getByText("The roll numbers are not text.")).toBeInTheDocument();
  });

  it("keeps the generic sentence for an error that has no field errors", async () => {
    const { user } = renderDialog(fixtures.masterAdmin, preview(), {
      routes: { [PREVIEW]: apiError(400, "VALIDATION_FAILED", "Bad request.") },
    });

    await checkWith(user, "MCQ");

    expect(await screen.findByRole("alert")).toHaveTextContent("Check the highlighted fields and try again.");
  });

  it("warns that a staged version will be replaced, with who uploaded it and when", async () => {
    const staged = sheet({
      status: "STAGED",
      uploadedBy: "Office Staff",
      uploadedAt: "2026-11-13T05:00:00Z",
      publishedBy: null,
      publishedAt: null,
    });
    const { user } = renderDialog(fixtures.masterAdmin, preview(), { staged });

    expect(await screen.findByText(/^A staged version uploaded by Office Staff on .+ will be replaced\.$/)).toBeInTheDocument();
    // Save stays allowed: the check still enables it.
    await checkWith(user, "MCQ");
    expect(await screen.findByRole("button", { name: "Save as staged" })).toBeEnabled();
  });

  it("shows no warning when nothing is staged", async () => {
    renderDialog(fixtures.masterAdmin);

    await waitFor(() => expect(screen.queryByText(/will be replaced/)).toBeNull());
  });
});

describe("the upload dialog, step 2: preview", () => {
  it("shows the problems first and keeps Save disabled while there are any", async () => {
    const problems = preview({
      problems: [
        {
          rowNumber: 23,
          roll: "192099",
          code: "ROLL_NOT_IN_BATCH",
          params: { roll: "192099" },
          message: "No student of this batch has this roll number.",
        },
      ],
    });
    const { user } = renderDialog(fixtures.masterAdmin, problems);

    await checkWith(user, "MCQ");

    const box = (await screen.findByText("Fix these in the file before saving")).closest("div") as HTMLElement;
    expect(within(box).getByText("Row 23, roll 192099: No student of this batch has roll 192099.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save as staged" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Save and publish" })).toBeDisabled();
  });

  it("shows the Bangla sentence for a problem code in Bangla", async () => {
    const problems = preview({
      problems: [{ rowNumber: 23, roll: "192099", code: "ROLL_NOT_IN_BATCH", params: { roll: "192099" }, message: "English fallback." }],
    });
    const { user } = renderDialog(fixtures.masterAdmin, problems, { locale: "bn" });

    await user.click(screen.getByRole("radio", { name: "MCQ" }));
    await user.upload(screen.getByLabelText(/ফলের ফাইল/), xlsx());
    await user.click(screen.getByRole("button", { name: "ফাইল যাচাই করুন" }));

    expect(await screen.findByText(/এই ব্যাচে রোল 192099-এর কোনো শিক্ষার্থী নেই।/)).toBeInTheDocument();
    expect(screen.queryByText("English fallback.")).toBeNull();
  });

  it("shows the API's English message for a problem code the screens do not know", async () => {
    const problems = preview({
      problems: [{ rowNumber: null, roll: null, code: "SOMETHING_NEW", params: {}, message: "The API says something new." }],
    });
    const { user } = renderDialog(fixtures.masterAdmin, problems);

    await checkWith(user, "MCQ");

    expect(await screen.findByText("The API says something new.")).toBeInTheDocument();
  });

  it("enables Save for a clean preview", async () => {
    const { user } = renderDialog(fixtures.masterAdmin, preview());

    await checkWith(user, "MCQ");

    expect(await screen.findByRole("button", { name: "Save as staged" })).toBeEnabled();
    expect(screen.getByText("Full marks 50 · Negative 0.25 per wrong · Rows: 2 · Matched: 2 · Absent: 0 · Differ from the file: 0")).toBeInTheDocument();
  });

  it("tints a row whose total differs from the file and shows the file's value", async () => {
    const mismatch = preview({
      counts: { rows: 2, matched: 2, absent: 0, mismatched: 1 },
      rows: [
        resultRow({ roll: "192001", studentName: "Rahim Uddin", total: 45, mcq: 45, rank: 1, passed: true, fileTotal: 46, fileRank: 1, mismatch: true }),
        resultRow({ roll: "192002", studentName: "Sumi Akter", total: 30, mcq: 30, rank: 2, passed: false, fileTotal: 30, fileRank: 2 }),
      ],
    });
    const { user } = renderDialog(fixtures.masterAdmin, mismatch);

    await checkWith(user, "MCQ");

    const table = await screen.findByRole("table", { name: "Result sheet" });
    const row = within(table).getByText("Rahim Uddin").closest("tr");
    expect(row).toHaveClass("bg-warn/10");
    expect(within(row as HTMLElement).getByText("46 in file")).toBeInTheDocument();
    expect(screen.getByText("Roll 192001: total 46 in the file, 45 calculated")).toBeInTheDocument();
  });

  it("names an absent student, shows Absent, and tags a zero row as might be absent", async () => {
    const withAbsent = preview({
      counts: { rows: 3, matched: 2, absent: 1, mismatched: 0 },
      rows: [
        resultRow({ roll: "192001", studentName: "Rahim Uddin", total: 45, mcq: 45, rank: 1, passed: true, fileTotal: 45, fileRank: 1 }),
        resultRow({ roll: "192002", studentName: "Sumi Akter", right: 0, wrong: 0, total: 0, mcq: 0, rank: 2, passed: false, maybeAbsent: true, fileTotal: 0, fileRank: 2 }),
        resultRow({ roll: "192003", studentName: "Kamal Hossain", absent: true, right: null, wrong: null, mcq: null, total: null, rank: null, passed: null, fileTotal: null, fileRank: null }),
      ],
    });
    const { user } = renderDialog(fixtures.masterAdmin, withAbsent);

    await checkWith(user, "MCQ");

    const table = await screen.findByRole("table", { name: "Result sheet" });
    expect(within(table).getByText("Absent")).toBeInTheDocument();
    expect(within(table).getAllByText("Might be absent")).toHaveLength(1);
    expect(screen.getByText("Roll 192003 (Kamal Hossain) is absent.")).toBeInTheDocument();
  });

  it("lists each absent row and each differing row once, a rank-only mismatch included, then the API's warnings", async () => {
    const mixed = preview({
      counts: { rows: 3, matched: 2, absent: 1, mismatched: 1 },
      rows: [
        resultRow({ roll: "192001", studentName: "Rahim Uddin", total: 45, mcq: 45, rank: 1, passed: true, fileTotal: 45, fileRank: 1 }),
        // Same total, a different position in the file: a rank-only mismatch.
        resultRow({ roll: "192002", studentName: "Sumi Akter", total: 30, mcq: 30, rank: 2, passed: false, fileTotal: 30, fileRank: 3, mismatch: true }),
        resultRow({ roll: "192003", studentName: "Kamal Hossain", absent: true, right: null, wrong: null, mcq: null, total: null, rank: null, passed: null, fileTotal: null, fileRank: null }),
      ],
      warnings: [
        {
          code: "ROLL_MATCHED_ZERO_PADDED",
          params: { roll: "123", matched: "0123" },
          message: "Row 9: roll 123 matched 0123.",
        },
      ],
    });
    const { user } = renderDialog(fixtures.masterAdmin, mixed);

    await checkWith(user, "MCQ");

    await screen.findByRole("table", { name: "Result sheet" });
    expect(screen.getAllByText("Roll 192003 (Kamal Hossain) is absent.")).toHaveLength(1);
    expect(screen.getAllByText("Roll 192002: position 3 in the file, 2 calculated")).toHaveLength(1);
    expect(screen.getAllByText("The roll 123 in the file matched roll 0123.")).toHaveLength(1);
    expect(screen.queryByText(/is absent\..*is absent\./)).toBeNull();
  });
});

describe("saving from the preview", () => {
  it("Save as staged posts the same file and choices, then closes with the toast", async () => {
    const { api, onOpenChange, user } = renderDialog(fixtures.masterAdmin, preview());

    await user.type(screen.getByLabelText(/Pass mark/), "40");
    await checkWith(user, "MCQ");
    await user.click(await screen.findByRole("button", { name: "Save as staged" }));

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    const save = api.calls.find((call) => call.method === "POST" && call.path.endsWith(`/${CLASS_STAGED}/results`));
    expect(save).toBeDefined();
    const body = save?.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect(body.get("examType")).toBe("MCQ");
    expect(body.get("passMark")).toBe("40");
    expect(body.get("file")).toBeInstanceOf(File);
    expect(await screen.findByText("Result saved. The Master Admin can publish it.")).toBeInTheDocument();
  });

  it("a save that the data changed under goes back to the preview with its problems, and Check the file again", async () => {
    let checks = 0;
    const { user } = renderDialog(fixtures.masterAdmin, () => {
      checks += 1;
      return preview();
    }, {
      routes: {
        [SAVE]: refused(400, [
          { field: "file", message: "Row 6: the total is above the full marks." },
          { field: "file", message: "Roll 192099 is no longer in this batch." },
        ]),
      },
    });

    await checkWith(user, "MCQ");
    await user.click(await screen.findByRole("button", { name: "Save as staged" }));

    const box = (await screen.findByText("Fix these in the file before saving")).closest("div") as HTMLElement;
    expect(within(box).getByText("Row 6: the total is above the full marks.")).toBeInTheDocument();
    expect(within(box).getByText("Roll 192099 is no longer in this batch.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save as staged" })).toBeNull();

    await user.click(screen.getByRole("button", { name: "Check the file again" }));

    expect(await screen.findByRole("button", { name: "Save as staged" })).toBeEnabled();
    expect(checks).toBe(2);
  });

  it("a save answered RESULT_CHANGED without field errors says the result changed", async () => {
    const { user } = renderDialog(fixtures.masterAdmin, preview(), {
      routes: { [SAVE]: new ErrorBody(409, { code: "RESULT_CHANGED", message: "changed" }) },
    });

    await checkWith(user, "MCQ");
    await user.click(await screen.findByRole("button", { name: "Save as staged" }));

    expect(await screen.findByText("The result changed since it was checked. Check the file again.")).toBeInTheDocument();
  });

  it("offers Save and publish to the Master Admin only", async () => {
    const { user } = renderDialog(fixtures.systemAdmin, preview());

    await checkWith(user, "MCQ");

    await screen.findByRole("button", { name: "Save as staged" });
    expect(screen.queryByRole("button", { name: "Save and publish" })).toBeNull();
  });

  it("Save and publish confirms first, with the students the check says will be told, then saves and publishes", async () => {
    const { api, user } = renderDialog(fixtures.masterAdmin, preview());

    await checkWith(user, "MCQ");
    await user.click(await screen.findByRole("button", { name: "Save and publish" }));

    const confirm = (await screen.findAllByRole("dialog")).at(-1) as HTMLElement;
    expect(within(confirm).getByText("2 students will be told in the app.")).toBeInTheDocument();
    await user.click(within(confirm).getByRole("button", { name: "Save and publish" }));

    await waitFor(() => {
      expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith(`/${CLASS_STAGED}/results`))).toBe(true);
      expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith("/results/publish"))).toBe(true);
    });
  });

  it("when the save works and the publish fails, closes with the toast that says the sheet is staged", async () => {
    const { onOpenChange, user } = renderDialog(fixtures.masterAdmin, preview(), {
      routes: { [PUBLISH]: apiError(409, "CONFLICT", "Nothing is staged.") },
    });

    await checkWith(user, "MCQ");
    await user.click(await screen.findByRole("button", { name: "Save and publish" }));
    const confirm = (await screen.findAllByRole("dialog")).at(-1) as HTMLElement;
    await user.click(within(confirm).getByRole("button", { name: "Save and publish" }));

    expect(
      await screen.findByText(/^Saved as staged\. Publishing did not work: .+\. Publish it from the Results tab\.$/),
    ).toBeInTheDocument();
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
  });
});
