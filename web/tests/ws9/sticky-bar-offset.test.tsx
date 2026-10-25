import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { StudentShell } from "@/components/shell/student-shell";
import { StickyActionBar } from "@/components/ui/sticky-action-bar";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

function Page() {
  return (
    <>
      <p>পরীক্ষার তথ্য</p>
      <StickyActionBar>
        <button type="button">পরীক্ষা শুরু করুন</button>
      </StickyActionBar>
    </>
  );
}

describe("StickyActionBar above the student tab bar (FN-2)", () => {
  it("sits above the tab bar: the shell publishes its height and the bar follows it", () => {
    mockApi({ "GET /api/v1/me/notices/unread-count": { count: 0 } });
    renderWithProviders(
      <StudentShell>
        <Page />
      </StudentShell>,
      { user: fixtures.student, route: "/exam/1" },
    );
    const surface = screen.getByRole("button", { name: "পরীক্ষা শুরু করুন" }).closest("[data-surface]");
    expect(surface).toHaveAttribute("data-tabbar", "true");
    expect((surface as HTMLElement).style.getPropertyValue("--tabbar-h")).toContain("3.5rem");
    expect(screen.getByRole("navigation", { name: "নিচের মেনু" })).toBeInTheDocument();
    const bar = screen.getByRole("button", { name: "পরীক্ষা শুরু করুন" }).closest("[data-sticky-bar]");
    expect(bar).not.toBeNull();
  });

  it("with no tab bar (the paper screen) the bar sits on the bottom edge and publishes no offset", () => {
    mockApi({ "GET /api/v1/me/notices/unread-count": { count: 0 } });
    renderWithProviders(
      <StudentShell>
        <Page />
      </StudentShell>,
      { user: fixtures.student, route: "/exam/1/sit" },
    );
    const surface = screen.getByRole("button", { name: "পরীক্ষা শুরু করুন" }).closest("[data-surface]");
    expect(surface).not.toHaveAttribute("data-tabbar");
    expect((surface as HTMLElement).style.getPropertyValue("--tabbar-h")).toBe("");
    expect(screen.queryByRole("navigation", { name: "নিচের মেনু" })).not.toBeInTheDocument();
  });
});
