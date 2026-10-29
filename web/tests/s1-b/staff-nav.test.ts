import { describe, expect, it } from "vitest";
import {
  activeNavFor,
  groupStaffNav,
  openNavFor,
  readNavCollapsed,
  staffNavFor,
  writeNavCollapsed,
  NAV_COLLAPSED_KEY,
} from "@/components/shell/staff-nav";
import type { Role } from "@/lib/api/exam/types";

const keysFor = (role: Role | null) => staffNavFor(role).map((item) => item.key);
const childKeysFor = (role: Role, key: string) =>
  staffNavFor(role)
    .find((item) => item.key === key)
    ?.children?.map((child) => child.key) ?? [];

/** A browser storage in memory, to check what the sidebar writes. */
function memoryStorage() {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => void values.set(key, value),
  };
}

describe("staff navigation by role", () => {
  it("the master admin sees every item, with the create actions under packages and exams", () => {
    expect(keysFor("MASTER_ADMIN")).toEqual(expect.arrayContaining(["announcements", "instructors", "admins", "audit", "systemStatus"]));
    expect(childKeysFor("MASTER_ADMIN", "modules")).toEqual(["modulesAll", "modulesCreate"]);
    expect(childKeysFor("MASTER_ADMIN", "exams")).toEqual(["examsAll", "examsCreate"]);
  });

  it("the system admin sees no master-only pages and no instructor-only items", () => {
    const keys = keysFor("SYSTEM_ADMIN");
    expect(keys).toEqual(expect.arrayContaining(["categories", "students", "payments", "exams", "modules"]));
    expect(keys).not.toEqual(expect.arrayContaining(["announcements", "instructors", "admins", "audit", "systemStatus"]));
    expect(childKeysFor("SYSTEM_ADMIN", "exams")).toEqual(["examsAll", "examsCreate"]);
  });

  it("an instructor sees only what they may open, and no create action", () => {
    const keys = keysFor("INSTRUCTOR");
    expect(keys).toEqual(["dashboard", "modules", "exams", "participation", "submissions", "account"]);
    expect(childKeysFor("INSTRUCTOR", "modules")).toEqual(["modulesAll"]);
    expect(childKeysFor("INSTRUCTOR", "exams")).toEqual(["examsAll"]);
  });

  it("a student and a signed-out visitor see no staff items", () => {
    expect(staffNavFor("STUDENT")).toEqual([]);
    expect(staffNavFor(null)).toEqual([]);
    expect(staffNavFor(undefined)).toEqual([]);
  });

  it("groups the items in section order and leaves out the sections the role cannot see", () => {
    const groups = groupStaffNav(staffNavFor("INSTRUCTOR")).map((section) => section.group);
    expect(groups).toEqual(["overview", "catalog", "exams", "admin"]);
  });
});

describe("the current item and the open group", () => {
  const master = staffNavFor("MASTER_ADMIN");
  const instructor = staffNavFor("INSTRUCTOR");

  it("the dashboard matches only its own path", () => {
    expect(activeNavFor("/staff", master)).toEqual({ key: "dashboard" });
    expect(activeNavFor("/staffing", master)).toBeNull();
  });

  it("a create page is the current sub-item, and its item opens", () => {
    expect(activeNavFor("/staff/modules/new", master)).toEqual({ key: "modules", child: "modulesCreate" });
    expect(openNavFor("/staff/modules/new", master)).toBe("modules");
  });

  it("a page under an item keeps the item current and open, with no sub-item", () => {
    expect(activeNavFor("/staff/exams/exam-1", master)).toEqual({ key: "exams" });
    expect(openNavFor("/staff/exams/exam-1", master)).toBe("exams");
  });

  it("the exams list is the All sub-item", () => {
    expect(activeNavFor("/staff/exams", master)).toEqual({ key: "exams", child: "examsAll" });
  });

  it("an item without sub-items is current but opens nothing", () => {
    expect(activeNavFor("/staff/students", master)).toEqual({ key: "students" });
    expect(openNavFor("/staff/students", master)).toBeNull();
  });

  it("a create page the role cannot open is not a sub-item for that role", () => {
    expect(activeNavFor("/staff/modules/new", instructor)).toEqual({ key: "modules" });
  });

  it("a path outside the navigation matches nothing", () => {
    expect(activeNavFor("/staff/unknown", master)).toBeNull();
  });
});

describe("the collapsed state in this browser", () => {
  it("remembers the choice between visits", () => {
    const storage = memoryStorage();
    writeNavCollapsed(true, storage);
    expect(readNavCollapsed(storage)).toBe(true);
    writeNavCollapsed(false, storage);
    expect(readNavCollapsed(storage)).toBe(false);
  });

  it("reads expanded when nothing is stored", () => {
    expect(readNavCollapsed(memoryStorage())).toBe(false);
    expect(readNavCollapsed(null)).toBe(false);
  });

  it("keeps working when storage throws on read or write", () => {
    const blockedRead = {
      getItem: (): string | null => {
        throw new Error("blocked");
      },
    };
    const blockedWrite = {
      setItem: (): void => {
        throw new Error("full");
      },
    };
    expect(readNavCollapsed(blockedRead)).toBe(false);
    expect(() => writeNavCollapsed(true, blockedWrite)).not.toThrow();
    expect(() => writeNavCollapsed(true, null)).not.toThrow();
  });

  it("stores under its own key", () => {
    const storage = memoryStorage();
    writeNavCollapsed(true, storage);
    expect(storage.getItem(NAV_COLLAPSED_KEY)).toBe("1");
  });
});
