import { describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SelectableExamList, type SelectableExam } from "@/components/ui/purchase/selectable-exam-list";
import { renderWithProviders } from "../helpers";

const items: SelectableExam[] = [
  { id: "e1", title: "পদার্থ ১", price: 200 },
  { id: "e2", title: "রসায়ন ১", price: 300 },
  { id: "e3", title: "গণিত ১", price: null },
  { id: "e4", title: "জীববিজ্ঞান ১", price: 150, disabledReason: "আপনার অ্যাক্সেস আছে" },
];

const summary = (count: number, total: number | null) =>
  total === null ? `${count}টি বেছে নেওয়া হয়েছে` : `${count}টি · মোট ${total}`;

function setup(selected: string[] = [], onChange = vi.fn()) {
  renderWithProviders(
    <SelectableExamList
      items={items}
      selected={selected}
      onSelectedChange={onChange}
      summaryLabel={summary}
      action={<button type="button">কিনুন</button>}
    />,
    { locale: "bn" },
  );
  return onChange;
}

describe("SelectableExamList (P-4)", () => {
  it("selects a row when its checkbox is ticked", async () => {
    const onChange = setup();
    await userEvent.click(screen.getByRole("checkbox", { name: /পদার্থ ১/ }));
    expect(onChange).toHaveBeenCalledWith(["e1"]);
  });

  it("deselects a row that is already selected", async () => {
    const onChange = setup(["e1", "e2"]);
    await userEvent.click(screen.getByRole("checkbox", { name: /পদার্থ ১/ }));
    expect(onChange).toHaveBeenCalledWith(["e2"]);
  });

  it("toggles with the Space key", async () => {
    const onChange = setup();
    screen.getByRole("checkbox", { name: /রসায়ন ১/ }).focus();
    await userEvent.keyboard(" ");
    expect(onChange).toHaveBeenCalledWith(["e2"]);
  });

  it("greys out a disabled row, shows its reason as text, and turns its checkbox off", async () => {
    const onChange = setup();
    const box = screen.getByRole("checkbox", { name: /জীববিজ্ঞান ১/ });
    expect(box).toBeDisabled();
    expect(screen.getByText("আপনার অ্যাক্সেস আছে")).toBeInTheDocument();
    await userEvent.click(box);
    expect(onChange).not.toHaveBeenCalled();
  });

  it("shows the count and total in the summary when every selected exam has a price", () => {
    setup(["e1", "e2"]);
    expect(screen.getByText("2টি · মোট 500")).toBeInTheDocument();
  });

  it("shows the count only when a selected exam has no price", () => {
    setup(["e1", "e3"]);
    expect(screen.getByText("2টি বেছে নেওয়া হয়েছে")).toBeInTheDocument();
  });

  it("shows the action beside the summary", () => {
    setup();
    const bar = screen.getByText("0টি বেছে নেওয়া হয়েছে").parentElement as HTMLElement;
    expect(within(bar).getByRole("button", { name: "কিনুন" })).toBeInTheDocument();
  });

  it("says a price is not set for a row without one", () => {
    setup();
    expect(screen.getAllByText("দাম এখনো নির্ধারণ হয়নি।").length).toBeGreaterThan(0);
  });
});
