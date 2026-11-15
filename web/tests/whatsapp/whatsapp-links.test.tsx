import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { WhatsAppLinkCard } from "@/components/staff/catalog/whatsapp-link-card";
import { BatchFormDialog } from "@/components/staff/offline/batch-form-dialog";
import { MyExamsPage } from "@/components/student/catalog/my-exams-page";
import { AdmissionCard } from "@/components/student/my-admission/admission-overview";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { admission } from "../my-admission/fixture";

const LINK = "https://chat.whatsapp.com/AbCdEf123456";

describe("a package's WhatsApp link, set by staff (15 Nov)", () => {
  it("saves a group invite link", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/live-exam-modules/m1/whatsapp": { url: null },
      "PUT /api/v1/live-exam-modules/m1/whatsapp": { url: LINK },
    });
    renderWithProviders(<WhatsAppLinkCard moduleId="m1" />, { user: fixtures.masterAdmin, locale: "en" });

    const input = await screen.findByRole("textbox", { name: /WhatsApp group or channel link/ });
    await user.type(input, LINK);
    await user.click(screen.getByRole("button", { name: "Save link" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "PUT")).toBe(true));
    expect(api.calls.find((call) => call.method === "PUT")?.body).toEqual({ url: LINK });
  });

  it("refuses a link that is not a WhatsApp group or channel, without calling the API", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "GET /api/v1/live-exam-modules/m1/whatsapp": { url: null } });
    renderWithProviders(<WhatsAppLinkCard moduleId="m1" />, { user: fixtures.masterAdmin, locale: "en" });

    await user.type(await screen.findByRole("textbox", { name: /WhatsApp group or channel link/ }), "https://example.com/group");
    await user.click(screen.getByRole("button", { name: "Save link" }));

    expect(await screen.findByText(/Paste a WhatsApp group invite link/)).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "PUT")).toBe(false);
  });
});

describe("a batch's WhatsApp link on the batch form", () => {
  it("sends the link with the batch", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "POST /api/v1/offline-batches": { id: "b-9" } });
    renderWithProviders(<BatchFormDialog batch={null} open onOpenChange={() => undefined} />, { user: fixtures.masterAdmin, locale: "en" });

    await user.type(screen.getByRole("textbox", { name: /Name \(English\)/ }), "Batch 5");
    await user.type(screen.getByRole("textbox", { name: /Name \(Bangla\)/ }), "ব্যাচ ৫");
    fireEvent.change(screen.getByLabelText(/Ends on/), { target: { value: "2027-05-31" } });
    await user.type(screen.getByRole("textbox", { name: /Course fee per month/ }), "2500");
    await user.type(screen.getByRole("textbox", { name: /WhatsApp group or channel link/ }), LINK);
    await user.click(screen.getByRole("button", { name: "Create batch" }));

    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/offline-batches")).toBe(true));
    expect(api.calls.find((call) => call.path === "/api/v1/offline-batches")?.body).toMatchObject({ whatsappUrl: LINK });
  });
});

describe("the student's join button", () => {
  it("shows on an owned package that has a link, and opens it in a new tab", async () => {
    mockApi({
      "GET /api/v1/me/live-exam-access": {
        exams: [],
        modules: [
          { moduleId: "m1", via: "MODULE", endsAt: null, expired: false, whatsappUrl: LINK },
          { moduleId: "m2", via: "MODULE", endsAt: null, expired: false },
        ],
      },
      "GET /api/v1/public/live-exam-modules/m1": { id: "m1", titleEn: "Package one", titleBn: "Package one" },
      "GET /api/v1/public/live-exam-modules/m2": { id: "m2", titleEn: "Package two", titleBn: "Package two" },
    });
    renderWithProviders(<MyExamsPage />, { user: fixtures.student, locale: "en" });

    await userEvent.click(await screen.findByRole("tab", { name: /Packages/ }));
    const withLink = (await screen.findByText("Package one")).closest("li") as HTMLElement;
    const join = within(withLink).getByRole("link", { name: /Join the WhatsApp group/ });
    expect(join).toHaveAttribute("href", LINK);
    expect(join).toHaveAttribute("target", "_blank");
    expect(join).toHaveAttribute("rel", "noopener noreferrer");
    const withoutLink = screen.getByText("Package two").closest("li") as HTMLElement;
    expect(within(withoutLink).queryByRole("link", { name: /Join the WhatsApp group/ })).toBeNull();
  });

  it("shows on an onboarded admission that has a link, and not without one", () => {
    const { unmount } = renderWithProviders(
      <AdmissionCard admission={admission("ONBOARDED", { whatsappUrl: LINK })} now={Date.parse("2026-11-15T04:00:00Z")} />,
      { user: fixtures.student, locale: "en" },
    );
    expect(screen.getByRole("link", { name: /Join the WhatsApp group/ })).toHaveAttribute("href", LINK);
    unmount();

    renderWithProviders(<AdmissionCard admission={admission("ONBOARDED")} now={Date.parse("2026-11-15T04:00:00Z")} />, {
      user: fixtures.student,
      locale: "en",
    });
    expect(screen.queryByRole("link", { name: /Join the WhatsApp group/ })).toBeNull();
  });
});
