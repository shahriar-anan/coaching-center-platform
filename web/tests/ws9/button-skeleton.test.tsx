import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { renderWithProviders } from "../helpers";

describe("Button sizes and states (FN-3)", () => {
  it("gives each size its height: sm 40 px, md 44 px (default), lg 48 px", () => {
    renderWithProviders(
      <>
        <Button size="sm">ছোট</Button>
        <Button>মাঝারি</Button>
        <Button size="lg">বড়</Button>
      </>,
    );
    expect(screen.getByRole("button", { name: "ছোট" })).toHaveClass("min-h-10");
    expect(screen.getByRole("button", { name: "মাঝারি" })).toHaveClass("min-h-11");
    expect(screen.getByRole("button", { name: "বড়" })).toHaveClass("min-h-12");
  });

  it("keeps its label and width while loading, with a spinner on top", () => {
    renderWithProviders(<Button loading>সেভ করুন</Button>);
    const button = screen.getByRole("button", { name: "সেভ করুন" });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
    const label = screen.getByText("সেভ করুন");
    expect(label).toHaveClass("invisible");
    expect(button.querySelector(".animate-spin")).not.toBeNull();
  });

  it("does not fire while loading", async () => {
    const onClick = vi.fn();
    renderWithProviders(<Button loading onClick={onClick}>সেভ করুন</Button>);
    await userEvent.click(screen.getByRole("button", { name: "সেভ করুন" }));
    expect(onClick).not.toHaveBeenCalled();
  });

  it("shows a link with aria-disabled as unavailable while loading", () => {
    renderWithProviders(<Button href="/staff" loading>এগিয়ে যান</Button>);
    const link = screen.getByRole("link", { name: "এগিয়ে যান" });
    expect(link).toHaveAttribute("aria-disabled", "true");
    expect(link).toHaveClass("aria-disabled:pointer-events-none");
  });
});

describe("Skeleton height (FN-3)", () => {
  it("uses the height asked for, so it matches the card it stands for", () => {
    const { container } = renderWithProviders(<Skeleton variant="card" count={3} height="h-40" />);
    const blocks = container.querySelectorAll("[aria-hidden='true'] > div");
    expect(blocks).toHaveLength(3);
    blocks.forEach((block) => expect(block).toHaveClass("h-40"));
  });

  it("keeps the default height when none is given", () => {
    const { container } = renderWithProviders(<Skeleton variant="card" />);
    expect(container.querySelector("[aria-hidden='true'] > div")).toHaveClass("h-24");
  });
});
