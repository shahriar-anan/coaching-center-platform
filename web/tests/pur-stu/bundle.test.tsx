import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BundleSelection, bundleTotal } from "@/components/student/catalog/bundle-selection";
import { bundleOrders } from "@/lib/config";
import { apiError, mockApi, renderWithProviders } from "../helpers";

const items = [
  { id: "e1", title: "CRPC", meta: "১২ নভেম্বর", price: 400, owned: false },
  { id: "e2", title: "Evidence", meta: "১৩ নভেম্বর", price: 300, owned: false },
  { id: "e3", title: "Penal", meta: "১৪ নভেম্বর", price: 500, owned: true },
];

describe("bundle selection (S-001)", () => {
  it("the flag is off by default", () => {
    expect(bundleOrders).toBe(false);
  });

  it("the total is the sum of the chosen prices, and null when one chosen exam has no price", () => {
    expect(bundleTotal([])).toBe(0);
    expect(bundleTotal([400, 300])).toBe(700);
    expect(bundleTotal([400, null])).toBeNull();
  });

  it("selecting two exams shows the count and the total", async () => {
    renderWithProviders(<BundleSelection items={items} disabled={false} />, { locale: "bn" });
    await userEvent.click(screen.getByRole("checkbox", { name: /CRPC/ }));
    await userEvent.click(screen.getByRole("checkbox", { name: /Evidence/ }));
    expect(screen.getByText(/২টি পরীক্ষা বেছে নেওয়া হয়েছে · মোট ৳৭০০/)).toBeInTheDocument();
  });

  it("a chosen exam with no price removes the total", async () => {
    const withNoPrice = [{ ...items[0], price: null }, items[1]];
    renderWithProviders(<BundleSelection items={withNoPrice} disabled={false} />, { locale: "bn" });
    await userEvent.click(screen.getByRole("checkbox", { name: /CRPC/ }));
    expect(screen.getByText(/কিছু পরীক্ষার মূল্য ঠিক হয়নি/)).toBeInTheDocument();
  });

  it("an owned exam cannot be chosen and says why", () => {
    renderWithProviders(<BundleSelection items={items} disabled={false} />, { locale: "bn" });
    expect(screen.getByRole("checkbox", { name: /Penal/ })).toBeDisabled();
    expect(screen.getByText("অ্যাক্সেস আছে")).toBeInTheDocument();
  });

  it("buying sends the chosen ids in one request", async () => {
    const { calls } = mockApi({ "POST /api/v1/live-exam-orders": { redirectUrl: null, tranId: "t1", orderId: "o1", status: "PENDING", amount: 700, scope: "BUNDLE", moduleId: null, examId: null } });
    renderWithProviders(<BundleSelection items={items} disabled={false} />, { locale: "bn" });
    await userEvent.click(screen.getByRole("checkbox", { name: /CRPC/ }));
    await userEvent.click(screen.getByRole("checkbox", { name: /Evidence/ }));
    await userEvent.click(screen.getByRole("button", { name: "বেছে নেওয়া পরীক্ষা কিনুন" }));
    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    const post = calls.find((c) => c.method === "POST");
    expect(post?.body).toEqual({ examIds: ["e1", "e2"] });
  });

  it("an owned exam in the request refuses the whole bundle, with the message and no redirect", async () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, assign });
    mockApi({ "POST /api/v1/live-exam-orders": () => apiError(409, "ALREADY_PURCHASED") });
    renderWithProviders(<BundleSelection items={items} disabled={false} />, { locale: "bn" });
    await userEvent.click(screen.getByRole("checkbox", { name: /CRPC/ }));
    await userEvent.click(screen.getByRole("button", { name: "বেছে নেওয়া পরীক্ষা কিনুন" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("বেছে নেওয়া একটি পরীক্ষা আগেই আপনার জন্য চালু আছে। কিছুই কাটা হয়নি।");
    expect(assign).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});
