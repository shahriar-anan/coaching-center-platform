import { describe, expect, it } from "vitest";
import bn from "@/i18n/messages/bn/staffMarking";
import en from "@/i18n/messages/en/staffMarking";

/** Every key path of a message tree, dotted. */
function keysOf(tree: Record<string, unknown>, prefix = ""): string[] {
  return Object.entries(tree).flatMap(([key, value]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return value !== null && typeof value === "object" ? keysOf(value as Record<string, unknown>, path) : [path];
  });
}

describe("staffMarking copy", () => {
  it("has the same keys in Bangla and English", () => {
    expect(keysOf(bn).sort()).toEqual(keysOf(en).sort());
  });

  it("has no empty string in either language", () => {
    for (const path of keysOf(bn)) {
      const value = path.split(".").reduce<unknown>((node, key) => (node as Record<string, unknown>)[key], bn);
      expect(value, path).not.toBe("");
    }
  });
});
