import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import AccountPage from "@/app/(site)/account/page";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";

describe("student name edit", () => {
  it("rejects an empty name without calling the API", async () => {
    const patch = vi.fn(() => fixtures.student);
    mockApi({ "GET /api/v1/me": fixtures.student, "PATCH /api/v1/me": patch });
    renderWithProviders(<AccountPage />, { user: fixtures.student });

    await userEvent.click(await screen.findByRole("button", { name: "নাম বদলান" }));
    const input = screen.getByLabelText(/^নাম/);
    await userEvent.clear(input);
    await userEvent.click(screen.getByRole("button", { name: "নাম সেভ করুন" }));

    expect(await screen.findByText("নাম ১ থেকে ১০০ অক্ষরের মধ্যে লিখুন।")).toBeInTheDocument();
    expect(patch).not.toHaveBeenCalled();
  });

  it("says the feature is coming soon when the API does not have the endpoint yet", async () => {
    mockApi({
      "GET /api/v1/me": fixtures.student,
      "PATCH /api/v1/me": () => apiError(404, "RESOURCE_NOT_FOUND"),
    });
    renderWithProviders(<AccountPage />, { user: fixtures.student });

    await userEvent.click(await screen.findByRole("button", { name: "নাম বদলান" }));
    await userEvent.click(screen.getByRole("button", { name: "নাম সেভ করুন" }));

    await waitFor(() =>
      expect(screen.getByText("নাম বদলানোর সুবিধাটি শীঘ্রই চালু হবে।")).toBeInTheDocument(),
    );
  });
});
