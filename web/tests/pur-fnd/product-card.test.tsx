import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { ProductCard } from "@/components/ui/purchase/product-card";
import { renderWithProviders } from "../helpers";

describe("ProductCard (P-5)", () => {
  it("is one link to its href", () => {
    renderWithProviders(
      <ProductCard href="/modules/bjs" title="১৯তম বিজেএস" price={400} examCount={3} />,
      { locale: "bn" },
    );
    expect(screen.getByRole("link", { name: /১৯তম বিজেএস/ })).toHaveAttribute("href", "/modules/bjs");
  });

  it("shows the price with the taka sign when set", () => {
    renderWithProviders(<ProductCard href="/x" title="মডিউল" price={2500} examCount={1} />, { locale: "bn" });
    expect(screen.getByText("৳২,৫০০")).toBeInTheDocument();
  });

  it("says the price is not set when null", () => {
    renderWithProviders(<ProductCard href="/x" title="মডিউল" price={null} examCount={1} />, { locale: "bn" });
    expect(screen.getByText("দাম এখনো নির্ধারণ হয়নি।")).toBeInTheDocument();
  });

  it("formats the exam count with Bengali digits in Bangla and western digits in English", () => {
    const { unmount } = renderWithProviders(<ProductCard href="/x" title="মডিউল" price={1} examCount={12} />, { locale: "bn" });
    expect(screen.getByText("১২টি পরীক্ষা")).toBeInTheDocument();
    unmount();
    renderWithProviders(<ProductCard href="/x" title="Module" price={1} examCount={12} />, { locale: "en" });
    expect(screen.getByText("12 exams")).toBeInTheDocument();
  });

  it("shows the subtitle, pills and footer", () => {
    renderWithProviders(
      <ProductCard href="/x" title="মডিউল" subtitle="নতুন ব্যাচ" pills={<span>অ্যাক্সেস আছে</span>} price={1} examCount={1} footer={<span>ফুটার</span>} />,
      { locale: "bn" },
    );
    expect(screen.getByText("নতুন ব্যাচ")).toBeInTheDocument();
    expect(screen.getByText("অ্যাক্সেস আছে")).toBeInTheDocument();
    expect(screen.getByText("ফুটার")).toBeInTheDocument();
  });
});
