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
