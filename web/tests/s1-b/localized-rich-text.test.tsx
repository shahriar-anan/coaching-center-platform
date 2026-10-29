import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { LocalizedRichText } from "@/components/staff/catalog/localized-rich-text";
import { fixtures, renderWithProviders } from "../helpers";

beforeAll(() => {
  // jsdom has no layout; ProseMirror measures text and asks for the element under a click.
  if (!document.elementFromPoint) {
    document.elementFromPoint = () => null;
  }
  const rect = () => ({ x: 0, y: 0, top: 0, left: 0, bottom: 0, right: 0, width: 0, height: 0, toJSON: () => ({}) }) as DOMRect;
  const none = () => [] as unknown as DOMRectList;
  for (const proto of [Element.prototype, Text.prototype, Range.prototype]) {
    const measured = proto as unknown as { getClientRects?: () => DOMRectList; getBoundingClientRect?: () => DOMRect };
    measured.getClientRects ??= none;
    measured.getBoundingClientRect ??= rect;
  }
});

function render(props: Partial<React.ComponentProps<typeof LocalizedRichText>> = {}) {
  const onChange = vi.fn();
  renderWithProviders(
    <LocalizedRichText title="Description" valueEn="<p>Hello</p>" valueBn="" onChange={onChange} {...props} />,
    { user: fixtures.masterAdmin, locale: "en" },
  );
  return onChange;
}

describe("localized rich text", () => {
  it("shows one editor, starting with English, and marks the empty language", async () => {
    render();

    expect(screen.getAllByRole("textbox")).toHaveLength(1);
    expect(await screen.findByRole("textbox", { name: "Description (English)" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "English" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "বাংলা •" })).toHaveAttribute("aria-pressed", "false");
  });

  it("switches to the Bangla text without losing the English text", async () => {
    render();

    await userEvent.click(screen.getByRole("button", { name: "বাংলা •" }));
    expect(await screen.findByRole("textbox", { name: "Description (বাংলা)" })).toBeInTheDocument();
    expect(screen.getByText("Nothing written yet")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "বাংলা •" }));
    await userEvent.click(screen.getByRole("button", { name: "English" }));
    const english = await screen.findByRole("textbox", { name: "Description (English)" });
    expect(await within(english).findByText("Hello")).toBeInTheDocument();
  });

  it("reports an edit with the language that was open", async () => {
    const onChange = render();

    await userEvent.click(screen.getByRole("button", { name: "বাংলা •" }));
    const box = await screen.findByRole("textbox", { name: "Description (বাংলা)" });
    await userEvent.click(box);
    await userEvent.keyboard("x");

    await waitFor(() => expect(onChange).toHaveBeenCalledWith("bn", expect.stringContaining("x")));
  });

  it("says the language is saved, and keeps Save off, when nothing has changed", async () => {
    render({ savedEn: "<p>Hello</p>", savedBn: "", onSave: vi.fn() });

    expect(screen.getByText("English saved")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("names the language with unsaved changes and saves on request", async () => {
    const onSave = vi.fn();
    render({ valueBn: "<p>নতুন</p>", savedEn: "<p>Hello</p>", savedBn: "", onSave });

    expect(screen.getByText("English saved. বাংলা has unsaved changes")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "বাংলা" }));
    expect(screen.getByText("বাংলা: unsaved changes")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(onSave).toHaveBeenCalledTimes(1);
  });

  it("moves to the language that has an error and shows it", async () => {
    render({ errorBn: "Write the Bangla description." });

    expect(await screen.findByRole("alert")).toHaveTextContent("Write the Bangla description.");
    expect(screen.getByRole("textbox", { name: "Description (বাংলা)" })).toBeInTheDocument();
  });
});
