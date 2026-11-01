import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ExamRow } from "@/components/staff/exams/exam-row";
import { ExamGroupSection } from "@/components/staff/exams/exam-group";
import type { ExamGroup } from "@/components/staff/exams/exam-groups";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const examId = "eeeeeeee-0000-0000-0000-000000000011";
const packageId = "aaaaaaaa-0000-0000-0000-000000000011";

function exam(overrides: Partial<StaffExam> = {}): StaffExam {
  return {
    id: examId,
    moduleId: packageId,
    examType: "WRITTEN",
    paperLanguage: "BN",
    titleEn: "Evidence Act paper",
    titleBn: "সাক্ষ্য আইন",
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
    rulesBn: null,
    liveAt: null,
    endAt: null,
    durationMinutes: 60,
    passPercent: 40,
    maximum: 100,
    status: "PUBLISHED",
    finallySaved: true,
    price: null,
    questionPdf: true,
    questions: null,
    packages: [{ id: packageId, titleEn: "Law", titleBn: "আইন" }],
    pendingChanges: false,
    mode: "LIVE",
    ...overrides,
  } as StaffExam;
}

async function openMenu(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "More actions" }));
}

describe("the exam row for a teacher", () => {
  it("the Open button opens the exam's submissions, not the exam page", () => {
    mockApi({});
    renderWithProviders(
      <ul>
        <ExamRow exam={exam()} canCopy={false} canRemove={false} onAction={() => undefined} />
      </ul>,
      { user: fixtures.instructor, locale: "en" },
    );

    expect(screen.getByRole("link", { name: "Open" })).toHaveAttribute("href", `/staff/submissions?exam=${examId}`);
  });

  it("the Master Admin's Open button still opens the exam page", () => {
    mockApi({});
    renderWithProviders(
      <ul>
        <ExamRow exam={exam()} canCopy={false} canRemove={false} onAction={() => undefined} />
      </ul>,
      { user: fixtures.masterAdmin, locale: "en" },
    );

    expect(screen.getByRole("link", { name: "Open" })).toHaveAttribute("href", `/staff/exams/${examId}`);
  });

  it("a written exam's menu has Results, and no Leaderboard until the results are released", async () => {
    const user = userEvent.setup();
    mockApi({});
    renderWithProviders(
      <ul>
        <ExamRow exam={exam()} canCopy={false} canRemove={false} onAction={() => undefined} />
      </ul>,
      { user: fixtures.instructor, locale: "en" },
    );
    await openMenu(user);

    expect(await screen.findByRole("menuitem", { name: "Results" })).toHaveAttribute("href", `/staff/exams/${examId}/results`);
    expect(screen.queryByRole("menuitem", { name: "Leaderboard" })).toBeNull();
  });

  it("a written exam's Leaderboard appears once the results are released", async () => {
    const user = userEvent.setup();
    mockApi({});
    renderWithProviders(
      <ul>
        <ExamRow exam={exam({ resultsReleased: true })} canCopy={false} canRemove={false} onAction={() => undefined} />
      </ul>,
      { user: fixtures.instructor, locale: "en" },
    );
    await openMenu(user);

    expect(await screen.findByRole("menuitem", { name: "Leaderboard" })).toHaveAttribute(
      "href",
      `/staff/exams/${examId}/leaderboard`,
    );
  });

  it("an MCQ exam has no Results item for a teacher, even when its results are released", async () => {
    const user = userEvent.setup();
    mockApi({});
    renderWithProviders(
      <ul>
        <ExamRow exam={exam({ examType: "MCQ", questionPdf: null, questions: [], resultsReleased: true })} canCopy={false} canRemove={false} onAction={() => undefined} />
      </ul>,
      { user: fixtures.instructor, locale: "en" },
    );
    await openMenu(user);

    expect(await screen.findByRole("menuitem", { name: "Leaderboard" })).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Results" })).toBeNull();
  });
});

describe("the exam group header for a teacher", () => {
  const group: ExamGroup = {
    key: `package:${packageId}`,
    kind: "package",
    titleEn: "Law",
    titleBn: "আইন",
    exams: [exam()],
  };

  function renderGroup(user: typeof fixtures.instructor | typeof fixtures.masterAdmin) {
    mockApi({});
    return renderWithProviders(
      <ExamGroupSection
        group={group}
        defaultOpen
        categoryName={() => undefined}
        canCopy={false}
        canRemove={false}
        onAction={() => undefined}
      />,
      { user, locale: "en" },
    );
  }

  it("shows no Open package button to a teacher", () => {
    renderGroup(fixtures.instructor);
    expect(screen.queryByRole("link", { name: "Open package" })).toBeNull();
    expect(screen.getByRole("link", { name: "Open" })).toHaveAttribute("href", `/staff/submissions?exam=${examId}`);
  });

  it("keeps the Open package button for the Master Admin", () => {
    renderGroup(fixtures.masterAdmin);
    expect(screen.getByRole("link", { name: "Open package" })).toHaveAttribute("href", `/staff/modules/${packageId}`);
  });
});
