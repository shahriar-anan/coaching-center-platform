import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import RegisterPage from "@/app/(auth)/register/page";
import { renderWithProviders } from "../helpers";

// The flag is read when the module loads, so this file turns it on for itself; the other signup tests keep it off.
vi.mock("@/lib/config", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/config")>()),
  googleEnabled: true,
}));

describe("register with Google", () => {
  it("shows Continue with Google as a link to the API's Google sign-in, with the Google mark", async () => {
    renderWithProviders(<RegisterPage />);

    const link = screen.getByRole("link", { name: "গুগল দিয়ে চালিয়ে যান" });
    expect(link).toHaveAttribute("href", expect.stringMatching(/\/api\/v1\/auth\/google$/));
    expect(link.querySelector("svg")).not.toBeNull();
    expect(link.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
  });

  it("keeps the buy path: the Google link carries next through", async () => {
    renderWithProviders(<RegisterPage />, { route: "/register?next=%2Fmodule%2Fabc" });

    const link = screen.getByRole("link", { name: "গুগল দিয়ে চালিয়ে যান" });
    expect(link).toHaveAttribute("href", expect.stringContaining("next=%2Fmodule%2Fabc"));
  });
});
