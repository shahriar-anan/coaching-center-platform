import { Suspense } from "react";
import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ModulePage from "@/app/(staff)/staff/modules/[id]/page";
import type { StaffModule } from "@/lib/api/exam/staff-catalog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

/*
 * The package screen has no Instructor tab: a teacher is set per exam, on the exam's own page.
 * The package's Exams tab shows each exam's teacher (see exam-teacher-column.test.tsx).
 */

const hoisted = vi.hoisted(() => ({ id: "aaaaaaaa-0000-4000-8000-000000000001" }));

// The package page reads its id from the route. The other next/navigation helpers stay as the setup file has them.
vi.mock("next/navigation", async () => {
  const { routerState: router } = await import("../helpers/router");
  return {
    useRouter: () => router,
    usePathname: () => router.pathname,
    useSearchParams: () => new URLSearchParams(router.search),
    useParams: () => hoisted,
    redirect: vi.fn(),
    notFound: vi.fn(),
  };
});

const moduleId = hoisted.id;

const moduleFixture: StaffModule = {
  id: moduleId,
  categoryId: "cccccccc-0000-4000-8000-000000000001",
  titleEn: "19th BCS",
  titleBn: "১৯তম বিজেএস",
  descriptionEn: null,
  descriptionBn: null,
  accessMode: "MANUAL",
  status: "PUBLISHED",
  finallySaved: true,
  position: 1,
  price: 2500,
  mcqCount: 1,
  writtenCount: 1,
};

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

describe("package screen: no Instructor tab (teachers are per exam)", () => {
  it("a Master Admin sees the package tabs without an Instructor tab", async () => {
    mockApi({
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
      [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: emptyPage,
    });
    renderWithProviders(
      <Suspense fallback={null}>
        <ModulePage />
      </Suspense>,
      { user: fixtures.masterAdmin, locale: "en", route: `/staff/modules/${moduleId}` },
    );

    expect(await screen.findByRole("tab", { name: "Exams" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Details" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Instructor" })).not.toBeInTheDocument();
  });

  it("an old ?tab=instructor link opens the details, and offers no instructor picker", async () => {
    mockApi({
      [`GET /api/v1/live-exam-modules/${moduleId}`]: moduleFixture,
      [`GET /api/v1/live-exam-modules/${moduleId}/exams`]: emptyPage,
    });
    renderWithProviders(
      <Suspense fallback={null}>
        <ModulePage />
      </Suspense>,
      { user: fixtures.masterAdmin, locale: "en", route: `/staff/modules/${moduleId}?tab=instructor` },
    );

    expect(await screen.findByRole("tab", { name: "Details" })).toBeInTheDocument();
    expect(screen.queryByText("Assign instructor")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Assign instructor" })).not.toBeInTheDocument();
  });
});
