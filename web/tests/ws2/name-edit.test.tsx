import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import AccountPage from "@/app/(site)/account/page";
import { ErrorBody, apiError, fixtures, mockApi, renderWithProviders } from "../helpers";

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

  it("shows a server field error for the name under the field", async () => {
    mockApi({
      "GET /api/v1/me": fixtures.student,
      "PATCH /api/v1/me": () =>
        new ErrorBody(400, {
          code: "VALIDATION_FAILED",
          message: "invalid",
          fieldErrors: [{ field: "fullName", message: "too long" }],
        } as unknown as { code: string; message: string }),
    });
    renderWithProviders(<AccountPage />, { user: fixtures.student });

    await userEvent.click(await screen.findByRole("button", { name: "নাম বদলান" }));
    await userEvent.click(screen.getByRole("button", { name: "নাম সেভ করুন" }));

    expect(await screen.findByText("নাম ১ থেকে ১০০ অক্ষরের মধ্যে লিখুন।")).toBeInTheDocument();
  });

  it("treats a 405 from an API without the endpoint as coming soon", async () => {
    mockApi({
      "GET /api/v1/me": fixtures.student,
      "PATCH /api/v1/me": () =>
        new ErrorBody(405, { code: "INTERNAL_ERROR", message: "Method Not Allowed" }),
    });
    renderWithProviders(<AccountPage />, { user: fixtures.student });

    await userEvent.click(await screen.findByRole("button", { name: "নাম বদলান" }));
    await userEvent.click(screen.getByRole("button", { name: "নাম সেভ করুন" }));

    expect(await screen.findByText("নাম বদলানোর সুবিধাটি শীঘ্রই চালু হবে।")).toBeInTheDocument();
  });
});
