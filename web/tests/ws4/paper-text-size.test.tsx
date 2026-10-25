import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { PaperTextSizeControl, usePaperTextSize } from "@/components/student/sitting/paper-text-size";
import { renderWithProviders } from "../helpers";

function Harness() {
  const size = usePaperTextSize();
  return (
    <div data-testid="paper" style={size.style}>
      <PaperTextSizeControl size={size} />
    </div>
  );
}

describe("paper text size", () => {
  beforeEach(() => window.localStorage.clear());

  it("starts at 16 px and cannot go smaller", async () => {
    renderWithProviders(<Harness />);
    expect(screen.getByTestId("paper").style.getPropertyValue("--paper-font")).toBe("16px");
    expect(screen.getByRole("button", { name: "লেখা ছোট করুন" })).toBeDisabled();
  });

  it("grows one step at a time and remembers the size on this device", async () => {
    renderWithProviders(<Harness />);
    await userEvent.click(screen.getByRole("button", { name: "লেখা বড় করুন" }));
    expect(screen.getByTestId("paper").style.getPropertyValue("--paper-font")).toBe("18px");
    expect(window.localStorage.getItem("paperTextSize")).toBe("1");
  });

  it("stops at the largest step", async () => {
    window.localStorage.setItem("paperTextSize", "2");
    renderWithProviders(<Harness />);
    expect(screen.getByRole("button", { name: "লেখা বড় করুন" })).toBeDisabled();
  });
});
