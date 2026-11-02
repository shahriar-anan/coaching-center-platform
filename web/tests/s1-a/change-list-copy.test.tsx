import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ChangeListDialog } from "@/components/staff/catalog/change-list-dialog";
import type { PendingChangeList } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const KEEP_SCORE = "Students who already submitted keep their score. Students who are sitting now keep the questions they started with.";
const NOT_RE_JUDGED = "Results already posted are not re-judged.";
const MAXIMUM_REFUSED = "A maximum below a mark already given or waiting to be marked is refused.";

const empty = {
  revisionId: "r1",
  fields: [],
  questions: [],
  paper: null,
  packageExams: [],
  impact: { papersToRescore: 0, notifiesStudents: false, studentsToNotify: 0 },
} as PendingChangeList;

function renderList(changes: PendingChangeList, written: boolean) {
  mockApi({ "GET /api/v1/live-exam-categories": [] });
  renderWithProviders(
    <ChangeListDialog
      open
      onOpenChange={() => undefined}
      changes={changes}
      written={written}
      onPublish={async () => undefined}
      onFailed={() => undefined}
    />,
    { user: fixtures.masterAdmin, locale: "en" },
  );
}

describe("change list copy for a withdrawn or published exam (D10, D17)", () => {
  it("says submitted MCQ scores stay and sitting students keep their questions, and no longer offers a re-score", async () => {
    renderList(
      {
        ...empty,
        questions: [{ questionId: "q1", position: 1, change: "UPDATED", before: "a", after: "b" }],
      } as PendingChangeList,
      false,
    );

    expect(await screen.findByText(KEEP_SCORE)).toBeInTheDocument();
    expect(screen.queryByText(/re-score/)).not.toBeInTheDocument();
  });

  it("says posted results are not re-judged when a written exam's pass percent changes", async () => {
    renderList({ ...empty, fields: [{ field: "passPercent", before: "40", after: "50" }] } as PendingChangeList, true);

    expect(await screen.findByText(NOT_RE_JUDGED)).toBeInTheDocument();
  });

  it("does not show the posted-results line for a pass percent change on an MCQ exam", async () => {
    renderList({ ...empty, fields: [{ field: "passPercent", before: "40", after: "50" }] } as PendingChangeList, false);

    expect(await screen.findByText("Pass percent")).toBeInTheDocument();
    expect(screen.queryByText(NOT_RE_JUDGED)).not.toBeInTheDocument();
  });

  it("says a maximum below a mark already given is refused, on a written exam", async () => {
    renderList({ ...empty, fields: [{ field: "maximum", before: "50", after: "40" }] } as PendingChangeList, true);

    expect(await screen.findByText(MAXIMUM_REFUSED)).toBeInTheDocument();
  });

  it("shows none of the three lines for a change that is about neither", async () => {
    renderList({ ...empty, fields: [{ field: "titleEn", before: "Old", after: "New" }] } as PendingChangeList, true);

    expect(await screen.findByText("English title")).toBeInTheDocument();
    expect(screen.queryByText(KEEP_SCORE)).not.toBeInTheDocument();
    expect(screen.queryByText(NOT_RE_JUDGED)).not.toBeInTheDocument();
    expect(screen.queryByText(MAXIMUM_REFUSED)).not.toBeInTheDocument();
  });
});
