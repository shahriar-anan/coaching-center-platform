import { Suspense } from "react";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import ParticipationPage from "@/app/(staff)/staff/participation/page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const EXAM = "ffffffff-0000-0000-0000-000000000031";
const MODULE = "aaaaaaaa-0000-0000-0000-000000000031";

const pageOf = (content: unknown[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: 1 });

/** A written exam with two papers waiting to be marked. */
const waitingRow = {
  examId: EXAM,
  titleEn: "Evidence Act",
  titleBn: "সাক্ষ্য আইন",
  moduleId: MODULE,
  moduleTitleEn: "Law",
  moduleTitleBn: "আইন",
  examType: "WRITTEN",
  liveAt: "2026-10-24T06:00:00Z",
  endAt: "2026-10-24T07:00:00Z",
  sitting: 0,
  submitted: 2,
  failedToSubmit: 0,
  awaitingMarking: 2,
  draftsAwaitingPublish: 0,
  published: 0,
  retakes: 0,
};

function renderPage(user: typeof fixtures.instructor | typeof fixtures.systemAdmin | typeof fixtures.masterAdmin, route: string) {
  mockApi({
    "GET /api/v1/live-exam-participation": pageOf([waitingRow]),
    "GET /api/v1/live-exam-modules": pageOf([]),
  });
  return renderWithProviders(
    <Suspense fallback={null}>
      <ParticipationPage />
    </Suspense>,
    { user, locale: "en", route },
  );
}

describe("the participation page for a teacher", () => {
  it("has no Drafts tab", async () => {
    renderPage(fixtures.instructor, "/staff/participation");

    const tabs = await screen.findByRole("tablist");
    expect(within(tabs).queryByRole("tab", { name: /Drafts/ })).toBeNull();
    expect(within(tabs).getByRole("tab", { name: /To mark/ })).toBeInTheDocument();
  });

  it("a Drafts link in the address shows the exam list, not the drafts", async () => {
    renderPage(fixtures.instructor, "/staff/participation?focus=drafts");

    expect((await screen.findAllByRole("link", { name: "Open marking" }))[0]).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Open module" })).toBeNull();
    expect(screen.queryByRole("tab", { name: /Drafts/ })).toBeNull();
  });

  it("the Open marking button goes to the exam's submissions, waiting papers first", async () => {
    renderPage(fixtures.instructor, "/staff/participation");

    expect((await screen.findAllByRole("link", { name: "Open marking" }))[0]).toHaveAttribute(
      "href",
      `/staff/submissions?exam=${EXAM}&status=AWAITING`,
    );
  });
});

describe("the participation page for a System Admin", () => {
  it("has no Open marking button, and offers the results instead", async () => {
    renderPage(fixtures.systemAdmin, "/staff/participation");

    expect((await screen.findAllByRole("link", { name: "View results" }))[0]).toHaveAttribute("href", `/staff/exams/${EXAM}/results`);
    expect(screen.queryByRole("link", { name: "Open marking" })).toBeNull();
  });

  it("the row menu has no Open the scripts item", async () => {
    const user = userEvent.setup();
    renderPage(fixtures.systemAdmin, "/staff/participation");

    await user.click((await screen.findAllByRole("button", { name: /Actions for Evidence Act/ }))[0]);
    expect(screen.queryByRole("menuitem", { name: "Open the scripts" })).toBeNull();
    expect(await screen.findByRole("menuitem", { name: "Open the results" })).toBeInTheDocument();
  });

  it("the Master Admin keeps the Open marking button", async () => {
    renderPage(fixtures.masterAdmin, "/staff/participation");

    expect((await screen.findAllByRole("link", { name: "Open marking" }))[0]).toHaveAttribute(
      "href",
      `/staff/submissions?exam=${EXAM}&status=AWAITING`,
    );
  });
});
