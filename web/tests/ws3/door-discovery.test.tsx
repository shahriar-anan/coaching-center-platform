import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DoorDiscovery } from "@/components/student/catalog/door-discovery";
import { apiError, mockApi, renderWithProviders } from "../helpers";

const upcoming = {
  id: "e1",
  moduleId: "m1",
  examType: "MCQ",
  titleEn: "BJS Mock 1",
  titleBn: "বিজেএস মক ১",
  moduleTitleEn: "BJS",
  moduleTitleBn: "বিজেএস",
  liveAt: "2026-11-12T06:00:00Z",
  endAt: "2026-11-12T06:40:00Z",
  price: 400,
};

describe("door discovery", () => {
  it("shows the next exams of this door's type", async () => {
    mockApi({
      "GET /api/v1/public/live-exams": { content: [upcoming], page: 0, totalPages: 1 },
    });
    renderWithProviders(<DoorDiscovery door="mcq" />);
    expect(await screen.findByRole("heading", { name: "আসন্ন পরীক্ষা" })).toBeInTheDocument();
    expect(await screen.findByText("বিজেএস মক ১")).toBeInTheDocument();
  });

  it("hides the strip while the API does not have the endpoint yet", async () => {
    mockApi({ "GET /api/v1/public/live-exams": () => apiError(404, "RESOURCE_NOT_FOUND") });
    renderWithProviders(<DoorDiscovery door="mcq" />);
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByRole("heading", { name: "আসন্ন পরীক্ষা" })).not.toBeInTheDocument();
  });
});
