import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Pagination } from "@/components/ui/pagination";
import { CopyField } from "@/components/ui/copy-field";
import { FileDrop } from "@/components/ui/file-drop";
import { Select } from "@/components/ui/select";
import { renderWithProviders } from "../helpers";

describe("hover and active states on shared controls (FN-3 follow-up)", () => {
  it("pagination buttons take the secondary button's hover and active states", () => {
    renderWithProviders(<Pagination page={1} totalPages={3} onPage={() => undefined} />);
    const next = screen.getByRole("button", { name: /পরের পাতা/ });
    expect(next).toHaveClass("hover:bg-bg", "active:bg-border");
  });

  it("copy field's button has hover and active states", () => {
    renderWithProviders(<CopyField label="লিংক" value="https://example.com" />);
    const copy = screen.getByRole("button", { name: /কপি/ });
    expect(copy).toHaveClass("hover:bg-bg", "active:bg-border");
  });

  it("file drop zone shows a hover border when idle", () => {
    const { container } = renderWithProviders(
      <FileDrop accept="pdf" maxBytes={1024 * 1024} onFile={vi.fn()} />,
    );
    const zone = container.querySelector(".border-dashed");
    expect(zone).toHaveClass("hover:border-accent", "transition-colors");
  });

  it("select has a hover background and an active border", async () => {
    renderWithProviders(
      <Select aria-label="অবস্থা" defaultValue="a">
        <option value="a">এক</option>
      </Select>,
    );
    const select = screen.getByRole("combobox", { name: "অবস্থা" });
    expect(select).toHaveClass("hover:bg-bg", "active:border-accent");
    await userEvent.click(select);
  });
});
