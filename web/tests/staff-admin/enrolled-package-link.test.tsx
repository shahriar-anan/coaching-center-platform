import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EnrolledList } from "@/components/staff/enrol/enrolled-list";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const EXAM = "eeeeeeee-0000-0000-0000-000000000001";
const PACKAGE = "aaaaaaaa-0000-0000-0000-000000000009";

const row = (overrides: Record<string, unknown>) => ({
  grantId: "g",
  studentId: "s",
  fullName: "Student",
  studentCode: null,
  phone: null,
  email: null,
  source: "MANUAL",
  reason: "x",
  enrolledAt: "2026-11-01T00:00:00Z",
  ...overrides,
});

describe("the exam page's enrolled list links to the package (plan 14.11, P61 to P63)", () => {
  it("links a package row to its package's roster tab, and gives an exam row no package link", async () => {
    mockApi({
      [`GET /api/v1/live-exams/${EXAM}/enrolments`]: {
        content: [
          row({ grantId: "g1", studentId: "s1", fullName: "Package Student", grantScope: "MODULE", moduleId: PACKAGE }),
          row({ grantId: "g2", studentId: "s2", fullName: "Exam Student", grantScope: "EXAM", moduleId: null }),
        ],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      },
    });
    renderWithProviders(<EnrolledList scope="EXAM" targetId={EXAM} />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("Package Student")).toBeInTheDocument();
    const links = screen.getAllByRole("link", { name: "Open the package" });
    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute("href", expect.stringContaining(`/staff/modules/${PACKAGE}?tab=roster`));
  });
});
