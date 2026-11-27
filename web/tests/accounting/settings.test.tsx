import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AccountingSettings } from "@/components/staff/accounting/settings/accounting-settings";
import type { Me } from "@/lib/api/exam/types";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";
import { ACCOUNTS, GROUPS, LECTURE, OTHER_TYPE, group } from "./fixtures";

/*
 * Settings (accounting-plan 4.1, 4.2 and 4.6): groups, the types of a group, and money accounts. Master Admin only.
 */

function renderSettings(locale: "en" | "bn" = "en", user: Me = fixtures.masterAdmin) {
  return renderWithProviders(<AccountingSettings />, { user, locale, route: "/staff/accounting/settings" });
}

describe("settings: groups", () => {
  it("lists the groups in order, and offers no archive for the Offline instructors group", async () => {
    mockApi({ "GET /api/v1/accounting/groups": GROUPS });
    renderSettings();

    const offline = await screen.findByText("Offline instructors", { selector: "p" });
    const row = offline.closest("li") as HTMLElement;
    expect(within(row).queryByRole("button", { name: "Archive" })).not.toBeInTheDocument();
    expect(within(row).getByText("Cannot be archived", { exact: false })).toBeInTheDocument();

    // Every other live group can be archived.
    expect(screen.getAllByRole("button", { name: "Archive" })).toHaveLength(GROUPS.length - 1);
  });

  it("adds an income group: its mode is Log and it is posted once, with direction and Count in Finance", async () => {
    const user = userEvent.setup();
    const api = mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "POST /api/v1/accounting/groups": group({ id: "g-new", nameEn: "Books", direction: "INCOME", mode: "LOG" }),
    });
    renderSettings();

    await user.click(await screen.findByRole("button", { name: "Add group" }));
    await user.type(screen.getByLabelText(/^Name \(English\)/), "Books");
    await user.selectOptions(screen.getByLabelText(/^Money/), "INCOME");

    expect(screen.getByLabelText(/^Records/)).toBeDisabled();
    expect(screen.getByLabelText(/^Records/)).toHaveValue("LOG");

    await user.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(api.calls.filter((call) => call.method === "POST")).toHaveLength(1));
    expect(api.calls.find((call) => call.method === "POST")?.body).toEqual({
      nameEn: "Books",
      nameBn: null,
      inFinance: true,
      mode: "LOG",
      direction: "INCOME",
    });
  });

  it("refuses a blank English name before any write", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "GET /api/v1/accounting/groups": GROUPS });
    renderSettings();

    await user.click(await screen.findByRole("button", { name: "Add group" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
    expect(screen.getByLabelText(/^Name \(English\)/)).toHaveAttribute("aria-invalid", "true");
  });

  it("shows the refusal text when the API refuses an archive", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`POST /api/v1/accounting/groups/${GROUPS[1].id}/archive`]: apiError(409, "ACC_REFUSED", "accounting.errors.name_taken"),
    });
    renderSettings();

    const staffRow = (await screen.findByText("Staff", { selector: "p" })).closest("li") as HTMLElement;
    await user.click(within(staffRow).getByRole("button", { name: "Archive" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Archive" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Another live item already has this name.");
  });
});

describe("settings: types", () => {
  it("lists the types of the chosen group with their kind, unit and flags", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/groups/${GROUPS[0].id}/types`]: [LECTURE, OTHER_TYPE],
    });
    const user = userEvent.setup();
    renderSettings();

    await user.click(await screen.findByRole("tab", { name: "Types" }));
    expect(await screen.findByText("Lecture", { selector: "p" })).toBeInTheDocument();
    expect(screen.getByText("Needs a description")).toBeInTheDocument();
    expect(screen.getByText("Counted (quantity × price) · class")).toBeInTheDocument();
  });

  it("locks the kind of a used type", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/groups/${GROUPS[0].id}/types`]: [{ ...LECTURE, used: true }],
    });
    renderSettings();

    await user.click(await screen.findByRole("tab", { name: "Types" }));
    await user.click(await screen.findByRole("button", { name: "Edit" }));
    expect(await screen.findByLabelText(/^Kind/)).toBeDisabled();
  });

  it("does not offer a deduction type in a Log group", async () => {
    const user = userEvent.setup();
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      [`GET /api/v1/accounting/groups/${GROUPS[3].id}/types`]: [],
    });
    renderSettings();

    await user.click(await screen.findByRole("tab", { name: "Types" }));
    await user.selectOptions(await screen.findByLabelText("Group"), GROUPS[3].id);
    await user.click(await screen.findByRole("button", { name: "Add type" }));
    const kind = await screen.findByLabelText(/^Kind/);
    expect(within(kind).queryByRole("option", { name: "Deduction (lowers the balance)" })).not.toBeInTheDocument();
  });
});

describe("settings: money accounts", () => {
  it("shows the seeded accounts in Bangla by kind, and a name the admin typed as it is", async () => {
    mockApi({
      "GET /api/v1/accounting/groups": GROUPS,
      "GET /api/v1/accounting/accounts": [...ACCOUNTS, { ...ACCOUNTS[1], id: "acc-custom", name: "bKash 2", position: 5 }],
    });
    const user = userEvent.setup();
    renderSettings("bn");

    await user.click(await screen.findByRole("tab", { name: "টাকার উৎস" }));
    expect(await screen.findByText("ক্যাশ বাক্স")).toBeInTheDocument();
    // The seeded bKash account is named বিকাশ; the typed account shows the same kind under its own name.
    expect(screen.getAllByText("বিকাশ", { selector: "p" })).toHaveLength(2);
    const custom = screen.getByText("bKash 2").closest("li") as HTMLElement;
    expect(custom).toHaveTextContent("বিকাশ");
  });
});

describe("settings: who may see it", () => {
  it("shows the Master Admin message to a System Admin, and asks the API for nothing", async () => {
    const api = mockApi({ "GET /api/v1/accounting/groups": GROUPS });
    renderSettings("en", fixtures.systemAdmin);

    expect(await screen.findByText("Accounting is for the Master Admin")).toBeInTheDocument();
    expect(api.calls.some((call) => call.path.startsWith("/api/v1/accounting"))).toBe(false);
  });
});
