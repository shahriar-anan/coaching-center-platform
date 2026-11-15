import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useSiteNav } from "@/components/shell/site-menu";
import { renderWithProviders } from "../helpers";

/** The offline switch, read by the menu at render time. The suite runs with it on; a test turns it off for its own case. */
const flags = vi.hoisted(() => ({ offline: true }));

vi.mock("@/lib/config", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/config")>();
  return {
    ...actual,
    get offlineAdmissionEnabled() {
      return flags.offline;
    },
  };
});

/** The main menu's entries as plain links, so the test reads the href each one goes to. */
function NavProbe() {
  const items = useSiteNav();
  return (
    <ul>
      {items.map((item) =>
        "href" in item ? (
          <li key={item.label}>
            <a href={item.href}>{item.label}</a>
          </li>
        ) : null,
      )}
    </ul>
  );
}

describe("the landing menu's Admission item follows the offline switch (plan Q6)", () => {
  beforeEach(() => {
    flags.offline = true;
  });

  it("goes to the batch cards while the offline switch is on", () => {
    renderWithProviders(<NavProbe />, { locale: "en" });
    expect(screen.getByRole("link", { name: "Admission form" })).toHaveAttribute("href", "/admission");
  });

  it("goes back to the register page while the offline switch is off", () => {
    flags.offline = false;
    renderWithProviders(<NavProbe />, { locale: "en" });
    expect(screen.getByRole("link", { name: "Admission form" })).toHaveAttribute("href", "/register");
  });
});
