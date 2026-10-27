import { describe, expect, it } from "vitest";
import { screen, within } from "@testing-library/react";
import { StudentShell } from "@/components/shell/student-shell";
import { ComingSoonPage, type ComingSoonKey } from "@/app/(site)/_components/coming-soon-page";
import { mockApi, renderWithProviders } from "../helpers";

const PAGES: { key: ComingSoonKey; href: string; bn: string; en: string }[] = [
  { key: "publications", href: "/publications", bn: "প্রকাশনা", en: "Publications" },
  { key: "blog", href: "/blog", bn: "ব্লগ", en: "Blog" },
  { key: "media", href: "/media", bn: "মিডিয়া", en: "Media" },
  { key: "resources", href: "/resources", bn: "রিসোর্স", en: "Resources" },
];

describe("landing menu: coming-soon pages", () => {
  it.each(PAGES)("$key shows its own title, a coming-soon message and a way home", ({ key, bn }) => {
    renderWithProviders(<ComingSoonPage page={key} />);
    expect(screen.getByRole("heading", { level: 1, name: bn })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "শীঘ্রই আসছে" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "হোম পেজে ফিরে যান" })).toHaveAttribute("href", "/");
  });

  it("is written in English when the language is English", () => {
    renderWithProviders(<ComingSoonPage page="blog" />, { locale: "en" });
    expect(screen.getByRole("heading", { level: 1, name: "Blog" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Coming soon" })).toBeInTheDocument();
  });

  it("each of the four menu items on the landing page has its own page", async () => {
    mockApi({});
    renderWithProviders(
      <StudentShell>
        <p>পাতা</p>
      </StudentShell>,
      { route: "/" },
    );
    const menu = await screen.findByRole("navigation", { name: "মূল মেনু" });
    const hrefs = PAGES.map((page) => within(menu).getByRole("link", { name: page.bn }).getAttribute("href"));
    expect(hrefs).toEqual(PAGES.map((page) => page.href));
    expect(new Set(hrefs).size).toBe(4);
  });
});
