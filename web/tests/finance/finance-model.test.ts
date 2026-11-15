import { describe, expect, it } from "vitest";
import {
  bucketExpense,
  bucketIncome,
  changeParts,
  chartDomain,
  entriesHref,
  financeHref,
  monthBuckets,
  parseFinanceUrl,
  presetRange,
  rangeProblem,
  selectedSources,
  toggleSource,
  weekBuckets,
  weekStart,
  yPercent,
  type FinanceUrlState,
} from "@/components/staff/finance/finance-model";

// 13 November 2026, 09:00 in Dhaka: the "today" every date rule below is measured from.
const NOW = Date.UTC(2026, 10, 13, 3, 0);
const BATCH = "9b2f6a2e-1c3d-4e5f-8a9b-0c1d2e3f4a5b";

function params(query: string): URLSearchParams {
  return new URLSearchParams(query);
}

describe("the finance URL", () => {
  it("defaults to this month, every source, month grouping and the source tab", () => {
    const state = parseFinanceUrl(params(""), NOW);
    expect(state).toMatchObject({
      range: "month",
      from: "2026-11-01",
      to: "2026-11-13",
      sources: null,
      batchId: null,
      group: "month",
      tab: "source",
      page: 0,
    });
  });

  it("keeps the dates the URL names and drops unknown sources, a malformed batch and a bad page", () => {
    const state = parseFinanceUrl(
      params(`range=custom&from=2026-10-01&to=2026-10-31&sources=offline_fees,NOPE,online_orders&batch=not-a-uuid&page=-4&tab=nope`),
      NOW,
    );
    expect(state).toMatchObject({
      range: "custom",
      from: "2026-10-01",
      to: "2026-10-31",
      sources: ["ONLINE_ORDERS", "OFFLINE_FEES"],
      batchId: null,
      page: 0,
      tab: "source",
    });
  });

  it("reads a valid batch and page, and treats a sources list of only unknown names as all sources", () => {
    const state = parseFinanceUrl(params(`batch=${BATCH}&page=3&tab=entries&sources=FOO`), NOW);
    expect(state.batchId).toBe(BATCH);
    expect(state.page).toBe(3);
    expect(state.tab).toBe("entries");
    expect(state.sources).toBeNull();
  });

  it("writes every field it reads, so a link opens exactly the same view", () => {
    const state: FinanceUrlState = {
      range: "custom",
      from: "2026-10-01",
      to: "2026-10-31",
      sources: ["OFFLINE_FEES", "OFFLINE_INSTRUCTORS"],
      batchId: BATCH,
      group: "week",
      tab: "entries",
      page: 2,
    };
    const href = financeHref(state);
    const again = parseFinanceUrl(new URL(href, "http://x").searchParams, NOW);
    expect(again).toEqual(state);
  });

  it("links to the entries behind a figure, on the first page, with its sources and dates", () => {
    const state = parseFinanceUrl(params("range=month&tab=source&page=4"), NOW);
    const href = entriesHref(state, ["STAFF_PAY"], null);
    const url = new URL(href, "http://x");
    expect(url.pathname).toBe("/staff/finance/entries");
    const query = url.searchParams;
    expect(query.has("tab")).toBe(false);
    expect(query.get("sources")).toBe("STAFF_PAY");
    expect(query.get("range")).toBe("month");
    expect(query.get("from")).toBe("2026-11-01");
    expect(query.has("batch")).toBe(false);
    expect(query.has("page")).toBe(false);
  });
});

describe("the date presets (Dhaka time)", () => {
  it("gives this month, last month, and this year", () => {
    expect(presetRange("month", NOW)).toEqual({ from: "2026-11-01", to: "2026-11-13" });
    expect(presetRange("last", NOW)).toEqual({ from: "2026-10-01", to: "2026-10-31" });
    expect(presetRange("year", NOW)).toEqual({ from: "2026-01-01", to: "2026-11-13" });
  });

  it("goes back across a year end for last month in January", () => {
    const january = Date.UTC(2027, 0, 5, 3, 0);
    expect(presetRange("last", january)).toEqual({ from: "2026-12-01", to: "2026-12-31" });
  });

  it("flags a range that is backwards, too long, or missing a date", () => {
    expect(rangeProblem("2026-11-02", "2026-11-01")).toBe("backwards");
    // The longest range is MAX_RANGE_DAYS (3653 days), the same limit the API enforces.
    expect(rangeProblem("2014-01-01", "2026-06-01")).toBe("long");
    expect(rangeProblem("2024-01-01", "2026-06-01")).toBeNull();
    expect(rangeProblem("", "2026-06-01")).toBe("invalid");
    expect(rangeProblem("2026-01-01", "2026-12-31")).toBeNull();
  });
});

describe("the source checkboxes", () => {
  const available = ["ONLINE_ORDERS", "OFFLINE_FEES", "STAFF_PAY"] as const;

  it("shows every available source when none is named, and only the named ones otherwise", () => {
    expect(selectedSources(null, available)).toEqual(["ONLINE_ORDERS", "OFFLINE_FEES", "STAFF_PAY"]);
    expect(selectedSources(["STAFF_PAY", "ONLINE_ORDERS"], available)).toEqual(["ONLINE_ORDERS", "STAFF_PAY"]);
    expect(selectedSources(["DIRECT_ENROLMENT"], available)).toEqual([]);
  });

  it("clears one source, and goes back to all when every available source is ticked again", () => {
    expect(toggleSource([...available], available, "OFFLINE_FEES")).toEqual(["ONLINE_ORDERS", "STAFF_PAY"]);
    expect(toggleSource(["ONLINE_ORDERS", "STAFF_PAY"], available, "OFFLINE_FEES")).toBeNull();
  });

  it("never clears the last ticked source", () => {
    expect(toggleSource(["STAFF_PAY"], available, "STAFF_PAY")).toEqual(["STAFF_PAY"]);
  });
});

describe("weeks and months for the chart", () => {
  it("starts a week on the Monday of the date's week", () => {
    expect(weekStart("2026-11-13")).toBe("2026-11-09");
    expect(weekStart("2026-11-09")).toBe("2026-11-09");
    expect(weekStart("2026-11-15")).toBe("2026-11-09");
  });

  it("gives every month of the range a column, with zero where there are no entries", () => {
    const buckets = monthBuckets("2026-10-15", "2026-12-02", [
      { month: "2026-11", income: 900, expense: 0, net: 900, bySource: { ONLINE_ORDERS: 900 } },
    ]);
    expect(buckets.map((bucket) => bucket.key)).toEqual(["2026-10", "2026-11", "2026-12"]);
    expect(buckets[0].bySource).toEqual({});
    expect(buckets[1].bySource).toEqual({ ONLINE_ORDERS: 900 });
  });

  it("puts each entry in the week it was paid in", () => {
    const buckets = weekBuckets("2026-11-04", "2026-11-15", [
      { source: "ONLINE_ORDERS", date: "2026-11-05", description: "", amount: 300, direction: "INCOME", reference: null, id: null, batchId: null },
      { source: "ONLINE_ORDERS", date: "2026-11-08", description: "", amount: 200, direction: "INCOME", reference: null, id: null, batchId: null },
      { source: "STAFF_PAY", date: "2026-11-10", description: "", amount: 50, direction: "EXPENSE", reference: null, id: null, batchId: null },
    ]);
    expect(buckets.map((bucket) => bucket.key)).toEqual(["2026-11-02", "2026-11-09"]);
    // 5 and 8 November are in the week that starts on Monday 2 November; 10 November starts the next week.
    expect(buckets[0].bySource).toEqual({ ONLINE_ORDERS: 500 });
    expect(buckets[1].bySource).toEqual({ STAFF_PAY: 50 });
  });
});

describe("the chart arithmetic", () => {
  const bucket = { key: "2026-11", bySource: { ONLINE_ORDERS: 20000, OFFLINE_FEES: 10000, STAFF_PAY: 12000 } };

  it("adds only the sources the chart shows", () => {
    expect(bucketIncome(bucket, ["ONLINE_ORDERS", "STAFF_PAY"])).toBe(20000);
    expect(bucketIncome(bucket, ["ONLINE_ORDERS", "OFFLINE_FEES"])).toBe(30000);
    expect(bucketExpense(bucket, ["ONLINE_ORDERS", "STAFF_PAY"])).toBe(12000);
    expect(bucketExpense(bucket, ["ONLINE_ORDERS"])).toBe(0);
  });

  it("keeps zero on the scale, with a room below it for a loss", () => {
    // Income 30,000 rounds up to the 1-2-5 step above it, 50,000.
    expect(chartDomain([bucket], ["ONLINE_ORDERS", "OFFLINE_FEES", "STAFF_PAY"])).toEqual({ min: 0, max: 50000 });
    // Expenditure 3,200 gives a top of 5,000; the 2,200 loss gives a bottom of -5,000, so zero sits in the middle.
    const loss = { key: "2026-11", bySource: { ONLINE_ORDERS: 1000, STAFF_PAY: 3200 } };
    const domain = chartDomain([loss], ["ONLINE_ORDERS", "STAFF_PAY"]);
    expect(domain).toEqual({ min: -5000, max: 5000 });
    expect(yPercent(0, domain)).toBe(50);
  });

  it("gives the change arrow and size, and nothing when there is no earlier figure", () => {
    expect(changeParts(12.5)).toEqual({ direction: "up", size: 12.5 });
    expect(changeParts(-4)).toEqual({ direction: "down", size: 4 });
    expect(changeParts(0)).toEqual({ direction: "flat", size: 0 });
    expect(changeParts(null)).toEqual({ direction: "none", size: 0 });
  });
});
