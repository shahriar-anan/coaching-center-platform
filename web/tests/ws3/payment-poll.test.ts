import { describe, expect, it } from "vitest";
import { paymentPollDelay } from "@/lib/api/exam/purchase";

const START = 1_000_000;

describe("payment status polling", () => {
  it("polls every 3 seconds while the payment is pending", () => {
    expect(paymentPollDelay("PENDING", START, START + 5_000)).toBe(3_000);
  });

  it("stops at one minute, even while still pending", () => {
    expect(paymentPollDelay("PENDING", START, START + 59_999)).toBe(3_000);
    expect(paymentPollDelay("PENDING", START, START + 60_000)).toBe(false);
  });

  it("stops as soon as the payment has a final answer", () => {
    expect(paymentPollDelay("PAID", START, START + 1_000)).toBe(false);
    expect(paymentPollDelay("FAILED", START, START + 1_000)).toBe(false);
    expect(paymentPollDelay(undefined, START, START + 1_000)).toBe(false);
  });
});
