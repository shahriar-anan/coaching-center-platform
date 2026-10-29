import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { FavouriteButton } from "@/components/student/catalog/favourite-button";
import { favouriteUrl } from "@/lib/api/exam/favourites";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";

describe("the favourite toggle (S3.3)", () => {
  it("turns on at once and sends PUT for an exam", async () => {
    const { calls } = mockApi({ "PUT /api/v1/me/favourites/exams/e1": {} });
    renderWithProviders(<FavouriteButton kind="exam" id="e1" initial={false} next="/exam/e1" />, {
      user: fixtures.student,
      locale: "en",
    });

    const mark = screen.getByRole("button", { name: "Wish List" });
    expect(mark).toHaveAttribute("aria-pressed", "false");
    await userEvent.click(mark);
    expect(mark).toHaveAttribute("aria-pressed", "true");
    await waitFor(() => expect(calls.some((call) => call.method === "PUT" && call.path === "/api/v1/me/favourites/exams/e1")).toBe(true));
  });

  it("turns off with DELETE for a package that is already a favourite", async () => {
    const { calls } = mockApi({ "DELETE /api/v1/me/favourites/packages/m1": {} });
    renderWithProviders(<FavouriteButton kind="package" id="m1" initial next="/mcq/m/m1" />, { user: fixtures.student, locale: "en" });

    const mark = screen.getByRole("button", { name: "Wish List" });
    expect(mark).toHaveAttribute("aria-pressed", "true");
    await userEvent.click(mark);
    expect(mark).toHaveAttribute("aria-pressed", "false");
    await waitFor(() => expect(calls.some((call) => call.method === "DELETE" && call.path === "/api/v1/me/favourites/packages/m1")).toBe(true));
  });

  it("goes back to its last state, and says so in a toast, when the API refuses the change", async () => {
    mockApi({ "PUT /api/v1/me/favourites/exams/e1": () => apiError(500, "INTERNAL_ERROR", "boom") });
    renderWithProviders(<FavouriteButton kind="exam" id="e1" initial={false} next="/exam/e1" />, {
      user: fixtures.student,
      locale: "en",
    });

    const mark = screen.getByRole("button", { name: "Wish List" });
    await userEvent.click(mark);
    // The message is a toast (Phase 4, S4.4), so it is not an inline alert beside the mark.
    expect(await screen.findByText("Could not save. Try again.")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(mark).toHaveAttribute("aria-pressed", "false");
  });

  it("sends a visitor to log in, and makes no favourite call", async () => {
    const { calls } = mockApi({});
    renderWithProviders(<FavouriteButton kind="exam" id="e1" initial={false} next="/exam/e1" />, { locale: "en" });

    const link = screen.getByRole("link", { name: "Wish List" });
    expect(link).toHaveAttribute("href", `/login?next=${encodeURIComponent("/exam/e1")}`);
    expect(calls).toHaveLength(0);
  });

  it("shows no mark to staff", () => {
    renderWithProviders(<FavouriteButton kind="exam" id="e1" initial={false} next="/exam/e1" />, {
      user: fixtures.masterAdmin,
      locale: "en",
    });
    expect(screen.queryByRole("button", { name: "Wish List" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Wish List" })).not.toBeInTheDocument();
  });

  it("builds the favourite URL for each kind", () => {
    expect(favouriteUrl("exam", "e1")).toBe("/api/v1/me/favourites/exams/e1");
    expect(favouriteUrl("package", "m1")).toBe("/api/v1/me/favourites/packages/m1");
  });
});
