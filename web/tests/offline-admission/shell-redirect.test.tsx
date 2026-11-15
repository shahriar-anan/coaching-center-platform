import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StudentShell } from "@/components/shell/student-shell";
import { fixtures, mockApi, renderWithProviders, routerState } from "../helpers";
import { admissionView } from "./admission-fixture";

/** The student shell with one admissions list. The unread count is the only other request the shell makes. */
function renderShell(route: string, admissions: unknown) {
  const api = mockApi({
    "GET /api/v1/me/notices/unread-count": { count: 0 },
    "GET /api/v1/me/admissions": admissions,
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

/** Lets the admissions answer arrive. The checks below are negative ones, so they need the answer to have landed. */
async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 50));
}

describe("a walk-in student whose admission is not onboarded is held on the admission pages (plan 4.5)", () => {
  it("is sent to the form from another student page, with no tab bar and no page content", async () => {
    renderShell("/my-modules", [admissionView("AWAITING_FORM", { source: "DESK" })]);

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/admission/form"));
    expect(screen.queryByText("page content")).not.toBeInTheDocument();
    await waitFor(() => expect(tabBar()).toBeNull());
  });

  it("is sent from the home page too", async () => {
    renderShell("/", [admissionView("SUBMITTED", { source: "DESK" })]);

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/admission/form"));
  });

  it("stays on the admission form, which shows the content and no tab bar", async () => {
    renderShell("/admission/form", [admissionView("CHANGES_REQUESTED", { source: "DESK", changesReason: "Fix the photo." })]);

    expect(await screen.findByText("page content")).toBeInTheDocument();
    await settle();
    await waitFor(() => expect(tabBar()).toBeNull());
    expect(routerState.replace).not.toHaveBeenCalled();
  });

  it("stays on the print view, too", async () => {
    renderShell("/admission/print", [admissionView("AWAITING_FORM", { source: "DESK" })]);

    expect(await screen.findByText("page content")).toBeInTheDocument();
    await settle();
    expect(routerState.replace).not.toHaveBeenCalled();
  });
});

describe("everyone else keeps the whole site (admission-access-plan.md)", () => {
  async function expectOpen(api: ReturnType<typeof renderShell>) {
    expect(await screen.findByText("page content")).toBeInTheDocument();
    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/me/admissions")).toBe(true));
    await settle();
    expect(routerState.replace).not.toHaveBeenCalled();
    expect(tabBar()).not.toBeNull();
  }

  it("a student with no admission", async () => {
    await expectOpen(renderShell("/my-modules", []));
  });

  it("a student who applied from their account, while the office reviews it", async () => {
    await expectOpen(renderShell("/my-modules", [admissionView("SUBMITTED", { source: "ACCOUNT" })]));
  });

  it("a student who applied from their account, once onboarded but before entering the join code", async () => {
    await expectOpen(renderShell("/", [admissionView("READY", { source: "ACCOUNT", joinCodePending: true })]));
  });

  it("a student who applied online", async () => {
    await expectOpen(renderShell("/results", [admissionView("CHANGES_REQUESTED", { source: "PUBLIC" })]));
  });

  it("a student with an onboarded batch and a desk admission for another batch still in progress", async () => {
    await expectOpen(
      renderShell("/my-modules", [
        admissionView("AWAITING_FORM", { id: "adm-2", source: "DESK" }),
        admissionView("ONBOARDED", { source: "DESK" }),
      ]),
    );
  });

  it("staff never ask for admissions", async () => {
    const api = mockApi({ "GET /api/v1/me/admissions": [admissionView("AWAITING_FORM", { source: "DESK" })] });
    renderWithProviders(
      <StudentShell>
        <p>page content</p>
      </StudentShell>,
      { user: fixtures.masterAdmin, locale: "en", route: "/my-modules" },
    );

    expect(await screen.findByText("page content")).toBeInTheDocument();
    await settle();
    expect(api.calls.some((call) => call.path === "/api/v1/me/admissions")).toBe(false);
    expect(routerState.replace).not.toHaveBeenCalled();
  });
});
