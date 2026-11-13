import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import StaffStudentsPage from "@/app/(staff)/staff/students/page";
import { AdmissionQueue } from "@/components/staff/offline/admission-queue";
import { DeskActivationDialog } from "@/components/staff/offline/desk-activation-dialog";
import { DeskStartDialog } from "@/components/staff/offline/desk-start-dialog";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { counts, page, queueRow } from "./fixtures";

const inTenMinutes = () => new Date(Date.now() + 600_000).toISOString();

describe("Add new student takes the phone only", () => {
  it("refuses a bad phone, then posts only the phone", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "POST /api/v1/offline-admissions/desk": { admissionId: "adm-9", studentId: "stu-9", phone: "01711000009", code: "K7Q2-9PXA", expiresAt: inTenMinutes() },
    });
    renderWithProviders(<DeskStartDialog open onOpenChange={() => undefined} onCreated={() => undefined} />, {
      user: fixtures.systemAdmin,
      locale: "en",
    });
    const phone = screen.getByRole("textbox", { name: /Phone/ });
    await user.type(phone, "0171");
    await user.click(screen.getByRole("button", { name: "Create" }));
    expect(screen.getByText("Enter a valid phone number, for example 017XXXXXXXX")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);

    await user.clear(phone);
    await user.type(phone, "01711000009");
    await user.click(screen.getByRole("button", { name: "Create" }));
    await waitFor(() => expect(api.calls.find((call) => call.method === "POST")?.body).toEqual({ phone: "01711000009" }));
  });

  it("with Offline admission ticked, the students dialog hides name and email and posts the phone to the desk route", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/students": page([]),
      "GET /api/v1/offline-admissions": page([]),
      "POST /api/v1/offline-admissions/desk": { admissionId: "adm-9", studentId: "stu-9", phone: "01711000009", code: "K7Q2-9PXA", expiresAt: inTenMinutes() },
    });
    renderWithProviders(<StaffStudentsPage />, { user: fixtures.masterAdmin, locale: "en", route: "/staff/students" });
    await user.click(await screen.findByRole("button", { name: "New student" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).queryByRole("textbox", { name: /^Name/ })).toBeNull();
    expect(within(dialog).queryByRole("textbox", { name: /^Email/ })).toBeNull();

    await user.type(within(dialog).getByRole("textbox", { name: /Phone/ }), "01711000009");
    await user.click(within(dialog).getByRole("button", { name: "Add" }));
    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/offline-admissions/desk")).toBe(true));
    expect(api.calls.find((call) => call.path === "/api/v1/offline-admissions/desk")?.body).toEqual({ phone: "01711000009" });
    expect(api.calls.some((call) => call.path === "/api/v1/students" && call.method === "POST")).toBe(false);
  });
});

describe("the code dialog counts down", () => {
  afterEach(() => vi.useRealTimers());

  it("counts down and offers New code at zero", () => {
    vi.useFakeTimers();
    renderWithProviders(
      <DeskActivationDialog
        admissionId="adm-9"
        phone="01711000009"
        initial={{ code: "K7Q2-9PXA", expiresAt: new Date(Date.now() + 582_000).toISOString() }}
        onClose={() => undefined}
      />,
      { user: fixtures.systemAdmin, locale: "en" },
    );
    expect(screen.getByText("Expires in 9:42")).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.getByText("Expires in 9:41")).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(581_000);
    });
    expect(screen.getByText("This code has expired.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New code" })).toBeInTheDocument();
  });
});

describe("New code", () => {
  it("makes a new code for an expired one and shows it", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "POST /api/v1/offline-admissions/adm-9/activation-code": { code: "NEW1-2XYZ", expiresAt: inTenMinutes() },
    });
    renderWithProviders(
      <DeskActivationDialog
        admissionId="adm-9"
        phone="01711000009"
        initial={{ code: "OLD1-0000", expiresAt: new Date(Date.now() - 1000).toISOString() }}
        onClose={() => undefined}
      />,
      { user: fixtures.systemAdmin, locale: "en" },
    );
    expect(screen.getByText("This code has expired.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "New code" }));
    expect(await screen.findByText("NEW1-2XYZ", { selector: "p" })).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST" && call.path.endsWith("/activation-code"))).toBe(true);
  });
});

describe("the Waiting for the student tab", () => {
  it("shows the phone, Not activated and Show code, and Show code opens the live code", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/offline-admissions": ({ url }: { url: URL }) =>
        page(
          url.searchParams.get("status") === "AWAITING_FORM"
            ? [queueRow({ id: "adm-7", status: "AWAITING_FORM", fullName: "", mobile: "01711000007", activated: false, submittedAt: null })]
            : [],
        ),
      "GET /api/v1/offline-admissions/counts": counts(),
      "GET /api/v1/offline-batches": [],
      "GET /api/v1/offline-admissions/adm-7/activation-code": { code: "ABCD-1234", expiresAt: inTenMinutes(), activated: false },
    });
    renderWithProviders(<AdmissionQueue />, { user: fixtures.systemAdmin, locale: "en", route: "/staff/admissions?tab=AWAITING_FORM" });

    expect(await screen.findByRole("tab", { name: /^Waiting for the student/ })).toHaveAttribute("aria-selected", "true");
    expect((await screen.findAllByText("01711000007")).length).toBeGreaterThan(0);
    expect((await screen.findAllByText("Not activated")).length).toBeGreaterThan(0);
    await user.click((await screen.findAllByRole("button", { name: "Show code" }))[0]);
    expect(await screen.findByText("ABCD-1234", { selector: "p" })).toBeInTheDocument();
  });
});
