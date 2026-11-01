import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ExamRow } from "@/components/staff/exams/exam-row";
import { ModuleList } from "@/components/staff/catalog/module-list";
import type { StaffExam, StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const examId = "eeeeeeee-0000-0000-0000-000000000001";
const moduleId = "aaaaaaaa-0000-0000-0000-000000000001";
const categoryId = "cccccccc-0000-0000-0000-000000000001";

const exam = {
  id: examId,
  moduleId: null,
  paperLanguage: "BN",
  titleEn: "Penal Code Mock",
  titleBn: "দণ্ডবিধি মক",
  descriptionEn: null,
  descriptionBn: null,
  rulesEn: null,
  rulesBn: null,
  examType: "MCQ",
  mode: "LIVE",
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
  packages: [],
  pendingChanges: false,
  categoryId,
} as unknown as StaffExam;

const moduleFixture: StaffModule = {
  id: moduleId,
  categoryId,
  titleEn: "19th BCS",
  titleBn: "১৯তম বিজেএস",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "PUBLISHED",
  finallySaved: true,
  position: 1,
  price: 2500,
  instructor: null,
  mcqCount: 2,
  writtenCount: 1,
};

const categories = [
  { id: categoryId, parentId: null, titleEn: "BCS", titleBn: "বিজেএস", position: 1, archived: false, children: [], modules: [] },
];

describe("the Reviews link in the exam and package menus", () => {
  it("an exam's menu links to its reviews for the Master Admin", async () => {
    const user = userEvent.setup();
    mockApi({});
    renderWithProviders(
      <ul>
        <ExamRow exam={exam} canCopy={false} canRemove={false} onAction={() => undefined} />
      </ul>,
      { user: fixtures.masterAdmin, locale: "en" },
    );
    await user.click(screen.getByRole("button", { name: "More actions" }));
    expect(await screen.findByRole("menuitem", { name: "Reviews" })).toHaveAttribute(
      "href",
      `/staff/reviews?kind=exam&targetId=${examId}`,
    );
  });

  it("an exam's menu links to its reviews for a teacher as well", async () => {
    const user = userEvent.setup();
    mockApi({});
    renderWithProviders(
      <ul>
        <ExamRow exam={exam} canCopy={false} canRemove={false} onAction={() => undefined} />
      </ul>,
      { user: fixtures.instructor, locale: "en" },
    );
    await user.click(screen.getByRole("button", { name: "More actions" }));
    expect(await screen.findByRole("menuitem", { name: "Reviews" })).toHaveAttribute(
      "href",
      `/staff/reviews?kind=exam&targetId=${examId}`,
    );
  });

  it("a package's menu links to its reviews", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/live-exam-categories": categories,
      "GET /api/v1/live-exam-modules": { content: [moduleFixture], page: 0, size: 100, totalElements: 1, totalPages: 1 },
    });
    renderWithProviders(<ModuleList />, { user: fixtures.masterAdmin, locale: "en" });
    // The table draws each package twice, as a desktop row and a stacked card; either opens the same menu.
    await user.click((await screen.findAllByRole("button", { name: "Actions for 19th BCS" }))[0]);
    expect(await screen.findByRole("menuitem", { name: "Reviews" })).toHaveAttribute(
      "href",
      `/staff/reviews?kind=package&targetId=${moduleId}`,
    );
  });
});
