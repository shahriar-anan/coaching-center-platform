import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ChangeListDialog } from "@/components/staff/catalog/change-list-dialog";
import type { PendingChangeList } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const KEEP_SCORE = "Students who already submitted keep their score. Students who are sitting now keep the questions they started with.";
const KEEP_RULES = "Students who already started keep the full marks, pass mark and negative mark they started with.";

const empty = {
  revisionId: "r1",
  fields: [],
  questions: [],
  paper: null,
  packageExams: [],
  impact: { papersToRescore: 0, notifiesStudents: false, studentsToNotify: 0 },
} as PendingChangeList;

function renderList(changes: PendingChangeList) {
  mockApi({ "GET /api/v1/live-exam-categories": [] });
  renderWithProviders(
    <ChangeListDialog
      open
      onOpenChange={() => undefined}
      changes={changes}
      onPublish={async () => undefined}
      onFailed={() => undefined}
    />,
    { user: fixtures.masterAdmin, locale: "en" },
  );
}

describe("change list copy for a withdrawn or published exam (D9, D10)", () => {
  it("says submitted MCQ scores stay and sitting students keep their questions, and no longer offers a re-score", async () => {
    renderList({
      ...empty,
      questions: [{ questionId: "q1", position: 1, change: "UPDATED", before: "a", after: "b" }],
    } as PendingChangeList);

    expect(await screen.findByText(KEEP_SCORE)).toBeInTheDocument();
    expect(screen.queryByText(/re-score/)).not.toBeInTheDocument();
  });

  it.each(["passPercent", "maximum", "negativeMarks"])("says students who started keep their rules when %s changes", async (field) => {
    renderList({ ...empty, fields: [{ field, before: "40", after: "50" }] } as PendingChangeList);

    expect(await screen.findByText(KEEP_RULES)).toBeInTheDocument();
  });

  it("shows neither line for a change that is about neither", async () => {
    renderList({ ...empty, fields: [{ field: "titleEn", before: "Old", after: "New" }] } as PendingChangeList);

    expect(await screen.findByText("English title")).toBeInTheDocument();
    expect(screen.queryByText(KEEP_SCORE)).not.toBeInTheDocument();
    expect(screen.queryByText(KEEP_RULES)).not.toBeInTheDocument();
  });
});
