import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StudentShell } from "@/components/shell/student-shell";
import { apiError, fixtures, mockApi, renderWithProviders, routerState } from "../helpers";
import { admissionView } from "./admission-fixture";

/** The student shell with one admission answer. The unread count is the only other request the shell makes. */
function renderShell(route: string, admission: unknown) {
  const api = mockApi({
    "GET /api/v1/me/notices/unread-count": { count: 0 },
    "GET /api/v1/me/admission": admission,
  });
  renderWithProviders(
    <StudentShell>
      <p>page content</p>
    </StudentShell>,
    { user: fixtures.student, locale: "en", route },
  );
  return api;
}

const tabBar = () => document.querySelector('[data-tabbar="true"]');

/** Lets the admission answer arrive. The check below is a negative one, so it needs the answer to have landed. */
async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 50));
}

describe("a pending student is held on the admission form (plan 4.5)", () => {
  it("is sent to /admission from another student page, with no tab bar and no page content", async () => {
    renderShell("/my-modules", admissionView("AWAITING_FORM"));

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/admission"));
    expect(screen.queryByText("page content")).not.toBeInTheDocument();
    await waitFor(() => expect(tabBar()).toBeNull());
  });

  it("is sent from the home page too, for every pending state", async () => {
    renderShell("/", admissionView("SUBMITTED"));

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/admission"));
  });

  it("stays on the admission form, which shows the content and no tab bar", async () => {
    renderShell("/admission", admissionView("CHANGES_REQUESTED", { changesReason: "Fix the photo." }));

    expect(await screen.findByText("page content")).toBeInTheDocument();
    await waitFor(() => expect(tabBar()).toBeNull());
    await settle();
    expect(routerState.replace).not.toHaveBeenCalled();
  });

  it("stays on the print view, too", async () => {
    renderShell("/admission/print", admissionView("AWAITING_FORM"));

    expect(await screen.findByText("page content")).toBeInTheDocument();
    await settle();
    expect(routerState.replace).not.toHaveBeenCalled();
  });
});

describe("everyone else is unaffected", () => {
  it("a student with no admission keeps every page and the tab bar", async () => {
    const api = renderShell("/my-modules", apiError(404, "RESOURCE_NOT_FOUND", "Resource not found."));

    expect(await screen.findByText("page content")).toBeInTheDocument();
    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/me/admission")).toBe(true));
    await settle();
    expect(routerState.replace).not.toHaveBeenCalled();
    expect(tabBar()).not.toBeNull();
  });

  it("a student whose admission is onboarded keeps every page and the tab bar", async () => {
    renderShell("/my-modules", admissionView("ONBOARDED"));

    expect(await screen.findByText("page content")).toBeInTheDocument();
    await settle();
    expect(routerState.replace).not.toHaveBeenCalled();
    expect(tabBar()).not.toBeNull();
  });

  it("staff never ask for an admission", async () => {
    const api = mockApi({ "GET /api/v1/me/admission": admissionView("AWAITING_FORM") });
    renderWithProviders(
      <StudentShell>
        <p>page content</p>
      </StudentShell>,
      { user: fixtures.masterAdmin, locale: "en", route: "/my-modules" },
    );

    expect(await screen.findByText("page content")).toBeInTheDocument();
    await settle();
    expect(api.calls.some((call) => call.path === "/api/v1/me/admission")).toBe(false);
    expect(routerState.replace).not.toHaveBeenCalled();
  });
});
