import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import StaffSystemStatusPage from "@/app/(staff)/staff/system-status/page";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";
import "./copy-setup";

/*
 * The status page never shows a value (AG-10). The response below carries fake
 * secrets in a note, in an extra field and in a check name. None may reach the page.
 */

const SECRET = "FAKE-SECRET-VALUE-42";
const SECRET_STORE = "FAKE-STORE-ID-9c1";
const SECRET_HOST = "smtp.fake-secret.example";

const response = {
  checks: [
    {
      name: "checkout.configured",
      ok: true,
      note: `store ${SECRET_STORE} password ${SECRET}`,
      value: SECRET,
    },
    {
      name: "mail.configured",
      ok: false,
      note: `host ${SECRET_HOST}`,
      value: SECRET,
    },
    {
      name: `google.${SECRET}`,
      ok: true,
      note: `client ${SECRET}`,
    },
    {
      name: "seed.off",
      ok: true,
      note: "The demo seed is off.",
      secret: SECRET,
    },
  ],
};

describe("system status page never shows a value (AG-10)", () => {
  it("does not show a fake secret anywhere on the page", async () => {
    mockApi({ "GET /api/v1/system-status": response });
    renderWithProviders(<StaffSystemStatusPage />, { user: fixtures.masterAdmin, locale: "bn" });

    // The rows are there, so the check below is not passing on an empty page.
    expect((await screen.findAllByText("পেমেন্ট গেটওয়ের সেটিং")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("হ্যাঁ").length).toBeGreaterThan(0);

    const html = document.body.innerHTML;
    const text = document.body.textContent ?? "";
    for (const secret of [SECRET, SECRET_STORE, SECRET_HOST, "fake-secret"]) {
      expect(text, `text shows ${secret}`).not.toContain(secret);
      expect(html, `markup shows ${secret}`).not.toContain(secret);
    }
  });

  it("uses the check's own name and note, not the API's", async () => {
    mockApi({ "GET /api/v1/system-status": response });
    renderWithProviders(<StaffSystemStatusPage />, { user: fixtures.masterAdmin, locale: "bn" });

    expect((await screen.findAllByText("অন্য একটি সেটিং")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("এই সেটিংয়ের বিবরণ এখানে নেই।").length).toBeGreaterThan(0);
    expect(screen.getAllByText("ডেমো ডেটার সিড বন্ধ আছে কি না।").length).toBeGreaterThan(0);
  });
});
