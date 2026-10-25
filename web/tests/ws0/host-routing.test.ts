import { describe, expect, it } from "vitest";
import { hostConfig } from "@/lib/config";
import { requestHost, routeForHost } from "@/lib/url/host-routing";

const split = hostConfig({
  siteUrl: "https://exam.example.com",
  staffUrl: "https://admin.exam.example.com",
});
const one = hostConfig({
  siteUrl: "https://srv2089893.hstgr.cloud",
  staffUrl: "https://srv2089893.hstgr.cloud",
});

function decide(host: string, pathname: string, search = "", config = split) {
  return routeForHost({ host, pathname, search, config });
}

describe("routeForHost on the admin host", () => {
  const admin = "admin.exam.example.com";

  it("sends the root to /staff", () => {
    expect(decide(admin, "/")).toEqual({
      action: "redirect",
      location: "https://admin.exam.example.com/staff",
    });
  });

  it("serves /staff and the shared auth pages", () => {
    expect(decide(admin, "/staff/students")).toEqual({ action: "next" });
    expect(decide(admin, "/login", "?next=%2Fstaff")).toEqual({ action: "next" });
    expect(decide(admin, "/activate")).toEqual({ action: "next" });
  });

  it("sends student routes to the student site, keeping the query", () => {
    expect(decide(admin, "/mcq", "?page=2")).toEqual({
      action: "redirect",
      location: "https://exam.example.com/mcq?page=2",
    });
  });
});

describe("routeForHost on the student host", () => {
  const site = "exam.example.com";

  it("sends /staff routes to the admin host", () => {
    expect(decide(site, "/staff/modules", "?q=bcs")).toEqual({
      action: "redirect",
      location: "https://admin.exam.example.com/staff/modules?q=bcs",
    });
  });

  it("serves student routes", () => {
    expect(decide(site, "/")).toEqual({ action: "next" });
    expect(decide(site, "/exam/abc/sit")).toEqual({ action: "next" });
  });
});

describe("routeForHost with one host", () => {
  it("never redirects, so both surfaces live side by side", () => {
    expect(routeForHost({ host: "srv2089893.hstgr.cloud", pathname: "/", search: "", config: one })).toEqual({
      action: "next",
    });
    expect(
      routeForHost({ host: "srv2089893.hstgr.cloud", pathname: "/staff", search: "", config: one }),
    ).toEqual({ action: "next" });
  });
});

describe("requestHost", () => {
  it("prefers the forwarded host and drops the port-less duplicates", () => {
    const headers = new Map([
      ["x-forwarded-host", "Admin.Exam.Example.com, other"],
      ["host", "127.0.0.1:3000"],
    ]);
    expect(requestHost({ get: (k) => headers.get(k) ?? null })).toBe("admin.exam.example.com");
  });
});
