import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { StudentAccessSection } from "@/components/staff/access/student-access-section";
import { fixtures, renderWithProviders } from "../helpers";
import { apiError, mockApi } from "../helpers/mock-api";
import "./copy-setup";

const studentId = "11111111-1111-4111-8111-111111111111";

const access = {
  content: [
    {
      moduleId: "aaaaaaaa-0000-4000-8000-000000000001",
      moduleTitleEn: "19th BJS",
      moduleTitleBn: "১৯তম বিজেএস",
      via: "MANUAL",
      exams: [{ id: "eeeeeeee-0000-4000-8000-000000000001", titleEn: "CPC", titleBn: "সিপিসি" }],
      orderId: null,
      tranId: null,
      grantId: "gggggggg-0000-4000-8000-000000000001",
    },
    {
      moduleId: "aaaaaaaa-0000-4000-8000-000000000002",
      moduleTitleEn: "Roster module",
      moduleTitleBn: "রোস্টার মডিউল",
      via: "ROSTER",
      exams: [],
      orderId: null,
      tranId: null,
      grantId: null,
    },
    {
      moduleId: "aaaaaaaa-0000-4000-8000-000000000003",
      moduleTitleEn: "Paid module",
      moduleTitleBn: "কেনা মডিউল",
      via: "MODULE_PURCHASE",
      exams: [],
      orderId: "oooooooo-0000-4000-8000-000000000001",
      tranId: "TRAN-PAID-1",
      grantId: null,
    },
  ],
  page: 0,
  size: 50,
  totalElements: 3,
  totalPages: 1,
};

const modules = {
  content: [{ id: "aaaaaaaa-0000-4000-8000-000000000001", titleEn: "19th BJS", titleBn: "১৯তম বিজেএস", status: "PUBLISHED" }],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
};

describe("StudentAccessSection", () => {
  it("lets Master Admin revoke only the rows that have a grant", async () => {
    mockApi({ [`GET /api/v1/students/${studentId}/live-exam-access`]: access });
    renderWithProviders(<StudentAccessSection studentId={studentId} studentName="রহিম" />, {
      user: fixtures.masterAdmin,
    });

    await screen.findAllByText("হাতে দেওয়া");
    // The table renders each row twice: a table for wide screens and cards for phones.
    expect(screen.getAllByRole("button", { name: "অ্যাক্সেস বাতিল" })).toHaveLength(2);
    expect(screen.getAllByText("TRAN-PAID-1")[0].closest("a")).toHaveAttribute("href", expect.stringContaining("/staff/payments/TRAN-PAID-1"));
  });

  it("offers neither revoke nor grant to System Admin, and says who can revoke", async () => {
    mockApi({ [`GET /api/v1/students/${studentId}/live-exam-access`]: access });
    renderWithProviders(<StudentAccessSection studentId={studentId} studentName="রহিম" />, {
      user: fixtures.systemAdmin,
    });

    await screen.findAllByText("হাতে দেওয়া");
    expect(screen.queryByRole("button", { name: "অ্যাক্সেস বাতিল" })).not.toBeInTheDocument();
    expect(screen.getByText("অ্যাক্সেস বাতিল করতে পারেন শুধু মাস্টার অ্যাডমিন।")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "হাতে অ্যাক্সেস দিন" })).not.toBeInTheDocument();
  });

  it("sends Master Admin to the direct enrolment screen for this student", async () => {
    mockApi({
      [`GET /api/v1/students/${studentId}/live-exam-access`]: access,
      "GET /api/v1/live-exam-modules": modules,
    });
    renderWithProviders(<StudentAccessSection studentId={studentId} studentName="রহিম" />, {
      user: fixtures.masterAdmin,
    });

    await screen.findAllByText("হাতে দেওয়া");
    expect(screen.getByRole("link", { name: "হাতে অ্যাক্সেস দিন" })).toHaveAttribute(
      "href",
      expect.stringContaining(`/staff/enrol?student=${studentId}`),
    );
  });

  it("shows the error state when the list cannot load", async () => {
    mockApi({ [`GET /api/v1/students/${studentId}/live-exam-access`]: apiError(500, "INTERNAL_ERROR", "Boom.") });
    renderWithProviders(<StudentAccessSection studentId={studentId} studentName="রহিম" />, {
      user: fixtures.masterAdmin,
    });

    expect(await screen.findByText("অ্যাক্সেসের তালিকা আনা যায়নি।")).toBeInTheDocument();
  });
});
