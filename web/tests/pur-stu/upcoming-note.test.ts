import { describe, expect, it } from "vitest";
import { upcomingPriceNote } from "@/components/student/catalog/door-discovery";

const NOW = Date.parse("2026-11-12T05:00:00Z");
const before = "2026-11-12T04:00:00Z";
const after = "2026-11-12T06:00:00Z";
const later = "2026-11-12T09:00:00Z";

describe("upcoming row note (S-010)", () => {
  it("a row live now says so, whatever the price", () => {
    expect(upcomingPriceNote({ price: 400, liveAt: before, endAt: after }, NOW)).toEqual({ kind: "live" });
  });

  it("a row with a price shows the price", () => {
    expect(upcomingPriceNote({ price: 400, liveAt: later, endAt: after }, NOW)).toEqual({ kind: "price", price: 400 });
  });

  it("a module-only exam with no price says it is sold only in its module", () => {
    expect(upcomingPriceNote({ price: null, moduleOnly: true, liveAt: later, endAt: after }, NOW)).toEqual({
      kind: "moduleOnly",
    });
  });

  it("a row with no price and no module-only flag shows nothing, never 'price not set'", () => {
    expect(upcomingPriceNote({ price: null, liveAt: later, endAt: after }, NOW)).toBeNull();
    expect(upcomingPriceNote({ price: null, moduleOnly: false, liveAt: later, endAt: after }, NOW)).toBeNull();
  });
});
