import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { RichTextEditor } from "@/components/ui/rich-text-editor";
import { RichTextView } from "@/components/ui/rich-text-view";
import { htmlFromStored, linkFromInput, plainExcerpt } from "@/lib/format/rich-text";
import { fixtures, renderWithProviders } from "../helpers";

describe("rich text viewer", () => {
  it("shows plain text as it is, on one line when it has no line breaks", () => {
    const { container } = renderWithProviders(<RichTextView value="Read the rules first" />, { user: fixtures.masterAdmin });

    expect(screen.getByText("Read the rules first")).toBeInTheDocument();
    expect(container.querySelector("strong")).toBeNull();
  });

  it("keeps the line breaks of plain text", () => {
    const { container } = renderWithProviders(<RichTextView value={"First line\nSecond line"} />, { user: fixtures.masterAdmin });

    expect(container.querySelector(".whitespace-pre-line")).toHaveTextContent("First line");
    expect(container.querySelector(".whitespace-pre-line")?.textContent).toBe("First line\nSecond line");
  });

  it("shows the allowed tags of HTML, and a safe link with rel", () => {
    const html =
      '<h2>Rules</h2><p>Read <strong>all</strong> and <em>carefully</em>.</p><ul><li>One</li></ul><p><a href="https://example.com" target="_blank">the site</a></p>';
    renderWithProviders(<RichTextView value={html} />, { user: fixtures.masterAdmin });

    expect(screen.getByRole("heading", { level: 2, name: "Rules" })).toBeInTheDocument();
    expect(screen.getByText("all").tagName).toBe("STRONG");
    expect(screen.getByText("carefully").tagName).toBe("EM");
    expect(screen.getByRole("list")).toHaveTextContent("One");
    const link = screen.getByRole("link", { name: "the site" });
    expect(link).toHaveAttribute("href", "https://example.com");
    expect(link).toHaveAttribute("rel", "noopener nofollow");
    expect(link).not.toHaveAttribute("target");
  });

  it("never runs a script, an event handler or a javascript link", () => {
    (window as unknown as { __rtRan?: boolean }).__rtRan = undefined;
    const html =
      '<p>Safe</p><script>window.__rtRan = true</script><img src="x" onerror="window.__rtRan = true"><p onclick="window.__rtRan = true">Tap</p><a href="javascript:window.__rtRan = true">bad link</a>';
    const { container } = renderWithProviders(<RichTextView value={html} />, { user: fixtures.masterAdmin });

    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("[onclick]")).toBeNull();
    expect(screen.getByText("Tap")).not.toHaveAttribute("onclick");
    expect(container.querySelector("a")).toBeNull();
    expect(container).toHaveTextContent("bad link");
    expect((window as unknown as { __rtRan?: boolean }).__rtRan).toBeUndefined();
  });

  it("keeps centre alignment on a paragraph and drops other styles", () => {
    renderWithProviders(<RichTextView value='<p style="text-align: center; color: red">Middle</p>' />, { user: fixtures.masterAdmin });

    const paragraph = screen.getByText("Middle");
    expect(paragraph.style.textAlign).toBe("center");
    expect(paragraph.style.color).toBe("");
  });

  it("shows nothing for an empty value", () => {
    const { container } = renderWithProviders(<RichTextView value="   " />, { user: fixtures.masterAdmin });

    expect(container.textContent).toBe("");
  });
});

describe("plainExcerpt", () => {
  it("removes the tags, decodes the entities and joins the paragraphs with a space", () => {
    expect(plainExcerpt("<p>Hello <strong>you</strong> &amp; welcome</p><p>Next</p>", 100)).toBe("Hello you & welcome Next");
  });

  it("cuts a long text to the limit with an ellipsis", () => {
    expect(plainExcerpt("abcdefghij", 5)).toBe("abcd…");
    expect(plainExcerpt("abcdefghij", 5)).toHaveLength(5);
  });

  it("only collapses the spaces of plain text, line breaks included", () => {
    expect(plainExcerpt("Line one\nLine   two", 50)).toBe("Line one Line two");
  });

  it("gives an empty text for no value", () => {
    expect(plainExcerpt(null, 10)).toBe("");
    expect(plainExcerpt("", 10)).toBe("");
  });
});

describe("rich text helpers", () => {
  it("opens plain text as one paragraph per line, and leaves HTML alone", () => {
    expect(htmlFromStored("One & <5 wins\nTwo")).toBe("<p>One &amp; &lt;5 wins</p><p>Two</p>");
    expect(htmlFromStored("<p>Kept</p>")).toBe("<p>Kept</p>");
  });

  it("accepts a bare domain as https and refuses other schemes", () => {
    expect(linkFromInput("example.com/rules")).toBe("https://example.com/rules");
    expect(linkFromInput("mailto:help@example.com")).toBe("mailto:help@example.com");
    expect(linkFromInput("javascript:alert(1)")).toBeNull();
    expect(linkFromInput("   ")).toBeNull();
  });
});

describe("rich text editor toolbar", () => {
  beforeAll(() => {
    // jsdom has no layout. ProseMirror asks for the element under a click and measures text, so those calls get empty answers.
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

  it("turns bold on for the typed text", async () => {
    const onChange = vi.fn();
    renderWithProviders(<RichTextEditor value="" onChange={onChange} label="Rules" />, { user: fixtures.masterAdmin });

    const bold = await screen.findByRole("button", { name: "গাঢ়" });
    await waitFor(() => expect(bold).toBeEnabled());
    await userEvent.click(screen.getByRole("textbox"));
    await userEvent.click(bold);
    expect(bold).toHaveAttribute("aria-pressed", "true");
    await userEvent.keyboard("Read");

    expect(onChange).toHaveBeenLastCalledWith(expect.stringContaining("<strong>Read</strong>"));
  });

  it("makes a bulleted list from the paragraph", async () => {
    const onChange = vi.fn();
    renderWithProviders(<RichTextEditor value="<p>Rule one</p>" onChange={onChange} label="Rules" />, { user: fixtures.masterAdmin });

    const list = await screen.findByRole("button", { name: "বুলেট তালিকা" });
    await waitFor(() => expect(list).toBeEnabled());
    await userEvent.click(screen.getByRole("textbox"));
    await userEvent.click(list);

    expect(list).toHaveAttribute("aria-pressed", "true");
    expect(within(screen.getByRole("textbox")).getByRole("list")).toHaveTextContent("Rule one");
    expect(onChange).toHaveBeenLastCalledWith(expect.stringContaining("<ul><li><p>Rule one</p></li></ul>"));
  });
});
