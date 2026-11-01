import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExamPage } from "@/components/student/catalog/exam-page";
import { ErrorState } from "@/components/ui/error-state";
import { ApiError } from "@/lib/api/errors";
import { apiError, mockApi, renderWithProviders } from "../helpers";

const NOT_FOUND = "We could not find what you asked for. It may have been removed, or the address may be wrong.";
const CONNECTION = "Check your internet connection and try again.";

function failure(status: number, code: string) {
  return new ApiError({ code, message: code, timestamp: "", requestId: "" }, status);
}

describe("error text follows the kind of failure (BUG-004)", () => {
  it("says the item was not found for a 404, not the connection text", () => {
    renderWithProviders(<ErrorState error={failure(404, "RESOURCE_NOT_FOUND")} onRetry={() => undefined} />, { locale: "en" });
    expect(screen.getByText(NOT_FOUND)).toBeInTheDocument();
    expect(screen.queryByText(CONNECTION)).toBeNull();
  });

  it("keeps the connection text for a network error, which is not an ApiError", () => {
    renderWithProviders(<ErrorState error={new TypeError("Failed to fetch")} onRetry={() => undefined} />, { locale: "en" });
    expect(screen.getByText(CONNECTION)).toBeInTheDocument();
  });

  it("says the server had a problem for a 5xx", () => {
    renderWithProviders(<ErrorState error={failure(500, "INTERNAL_ERROR")} onRetry={() => undefined} />, { locale: "en" });
    expect(screen.getByText("Something went wrong on our side. Try again in a moment.")).toBeInTheDocument();
    expect(screen.queryByText(CONNECTION)).toBeNull();
  });

  it("shows the exam not-found page for an id that is not a UUID, such as /exam/abc", async () => {
    mockApi({ "GET /api/v1/public/live-exams/abc": apiError(400, "VALIDATION_FAILED", "Parameter id is invalid.") });
    renderWithProviders(<ExamPage examId="abc" />, { locale: "en" });
    expect(await screen.findByRole("heading", { level: 2, name: "This exam was not found" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to home" })).toBeInTheDocument();
    expect(screen.queryByText(CONNECTION)).toBeNull();
  });
});
