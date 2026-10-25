import { describe, expect, it } from "vitest";
import bn from "@/i18n/messages/bn/staffCatalog";
import en from "@/i18n/messages/en/staffCatalog";

function keys(tree: Record<string, unknown>, prefix = ""): string[] {
  return Object.entries(tree).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return typeof value === "object" && value !== null ? keys(value as Record<string, unknown>, path) : [path];
  });
}

describe("staffCatalog messages", () => {
  it("has the same keys in Bangla and English", () => {
    expect(keys(en).sort()).toEqual(keys(bn).sort());
  });

  it("has no empty copy", () => {
    for (const key of keys(bn)) {
      const value = key.split(".").reduce<unknown>((node, part) => (node as Record<string, unknown>)[part], bn);
      expect(String(value).trim(), key).not.toBe("");
    }
  });
});
