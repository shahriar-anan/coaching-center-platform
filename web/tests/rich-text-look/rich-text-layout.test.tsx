import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RichTextView, richTextBody } from "@/components/ui/rich-text-view";

describe("saved rich text reads as a document", () => {
  it("is block flow, not a grid, so inline words stay in their line", () => {
    expect(richTextBody).not.toMatch(/(^|\s)grid(\s|$)/);
    expect(richTextBody).toContain("[&>*+*]:mt-3");
  });

  it("keeps a bold word inside its paragraph instead of splitting it into rows", () => {
    const { container } = render(<RichTextView value="<p>Bold <strong>texts</strong>.</p><p>Next</p>" />);
    const paragraphs = container.querySelectorAll("p");
    expect(paragraphs).toHaveLength(2);
    expect(paragraphs[0]).toHaveTextContent("Bold texts.");
    expect(paragraphs[0].querySelector("strong")).toHaveTextContent("texts");
    expect(container.firstElementChild).not.toHaveClass("grid");
  });
});

import { screen, within } from "@testing-library/react";
import { RichTextTopics } from "@/components/staff/catalog/rich-text-topics";
import { renderWithProviders } from "../helpers";

describe("the saved preview keeps English and Bangla apart", () => {
  it("gives each language its own labelled panel inside a card", () => {
    const noop = () => undefined;
    renderWithProviders(
      <RichTextTopics
        previewNote="This is how students will read the description."
        onSave={noop}
        topics={[
          {
            title: "Description",
            valueEn: "<p>Bold <strong>texts</strong>.</p>",
            valueBn: "<p>বাংলা লেখা</p>",
            savedEn: "<p>Bold <strong>texts</strong>.</p>",
            savedBn: "<p>বাংলা লেখা</p>",
            onChange: noop,
          },
        ]}
      />,
      { locale: "en", route: "/staff/modules/x" },
    );
    const card = screen.getByRole("heading", { name: "Description" }).closest("section");
    expect(card).toHaveClass("bg-card", "shadow-sm");
    const english = within(card!).getByText("English").closest("div");
    const bangla = within(card!).getByText("বাংলা").closest("div");
    expect(english).not.toBe(bangla);
    expect(english).toHaveTextContent("Bold texts.");
    expect(bangla).not.toHaveTextContent("Bold texts.");
  });
});
