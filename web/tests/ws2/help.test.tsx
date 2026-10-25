import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import HelpPage from "@/app/(site)/help/page";
import { renderWithProviders } from "../helpers";

describe("help", () => {
  it("filters the questions by the search text", async () => {
    renderWithProviders(<HelpPage />, { route: "/help" });
    expect(screen.getByRole("heading", { name: "কেনা ও পেমেন্ট" })).toBeInTheDocument();
    await userEvent.type(screen.getByRole("searchbox"), "মেগাবাইট");
    await waitFor(() => expect(screen.queryByRole("heading", { name: "কেনা ও পেমেন্ট" })).toBeNull());
    expect(screen.getByRole("heading", { name: "রিটেন খাতা আপলোড" })).toBeInTheDocument();
  });

  it("says nothing matches for an unknown word", async () => {
    renderWithProviders(<HelpPage />, { route: "/help" });
    await userEvent.type(screen.getByRole("searchbox"), "zzzz-no-match");
    expect(await screen.findByText("এই শব্দে কোনো প্রশ্ন পাওয়া যায়নি।")).toBeInTheDocument();
  });
});
