import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ModuleDetails } from "@/components/staff/catalog/module-details";
import { StagedChangesPanel } from "@/components/staff/catalog/staged-changes-panel";
import type { StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const CATEGORY = "cccccccc-0000-0000-0000-000000000001";
const MODULE_ID = "aaaaaaaa-0000-0000-0000-000000000011";
const EXAM_ID = "bbbbbbbb-0000-0000-0000-000000000011";
const TEACHER = "dddddddd-0000-0000-0000-000000000011";

const SUMMARY = `GET /api/v1/live-exam-modules/${MODULE_ID}/staged-summary`;
const PACKAGE_CHANGES = `GET /api/v1/live-exam-modules/${MODULE_ID}/pending-changes`;
const EXAM_CHANGES = `GET /api/v1/live-exams/${EXAM_ID}/pending-changes`;
const PUBLISH = `POST /api/v1/live-exam-modules/${MODULE_ID}/publish-changes`;

// A package that is already published, with a finished final save and nothing staged.
const published: StaffModule = {
  id: MODULE_ID,
  categoryId: CATEGORY,
  titleEn: "Published package",
  titleBn: "প্রকাশিত প্যাকেজ",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "PUBLISHED",
  finallySaved: true,
  position: 1,
  price: 2500,
  pendingChanges: false,
};

// The package's title, one exam with two waiting changes, and a teacher change on that exam.
const waiting = {
  packageChanges: true,
  exams: [{ id: EXAM_ID, titleEn: "Written paper A", titleBn: "লিখিত পরীক্ষা ক", changes: 2 }],
  instructorChanges: true,
};

const nothing = { packageChanges: false, exams: [], instructorChanges: false };

const impact = { papersToRescore: 0, notifiesStudents: false, studentsToNotify: 0 };

const packageList = {
  revisionId: "pkg-rev-1",
  fields: [{ field: "titleEn", before: "Published package", after: "Renamed package" }],
  questions: [],
  paper: null,
  packageExams: [],
  impact,
};

const examList = {
  revisionId: "exam-rev-1",
  fields: [{ field: "instructor", before: null, after: TEACHER }],
  questions: [{ questionId: "q-1", position: 0, change: "STEM", before: "Old stem", after: "New stem" }],
  paper: null,
  packageExams: [],
  impact,
};

describe("the changes waiting on a package", () => {
  it("lists a staged exam and a staged teacher change at the top of the package screen", async () => {
    mockApi({ [SUMMARY]: waiting });
    renderWithProviders(<StagedChangesPanel moduleId={MODULE_ID} />, { user: fixtures.masterAdmin, locale: "en" });

    const panel = await screen.findByRole("region", { name: "Changes waiting" });
    expect(within(panel).getByText("This package")).toBeInTheDocument();
    expect(within(panel).getByText(/Written paper A/)).toBeInTheDocument();
    expect(within(panel).getByText("2 waiting")).toBeInTheDocument();
    expect(within(panel).getByText("Teacher changes")).toBeInTheDocument();
    expect(within(panel).getByRole("link", { name: "Open exam" })).toHaveAttribute("href", `/staff/exams/${EXAM_ID}`);
  });

  it("publishes everything with one publish-changes call, naming the revisions that were read", async () => {
    const { calls } = mockApi({
      [SUMMARY]: waiting,
      [PACKAGE_CHANGES]: packageList,
      [EXAM_CHANGES]: examList,
      [PUBLISH]: { packageChanged: true, examsPublished: 1 },
    });
    const user = userEvent.setup();
    renderWithProviders(<StagedChangesPanel moduleId={MODULE_ID} />, { user: fixtures.masterAdmin, locale: "en" });

    await user.click(await screen.findByRole("button", { name: "Review and publish changes" }));
    const publish = await screen.findByRole("button", { name: "Publish changes" });
    await waitFor(() => expect(publish).toBeEnabled());
    await user.click(publish);

    await waitFor(() => expect(calls.filter((call) => call.method === "POST" && call.path.endsWith("/publish-changes"))).toHaveLength(1));
    const post = calls.find((call) => call.method === "POST");
    expect(post?.body).toEqual({ revisionId: "pkg-rev-1", examRevisionIds: { [EXAM_ID]: "exam-rev-1" } });
  });

  it("a published package with nothing staged says that nothing is waiting, and offers no publish", async () => {
    mockApi({ [SUMMARY]: nothing });
    renderWithProviders(<ModuleDetails module={published} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(
      await screen.findByText(
        "Nothing is waiting. Edits you save here, in its exams or in its teachers wait until you publish them.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Publish changes" })).not.toBeInTheDocument();
  });

  it("a System Admin sees the same waiting list and the note that the Master Admin publishes, and no publish button", async () => {
    const { calls } = mockApi({ [SUMMARY]: waiting });
    renderWithProviders(<StagedChangesPanel moduleId={MODULE_ID} />, { user: fixtures.systemAdmin, locale: "en" });

    const panel = await screen.findByRole("region", { name: "Changes waiting" });
    expect(within(panel).getByText(/Written paper A/)).toBeInTheDocument();
    expect(within(panel).getByText("The Master Admin publishes these changes.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Review and publish changes" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Publish changes" })).not.toBeInTheDocument();
    expect(calls.some((call) => call.method === "POST")).toBe(false);
  });

  it("a System Admin's package screen with a staged change shows no Publish changes button", async () => {
    const { calls } = mockApi({ [SUMMARY]: waiting });
    renderWithProviders(<ModuleDetails module={{ ...published, pendingChanges: true }} />, {
      user: fixtures.systemAdmin,
      locale: "en",
    });

    await waitFor(() => expect(calls.some((call) => call.path.endsWith("/staged-summary"))).toBe(true));
    expect(screen.queryByRole("button", { name: "Publish changes" })).not.toBeInTheDocument();
  });
});
