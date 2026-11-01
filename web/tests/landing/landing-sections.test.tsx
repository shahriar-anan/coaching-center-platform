import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Closing } from "@/components/student/landing/closing";
import { Contact } from "@/components/student/landing/contact";
import { Steps } from "@/components/student/landing/steps";
import { socials } from "@/content/site-info";
import bn from "@/i18n/messages/bn/landing";
import en from "@/i18n/messages/en/landing";
import { renderWithProviders } from "../helpers";

describe("landing steps", () => {
  it("shows the four payment logos, each named by its method", () => {
    renderWithProviders(<Steps />, { user: null, locale: "en" });

    expect(screen.getByText(en.paySecure)).toBeInTheDocument();
    const logos = screen.getByRole("list", { name: en.paymentsAlt });
    for (const name of ["bKash", "Nagad", "Rocket", "Visa"]) {
      expect(within(logos).getByRole("img", { name })).toBeInTheDocument();
    }
    expect(within(logos).getByRole("img", { name: "bKash" })).toHaveAttribute("src", expect.stringContaining("bkash.svg"));
  });

  it("numbers the three steps", () => {
    renderWithProviders(<Steps />, { user: null, locale: "bn" });

    expect(screen.getByText(bn.stepNumber.replace("{n}", "১"))).toBeInTheDocument();
    expect(screen.getByText(bn.stepNumber.replace("{n}", "৩"))).toBeInTheDocument();
  });
});

describe("landing closing", () => {
  it("links the sign-up button to /register and the packages button to /packages", () => {
    renderWithProviders(<Closing />, { user: null, locale: "bn" });

    expect(screen.getByRole("link", { name: bn.ctaSignUp })).toHaveAttribute("href", "/register");
    expect(screen.getByRole("link", { name: bn.closingPackages })).toHaveAttribute("href", "/packages");
    expect(screen.getByRole("link", { name: bn.closingLogin })).toHaveAttribute("href", "/login");
  });
});

describe("landing contact", () => {
  it("offers tap-to-call numbers, a WhatsApp link in a new tab, and the social links", () => {
    renderWithProviders(<Contact />, { user: null, locale: "en" });

    expect(screen.getByRole("link", { name: "01601-561225" })).toHaveAttribute("href", "tel:+8801601561225");
    const whatsapp = screen.getByRole("link", { name: en.contactWhatsapp });
    expect(whatsapp).toHaveAttribute("href", "https://wa.me/8801601561225");
    expect(whatsapp).toHaveAttribute("target", "_blank");
    expect(whatsapp).toHaveAttribute("rel", "noopener noreferrer");
    expect(screen.getByRole("link", { name: "Facebook" })).toHaveAttribute("href", socials.facebook);
  });
});
