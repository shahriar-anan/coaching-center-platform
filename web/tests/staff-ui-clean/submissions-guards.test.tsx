import { Suspense } from "react";
import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import StaffSubmissionsPage from "@/app/(staff)/staff/submissions/page";
import StaffSubmissionPage from "@/app/(staff)/staff/submissions/[participantId]/page";
import StaffModuleSubmissionsPage from "@/app/(staff)/staff/submissions/m/[moduleId]/page";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

const moduleId = "aaaaaaaa-0000-0000-0000-000000000021";

/** The not-found page RequireStaff shows for a role the page does not admit. */
const NOT_FOUND = "Page not found";

/**
 * A params promise that is already resolved. React's `use` reads a fulfilled `status` at once, so the page does not
 * stay on its Suspense fallback in the test (a plain Promise.resolve does, because the retry never runs under jsdom).
 */
function resolvedParams<T>(value: T): Promise<T> {
  return Object.assign(Promise.resolve(value), { status: "fulfilled" as const, value });
}

describe("the submissions pages refuse a System Admin", () => {
  it("the browser page shows the not-found page to a System Admin", async () => {
    mockApi({});
    renderWithProviders(<StaffSubmissionsPage />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/submissions" });

    expect(await screen.findByText(NOT_FOUND)).toBeInTheDocument();
  });

  it("the paper page shows the not-found page to a System Admin", async () => {
    mockApi({});
    const params = resolvedParams({ participantId: "11111111-0000-0000-0000-000000000021" });
    renderWithProviders(
      <Suspense fallback={null}>
        <StaffSubmissionPage params={params} />
      </Suspense>,
      { user: fixtures.systemAdmin, locale: "en", route: "/staff/submissions/11111111-0000-0000-0000-000000000021" },
    );

    expect(await screen.findByText(NOT_FOUND)).toBeInTheDocument();
  });

  it("the old per-package address shows the not-found page to a System Admin, and does not redirect", async () => {
    mockApi({});
    const params = resolvedParams({ moduleId });
    renderWithProviders(
      <Suspense fallback={null}>
        <StaffModuleSubmissionsPage params={params} />
      </Suspense>,
      { user: fixtures.systemAdmin, locale: "en", route: `/staff/submissions/m/${moduleId}` },
    );

    expect(await screen.findByText(NOT_FOUND)).toBeInTheDocument();
    expect(routerState.replace).not.toHaveBeenCalled();
  });

  it("the old per-package address still redirects the Master Admin to the browser with the package chosen", async () => {
    mockApi({});
    const params = resolvedParams({ moduleId });
    renderWithProviders(
      <Suspense fallback={null}>
        <StaffModuleSubmissionsPage params={params} />
      </Suspense>,
      { user: fixtures.masterAdmin, locale: "en", route: `/staff/submissions/m/${moduleId}` },
    );

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith(expect.stringContaining(`module=${moduleId}`)));
  });
});
