import { describe, expect, it } from "vitest";
import { bn as bnAll, en as enAll } from "@/i18n/messages";
import bn from "@/i18n/messages/bn/staffReviews";
import en from "@/i18n/messages/en/staffReviews";

/** Every key path of a message tree, dotted. */
function keysOf(tree: Record<string, unknown>, prefix = ""): string[] {
  return Object.entries(tree).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return value !== null && typeof value === "object" ? keysOf(value as Record<string, unknown>, path) : [path];
  });
}

function valueOf(tree: Record<string, unknown>, path: string): string {
  return String(path.split(".").reduce<unknown>((node, key) => (node as Record<string, unknown>)[key], tree));
}

describe("staff reviews copy", () => {
  it("has the same keys in Bangla and English", () => {
    expect(keysOf(bn).sort()).toEqual(keysOf(en).sort());
  });

  it("has text in both languages for every key", () => {
    for (const path of keysOf(en)) {
      expect(valueOf(en, path).trim(), `en ${path}`).not.toBe("");
      expect(valueOf(bn, path).trim(), `bn ${path}`).not.toBe("");
    }
  });

  it("never writes পেজ in Bangla (the word for a page is পাতা)", () => {
    for (const path of keysOf(bn)) {
      expect(valueOf(bn, path), path).not.toContain("পেজ");
    }
    expect(String(valueOf(bnAll as Record<string, unknown>, "common.staffNav.reviews"))).not.toContain("পেজ");
  });

  it("uses the agreed Bangla words for exam, package, review and comment", () => {
    expect(valueOf(bn, "kind.exam")).toBe("এক্সাম");
    expect(valueOf(bn, "kind.package")).toBe("প্যাকেজ");
    expect(valueOf(bn, "title")).toBe("রিভিউ");
    expect(valueOf(bn, "row.starsOnly")).toContain("মন্তব্য");
  });

  it("names the sidebar item in both languages", () => {
    expect(valueOf(enAll as Record<string, unknown>, "common.staffNav.reviews")).toBe("Reviews");
    expect(valueOf(bnAll as Record<string, unknown>, "common.staffNav.reviews")).toBe("রিভিউ");
  });
});
