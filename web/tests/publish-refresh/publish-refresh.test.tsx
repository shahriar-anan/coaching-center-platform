import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { catalogKeys, useCategoryTree, usePublicExam } from "@/lib/api/exam/catalog";
import { sittingKeys } from "@/lib/api/exam/sitting";
import { usePublishPendingChanges } from "@/lib/api/exam/staff-catalog";
import { mockApi, renderWithProviders } from "../helpers";

/** The student's open exam page: the public read of the exam. */
function StudentExamTitle() {
  const exam = usePublicExam("ex-1");
  return <p>{exam.data ? exam.data.titleEn : "loading"}</p>;
}

/** The Master Admin's publish button for the exam, naming the revision it read. */
function PublishProbe() {
  const publish = usePublishPendingChanges("exam", "ex-1");
  return (
    <button type="button" onClick={() => void publish.mutateAsync("rev-1")}>
      send publish
    </button>
  );
}

function TreeProbe() {
  useCategoryTree(null);
  return null;
}

const publicExam = (titleEn: string) => ({
  id: "ex-1",
  moduleId: "m-1",
  examType: "MCQ",
  titleEn,
  titleBn: titleEn,
  descriptionEn: "d",
  descriptionBn: "d",
  rulesEn: "r",
  rulesBn: "r",
  paperLanguage: "EN",
  liveAt: null,
  endAt: null,
  durationMinutes: 40,
  price: null,
  access: null,
  categoryId: "c-1",
  mode: "LIVE",
  phase: "UPCOMING",
  popular: false,
  previewEnabled: false,
  pricing: null,
  packages: [],
  favourite: null,
  coverUrl: null,
  ratingAverage: null,
  ratingCount: 0,
});

describe("a staff publish reaches the student reads in the same client", () => {
  it("the open student exam shows the published title without a reload", async () => {
    const user = userEvent.setup();
    let published = false;
    mockApi({
      "GET /api/v1/public/live-exams/ex-1": () => publicExam(published ? "After" : "Before"),
      "POST /api/v1/live-exams/ex-1/publish": () => {
        published = true;
        return { id: "ex-1", moduleId: "m-1", status: "PUBLISHED", titleEn: "After" };
      },
    });
    renderWithProviders(
      <>
        <StudentExamTitle />
        <PublishProbe />
      </>,
      { locale: "en" },
    );
    expect(await screen.findByText("Before")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "send publish" }));

    expect(await screen.findByText("After")).toBeInTheDocument();
  });

  it("a publish invalidates the catalog, the routine and the student's own exam, not only the staff keys", async () => {
    const user = userEvent.setup();
    mockApi({
      "POST /api/v1/live-exams/ex-1/publish": { id: "ex-1", moduleId: "m-1", status: "PUBLISHED", titleEn: "After" },
      "GET /api/v1/live-exams/ex-1/pending-changes": { revisionId: "rev-1", fields: [], questions: [], impact: {} },
    });
    const { queryClient } = renderWithProviders(<PublishProbe />, { locale: "en" });
    const spy = vi.spyOn(queryClient, "invalidateQueries");

    await user.click(screen.getByRole("button", { name: "send publish" }));

    await waitFor(() => expect(spy).toHaveBeenCalledWith({ queryKey: catalogKeys.all }));
    expect(spy).toHaveBeenCalledWith({ queryKey: sittingKeys.exam("ex-1") });
    expect(spy).toHaveBeenCalledWith({ queryKey: ["live-exam-preview"] });
  });
});

describe("the public catalog reads", () => {
  it("refetch when the tab regains focus, and stay fresh for one minute", async () => {
    mockApi({
      "GET /api/v1/public/live-exam-categories": [],
      "GET /api/v1/public/live-exams/ex-1": () => publicExam("Before"),
    });
    const { queryClient } = renderWithProviders(
      <>
        <TreeProbe />
        <StudentExamTitle />
      </>,
      { locale: "en" },
    );
    await waitFor(() =>
      expect(queryClient.getQueryCache().find({ queryKey: catalogKeys.tree(null) })?.state.status).toBe("success"),
    );
    await screen.findByText("Before");

    const tree = queryClient.getQueryCache().find({ queryKey: catalogKeys.tree(null) });
    const exam = queryClient.getQueryCache().find({ queryKey: [...catalogKeys.publicExam("ex-1"), false] });
    expect(tree?.observers[0]?.options.refetchOnWindowFocus).toBe(true);
    expect(tree?.observers[0]?.options.staleTime).toBe(60_000);
    expect(exam?.observers[0]?.options.refetchOnWindowFocus).toBe(true);
    expect(exam?.observers[0]?.options.staleTime).toBe(60_000);
  });
});
