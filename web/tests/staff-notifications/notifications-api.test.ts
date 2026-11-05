import { describe, expect, it } from "vitest";
import { staffNotificationKeys, staffNotificationsPath, STAFF_NOTIFICATION_PAGE } from "@/lib/api/exam/staff-notifications";

describe("the staff notification paths and keys", () => {
  it("asks for one page of the signed-in person's notifications, with the page size", () => {
    expect(staffNotificationsPath(0, false)).toBe(`/api/v1/me/staff-notifications?page=0&size=${STAFF_NOTIFICATION_PAGE}`);
    expect(STAFF_NOTIFICATION_PAGE).toBe(20);
  });

  it("adds the unread filter only for the Unread tab", () => {
    const path = staffNotificationsPath(2, true);
    const query = new URLSearchParams(path.split("?")[1]);
    expect(path.startsWith("/api/v1/me/staff-notifications?")).toBe(true);
    expect(query.get("page")).toBe("2");
    expect(query.get("unread")).toBe("true");
    expect(new URLSearchParams(staffNotificationsPath(0, false).split("?")[1]).has("unread")).toBe(false);
  });

  it("keeps the Unread and All lists under different keys, both under the one prefix", () => {
    const unread = staffNotificationKeys.list(0, true);
    const all = staffNotificationKeys.list(0, false);
    expect(unread).not.toEqual(all);
    expect(unread[0]).toBe("staffNotifications");
    expect(staffNotificationKeys.unread()[0]).toBe(staffNotificationKeys.all[0]);
  });
});
