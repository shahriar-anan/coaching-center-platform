import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { NotificationsPage } from "@/components/staff/notifications/notifications-page";
import type { StaffNotification } from "@/lib/api/exam/staff-notifications";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

function note(overrides: Partial<StaffNotification> & Pick<StaffNotification, "id">): StaffNotification {
  return {
    kind: "PAYMENT_RECORDED",
    payload: { amount: 500, method: "BKASH" },
    link: "/staff/my-pay",
    createdAt: "2026-11-05T06:20:00+06:00",
    readAt: null,
    ...overrides,
  };
}

function page(rows: StaffNotification[]) {
  return { content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 };
}

const UNREAD_ROW = note({ id: "n1" });
const READ_ROW = note({
  id: "n2",
  kind: "PAYOUT_ACCOUNT_CHANGED",
  payload: { personName: "Rahim", change: "ADDED" },
  link: "/staff/pay",
  readAt: "2026-11-04T10:00:00+06:00",
});

function renderPage(rows: StaffNotification[], unreadCount = 1) {
  const api = mockApi({
    "GET /api/v1/me/staff-notifications": ({ url }: { url: URL }) => {
      if (url.searchParams.get("unread") === "true") {
        return page(rows.filter((row) => !row.readAt));
      }
      return page(rows);
    },
    "GET /api/v1/me/staff-notifications/unread-count": { count: unreadCount },
    "POST /api/v1/me/staff-notifications/n1/read": { ...UNREAD_ROW, readAt: "2026-11-05T06:30:00+06:00" },
    "POST /api/v1/me/staff-notifications/read-all": { updated: 1 },
  });
  renderWithProviders(<NotificationsPage />, { user: fixtures.instructor, locale: "en", route: "/staff/notifications" });
  return api;
}

describe("the staff notification list", () => {
  it("shows each row's text, and links it to its page", async () => {
    renderPage([UNREAD_ROW, READ_ROW]);
    const paid = await screen.findByRole("link", { name: /You were paid ৳500\.00 by bKash\./ });
    expect(paid).toHaveAttribute("href", "/staff/my-pay");
    const changed = screen.getByRole("link", { name: /Rahim added a payout account\./ });
    expect(changed).toHaveAttribute("href", "/staff/pay");
  });

  it("shows the unread dot only on an unread row", async () => {
    renderPage([UNREAD_ROW, READ_ROW]);
    const unreadRow = await screen.findByRole("link", { name: /You were paid/ });
    expect(within(unreadRow).getByText("Unread:", { exact: false })).toBeInTheDocument();
    const readRow = screen.getByRole("link", { name: /Rahim added/ });
    expect(within(readRow).queryByText("Unread:", { exact: false })).toBeNull();
  });

  it("asks for the unread rows only when the Unread tab is chosen", async () => {
    const api = renderPage([UNREAD_ROW, READ_ROW]);
    await screen.findByRole("link", { name: /You were paid/ });
    await userEvent.click(screen.getByRole("tab", { name: /Unread/ }));
    await waitFor(() => {
      expect(api.calls.some((call) => call.search.includes("unread=true"))).toBe(true);
    });
    expect(await screen.findByRole("link", { name: /You were paid/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Rahim added/ })).toBeNull();
  });

  it("marks a row read when it is opened, and only an unread row is sent", async () => {
    const api = renderPage([UNREAD_ROW, READ_ROW]);
    await userEvent.click(await screen.findByRole("link", { name: /You were paid/ }));
    await waitFor(() => {
      expect(api.calls.some((call) => call.method === "POST" && call.path === "/api/v1/me/staff-notifications/n1/read")).toBe(true);
    });
    expect(api.calls.some((call) => call.path === "/api/v1/me/staff-notifications/n2/read")).toBe(false);
  });

  it("marks everything read and says so", async () => {
    const api = renderPage([UNREAD_ROW, READ_ROW]);
    await userEvent.click(await screen.findByRole("button", { name: "Mark all read" }));
    expect(await screen.findByText("All notifications are marked as read.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST" && call.path === "/api/v1/me/staff-notifications/read-all")).toBe(true);
  });

  it("disables Mark all read when nothing is unread", async () => {
    renderPage([READ_ROW], 0);
    expect(await screen.findByRole("button", { name: "Mark all read" })).toBeDisabled();
  });

  it("says when there are no notifications at all", async () => {
    renderPage([], 0);
    expect(await screen.findByText("No notifications yet.")).toBeInTheDocument();
  });
});
