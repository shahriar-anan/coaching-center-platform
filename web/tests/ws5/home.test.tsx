import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import StaffHomePage from "@/app/(staff)/staff/page";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const participation = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

describe("staff home per role", () => {
  it("Master Admin sees the three counts and the payment card", async () => {
    mockApi({
      "GET /api/v1/live-exam-participation/summary": { awaitingMarking: 62, draftsAwaitingPublish: 9 },
      "GET /api/v1/live-exam-participation": participation,
      "GET /api/v1/live-exam-orders": { ...participation, totalElements: 3 },
    });
    renderWithProviders(<StaffHomePage />, { user: fixtures.masterAdmin });

    expect(await screen.findByText("মূল্যায়ন বাকি")).toBeInTheDocument();
    expect(screen.getByText("প্রকাশ বাকি")).toBeInTheDocument();
    expect(screen.getByText("পেমেন্ট অপেক্ষমাণ")).toBeInTheDocument();
    expect(screen.getByText("শর্টকাট")).toBeInTheDocument();
  });

  it("System Admin sees the payment card but not the instructor copy", async () => {
    mockApi({
      "GET /api/v1/live-exam-participation/summary": { awaitingMarking: 1, draftsAwaitingPublish: 0 },
      "GET /api/v1/live-exam-participation": participation,
      "GET /api/v1/live-exam-orders": { ...participation, totalElements: 0 },
    });
    renderWithProviders(<StaffHomePage />, { user: fixtures.systemAdmin });

    expect(await screen.findByText("পেমেন্ট অপেক্ষমাণ")).toBeInTheDocument();
    expect(screen.queryByText("আপনাকে এখনো কোনো মডিউল দেওয়া হয়নি")).not.toBeInTheDocument();
  });

  it("Instructor sees only assigned modules, and the empty sentence when there are none", async () => {
    mockApi({
      "GET /api/v1/live-exam-modules": participation,
      "GET /api/v1/live-exam-participation": participation,
    });
    renderWithProviders(<StaffHomePage />, { user: fixtures.instructor });

    expect(await screen.findByText("আপনাকে এখনো কোনো মডিউল দেওয়া হয়নি")).toBeInTheDocument();
    expect(screen.queryByText("পেমেন্ট অপেক্ষমাণ")).not.toBeInTheDocument();
  });
});
