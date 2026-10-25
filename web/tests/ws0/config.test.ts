import { describe, expect, it } from "vitest";
import { hostConfig, hrefOn } from "@/lib/config";

const STAGING = { siteUrl: "https://srv2089893.hstgr.cloud", staffUrl: "https://srv2089893.hstgr.cloud" };
const SPLIT = { siteUrl: "https://exam.example.com", staffUrl: "https://admin.exam.example.com" };

describe("hostConfig", () => {
  it("treats equal origins as one host", () => {
    const cfg = hostConfig(STAGING);
    expect(cfg.splitHosts).toBe(false);
  });

  it("detects two origins", () => {
    expect(hostConfig(SPLIT).splitHosts).toBe(true);
  });

  it("uses the site host for staff when the staff value is missing", () => {
    expect(hostConfig({ siteUrl: SPLIT.siteUrl }).staffUrl).toBe(SPLIT.siteUrl);
  });

  it("strips trailing slashes", () => {
    expect(hostConfig({ siteUrl: "https://exam.example.com/" }).siteUrl).toBe("https://exam.example.com");
  });
});

describe("hrefOn in one-host mode", () => {
  const cfg = hostConfig(STAGING);

  it("returns a relative path for both surfaces", () => {
    expect(hrefOn(cfg, "site", "/login")).toBe("/login");
    expect(hrefOn(cfg, "staff", "/staff")).toBe("/staff");
  });
});

describe("hrefOn in split-host mode", () => {
  const cfg = hostConfig(SPLIT);

  it("returns the absolute staff URL when the visitor is on the student host", () => {
    expect(hrefOn(cfg, "staff", "/staff", "https://exam.example.com")).toBe(
      "https://admin.exam.example.com/staff",
    );
  });

  it("returns a relative path when the visitor is already on the target host", () => {
    expect(hrefOn(cfg, "staff", "/staff", "https://admin.exam.example.com")).toBe("/staff");
    expect(hrefOn(cfg, "site", "/", "https://exam.example.com")).toBe("/");
  });

  it("returns the absolute student URL from the admin host", () => {
    expect(hrefOn(cfg, "site", "/login", "https://admin.exam.example.com")).toBe(
      "https://exam.example.com/login",
    );
  });

  it("adds a leading slash to paths that lack one", () => {
    expect(hrefOn(cfg, "site", "account", "https://admin.exam.example.com")).toBe(
      "https://exam.example.com/account",
    );
  });
});
