import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ErrorState } from "@/components/ui/error-state";
import { ApiError } from "@/lib/api/errors";
import { renderWithProviders } from "../helpers";

const CONNECTION = "Check your internet connection and try again.";
const SERVER = "Something went wrong on our side. Try again in a moment.";
const NOT_FOUND = "We could not find what you asked for. It may have been removed, or the address may be wrong.";

function failure(status: number, code: string) {
  return new ApiError({ code, message: code, timestamp: "", requestId: "" }, status);
}

describe("ErrorState text follows the error the caller passes", () => {
  it("says the server had a problem for a 5xx", () => {
    renderWithProviders(<ErrorState error={failure(500, "INTERNAL_ERROR")} onRetry={() => undefined} />, { locale: "en" });
    expect(screen.getByText(SERVER)).toBeInTheDocument();
    expect(screen.queryByText(CONNECTION)).toBeNull();
  });

  it("says the item was not found for a 404", () => {
    renderWithProviders(<ErrorState error={failure(404, "RESOURCE_NOT_FOUND")} onRetry={() => undefined} />, { locale: "en" });
    expect(screen.getByText(NOT_FOUND)).toBeInTheDocument();
    expect(screen.queryByText(CONNECTION)).toBeNull();
  });

  it("keeps the connection text for a network error, which is not an ApiError", () => {
    renderWithProviders(<ErrorState error={new TypeError("Failed to fetch")} onRetry={() => undefined} />, { locale: "en" });
    expect(screen.getByText(CONNECTION)).toBeInTheDocument();
  });

  it("keeps the connection text when no error is passed", () => {
    renderWithProviders(<ErrorState onRetry={() => undefined} />, { locale: "en" });
    expect(screen.getByText(CONNECTION)).toBeInTheDocument();
  });
});
