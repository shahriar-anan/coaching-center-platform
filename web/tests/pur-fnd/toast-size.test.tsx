import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useToast } from "@/components/ui/toast";
import { renderWithProviders } from "../helpers";

function Trigger() {
  const { toast } = useToast();
  return (
    <button type="button" onClick={() => toast({ title: "সেভ হয়েছে" })}>
      সেভ
    </button>
  );
}

describe("toast size (P-8, S-029)", () => {
  it("has no arbitrary text size in its source and uses the scale's label size", () => {
    const source = readFileSync(resolve(process.cwd(), "src/components/ui/toast.tsx"), "utf8");
    expect(source).not.toMatch(/text-\[/);
    expect(source).toMatch(/text-label/);
  });

  it("renders a shown toast at the label size", async () => {
    renderWithProviders(<Trigger />, { locale: "bn" });
    await userEvent.click(screen.getByRole("button", { name: "সেভ" }));
    const message = await screen.findByText("সেভ হয়েছে");
    const item = message.closest("[class*='text-label']");
    expect(item).not.toBeNull();
    expect(item?.className).not.toMatch(/text-\[/);
  });
});
