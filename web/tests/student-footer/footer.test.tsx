import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { StudentShell, footerVariant } from "@/components/shell/student-shell";
import en from "@/i18n/messages/en/common";
import bn from "@/i18n/messages/bn/common";
import { mockApi, renderWithProviders } from "../helpers";

const WEB = path.resolve(__dirname, "../..");
const FOOTER_NAV = "Site information";

function renderShell(route: string, locale: "en" | "bn" = "en") {
  mockApi({});
  renderWithProviders(
    <StudentShell>
      <p>page</p>
    </StudentShell>,
    { user: null, locale, route },
  );
}

async function fullFooter() {
  return screen.findByRole("contentinfo");
}

describe("site footer, full variant", () => {
  it("shows the old site's contact, company, app, social and copyright content in English", async () => {
    renderShell("/mcq", "en");
    const footer = await fullFooter();
    const info = within(footer).getByRole("navigation", { name: FOOTER_NAV });

    expect(within(info).getByRole("heading", { name: "Contact Information" })).toBeInTheDocument();
    expect(within(info).getByText("+8801601561225")).toBeInTheDocument();
    expect(within(info).getByRole("link", { name: "info@symamlaw.com" })).toHaveAttribute("href", "mailto:info@symamlaw.com");
    expect(within(info).getByText("10:00 AM - 6:00 PM")).toBeInTheDocument();
    expect(within(info).getByText("Binodpur Bazar, Motihar, Rajshahi, Bangladesh")).toBeInTheDocument();

    expect(within(info).getByRole("heading", { name: "Company" })).toBeInTheDocument();
    expect(within(info).getByRole("link", { name: "Terms and conditions" })).toHaveAttribute("href", "/terms");
    expect(within(info).getByRole("link", { name: "Return Refund" })).toHaveAttribute("href", "/refund");
    expect(within(info).getByRole("link", { name: "Privacy Policy" })).toHaveAttribute("href", "/privacy");
    expect(within(info).getByRole("link", { name: "About Us" })).toHaveAttribute("href", "/about");

    expect(within(info).getByRole("heading", { name: "Get Mobile App" })).toBeInTheDocument();
    expect(within(info).getByText(en.footer.appText)).toBeInTheDocument();
    expect(within(info).getByRole("link", { name: "App Store" })).toHaveAttribute(
      "href",
      "https://apps.apple.com/us/app/apple-store/id375380948",
    );
    expect(within(info).getByRole("link", { name: "Google Play" })).toHaveAttribute(
      "href",
      "https://expo.dev/accounts/abtahi-tajwar/projects/symamlaw-mobile/builds/a0368e73-baf4-4021-8bc6-e59fc962afaa",
    );

    const social = within(footer).getByRole("navigation", { name: "Social media" });
    expect(within(social).getByRole("link", { name: "Facebook" })).toHaveAttribute(
      "href",
      "https://www.facebook.com/profile.php?id=100065554386812",
    );
    expect(within(social).getByRole("link", { name: "WhatsApp" })).toHaveAttribute("href", "https://api.whatsapp.com/send?phone=8801601561225");
    expect(within(social).getByRole("link", { name: "LinkedIn" })).toHaveAttribute(
      "href",
      "https://www.linkedin.com/company/symam-law/?viewAsMember=true",
    );
    expect(within(social).getByRole("link", { name: "YouTube" })).toHaveAttribute("href", "https://youtu.be/ZmrfDj6T5R4?si=aKH4z7RErGn-v7Lr");

    expect(within(footer).getByText(en.footer.copyright)).toBeInTheDocument();
    expect(within(footer).getByText("Developed By Fleeting Trails")).toBeInTheDocument();
  });

  it("shows the same content in Bangla", async () => {
    renderShell("/mcq", "bn");
    const footer = await fullFooter();
    expect(within(footer).getByRole("heading", { name: bn.footer.contactHeading })).toBeInTheDocument();
    expect(within(footer).getByRole("heading", { name: bn.footer.companyHeading })).toBeInTheDocument();
    expect(within(footer).getByRole("heading", { name: bn.footer.appHeading })).toBeInTheDocument();
    expect(within(footer).getByText(bn.footer.appText)).toBeInTheDocument();
    expect(within(footer).getByText(bn.footer.copyright)).toBeInTheDocument();
    expect(within(footer).getByText(bn.footer.developer)).toBeInTheDocument();
    expect(within(footer).getByRole("link", { name: bn.footer.about })).toHaveAttribute("href", "/about");
    expect(within(footer).queryByText("Contact Information")).not.toBeInTheDocument();
  });

  it("links only to pages that exist on the student site", async () => {
    renderShell("/mcq", "en");
    const footer = await fullFooter();
    const internal = within(footer)
      .getAllByRole("link")
      .map((link) => link.getAttribute("href") ?? "")
      .filter((href) => href.startsWith("/"));
    expect(internal.sort()).toEqual(["/", "/about", "/privacy", "/refund", "/terms"]);
    for (const href of internal) {
      expect(existsSync(path.join(WEB, "src/app/(site)", href, "page.tsx"))).toBe(true);
    }
  });
});

describe("site footer, minimal and none variants", () => {
  for (const route of ["/login", "/register", "/set-phone", "/forgot-password"]) {
    it(`shows only copyright and legal links on ${route}`, async () => {
      renderShell(route, "en");
      const footer = await fullFooter();
      const links = within(footer).getAllByRole("link").map((link) => link.textContent);
      expect(links).toEqual(["Terms and conditions", "Return Refund", "Privacy Policy"]);
      expect(within(footer).getByText(en.footer.copyright)).toBeInTheDocument();
      expect(within(footer).queryByText("Contact Information")).not.toBeInTheDocument();
      expect(within(footer).queryByText("Developed By Fleeting Trails")).not.toBeInTheDocument();
    });
  }

  it("shows no footer on the exam sitting page", () => {
    renderShell("/exam/abc123/sit", "en");
    expect(screen.queryByRole("contentinfo")).not.toBeInTheDocument();
  });
});

describe("footerVariant (the route decides)", () => {
  it("gives the sitting page no footer", () => {
    expect(footerVariant("/exam/abc/sit")).toBe("none");
    expect(footerVariant("/exam/abc/sit/")).toBe("none");
  });

  it("gives sign-in and sign-up pages the minimal footer", () => {
    for (const route of ["/login", "/login/google", "/register", "/set-phone", "/activate", "/code", "/reset-password"]) {
      expect(footerVariant(route)).toBe("minimal");
    }
  });

  it("gives result, submitted, preview and other pages the full footer", () => {
    for (const route of ["/", "/mcq", "/exam/abc", "/exam/abc/result", "/exam/abc/submitted", "/exam/abc/preview", "/results", "/loginx"]) {
      expect(footerVariant(route)).toBe("full");
    }
  });
});

describe("staff and admin have no site footer", () => {
  it("the staff and admin layouts do not use the student shell or the site footer", () => {
    for (const layout of ["src/app/(staff)/layout.tsx", "src/app/(admin)/layout.tsx"]) {
      const source = readFileSync(path.join(WEB, layout), "utf8");
      expect(source).not.toMatch(/StudentShell|SiteFooter/);
    }
  });

  it("the staff sign-in shell does not render the site footer", () => {
    const source = readFileSync(path.join(WEB, "src/components/shell/staff-auth-shell.tsx"), "utf8");
    expect(source).not.toMatch(/SiteFooter/);
  });
});

describe("footer copy keys", () => {
  it("has the same keys in English and Bangla, each with text", () => {
    const enKeys = Object.keys(en.footer).sort();
    expect(Object.keys(bn.footer).sort()).toEqual(enKeys);
    for (const key of enKeys) {
      expect(en.footer[key as keyof typeof en.footer]).not.toBe("");
      expect(bn.footer[key as keyof typeof bn.footer]).not.toBe("");
    }
  });
});
