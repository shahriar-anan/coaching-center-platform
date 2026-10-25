import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfirmSheet } from "@/components/ui/purchase/confirm-sheet";
import { Button } from "@/components/ui/button";
import { renderWithProviders } from "../helpers";

function Harness({ primary, leavingNote }: { primary: React.ReactNode; leavingNote?: string }) {
  return (
    <ConfirmSheet
      open
      onOpenChange={vi.fn()}
      title="কেনার নিশ্চিতকরণ"
      amountLabel="৳৪০০"
      unlock="এই মডিউলের সব পরীক্ষা খুলে যাবে।"
      primary={primary}
      cancelLabel="বাতিল"
      leavingNote={leavingNote}
    />
  );
}

describe("ConfirmSheet (P-3)", () => {
  it("shows the title, amount, unlock sentence and both buttons", () => {
    renderWithProviders(<Harness primary={<Button>নিশ্চিত করুন</Button>} />, { locale: "bn" });
    expect(screen.getByRole("dialog", { name: "কেনার নিশ্চিতকরণ" })).toBeInTheDocument();
    expect(screen.getByText("৳৪০০")).toBeInTheDocument();
    expect(screen.getByText("এই মডিউলের সব পরীক্ষা খুলে যাবে।")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "বাতিল" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "নিশ্চিত করুন" })).toBeInTheDocument();
  });

  it("moves focus to the primary button when it opens", async () => {
    renderWithProviders(<Harness primary={<Button>নিশ্চিত করুন</Button>} />, { locale: "bn" });
    await waitFor(() => expect(screen.getByRole("button", { name: "নিশ্চিত করুন" })).toHaveFocus());
  });

  it("closes on Escape and on cancel", async () => {
    const onOpenChange = vi.fn();
    renderWithProviders(
      <ConfirmSheet
        open
        onOpenChange={onOpenChange}
        title="কেনার নিশ্চিতকরণ"
        amountLabel="৳৪০০"
        unlock="খুলবে।"
        primary={<Button>নিশ্চিত করুন</Button>}
        cancelLabel="বাতিল"
      />,
      { locale: "bn" },
    );
    await userEvent.keyboard("{Escape}");
    expect(onOpenChange).toHaveBeenLastCalledWith(false);
    await userEvent.click(screen.getByRole("button", { name: "বাতিল" }));
    expect(onOpenChange).toHaveBeenCalledTimes(2);
  });

  it("keeps the primary disabled while it is a loading button", () => {
    renderWithProviders(<Harness primary={<Button loading>নিশ্চিত করুন</Button>} />, { locale: "bn" });
    expect(screen.getByRole("button", { name: "নিশ্চিত করুন" })).toBeDisabled();
  });

  it("shows the leaving note under the buttons", () => {
    renderWithProviders(<Harness primary={<Button>নিশ্চিত করুন</Button>} leavingNote="পেমেন্ট চলাকালে এই পাতা বন্ধ করবেন না।" />, { locale: "bn" });
    expect(screen.getByText("পেমেন্ট চলাকালে এই পাতা বন্ধ করবেন না।")).toBeInTheDocument();
  });
});
