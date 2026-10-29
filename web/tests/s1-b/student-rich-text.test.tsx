import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RichTextView } from "@/components/ui/rich-text-view";
import { paperLanguageLabel } from "@/components/student/catalog/paper-language";
import { plainExcerpt } from "@/lib/format/rich-text";
import { fixtures, renderWithProviders } from "../helpers";

/** A stand-in translator that returns the key, so the tests read the keys and not the words. */
const keyOf = ((key: string) => key) as unknown as Parameters<typeof paperLanguageLabel>[1];

describe("paper language on an exam page", () => {
  it("BN and EN show their language, and BOTH shows Both", () => {
    expect(paperLanguageLabel("BN", keyOf)).toBe("catalog.exam.langBn");
    expect(paperLanguageLabel("EN", keyOf)).toBe("catalog.exam.langEn");
    expect(paperLanguageLabel("BOTH", keyOf)).toBe("common.paperLanguage.both");
  });

  it("no paper language gives nothing to show, so the row is left out", () => {
    expect(paperLanguageLabel(null, keyOf)).toBeNull();
    expect(paperLanguageLabel(undefined, keyOf)).toBeNull();
    expect(paperLanguageLabel("", keyOf)).toBeNull();
  });
});

describe("a package card shows an excerpt without tags", () => {
  it("removes the tags and keeps the words of each paragraph and item", () => {
    const stored = "<p>Read <strong>all</strong> the rules.</p><ul><li>Arrive early</li></ul>";
    expect(plainExcerpt(stored, 160)).toBe("Read all the rules. Arrive early");
  });

  it("cuts a long description to the card's length, with an ellipsis", () => {
    const text = plainExcerpt(`<p>${"word ".repeat(60)}</p>`, 40);
    expect(text.endsWith("…")).toBe(true);
    expect(text).not.toContain("<");
    expect(text.length).toBeLessThanOrEqual(40);
  });
});

describe("a detail page renders the stored HTML, cleaned", () => {
  it("shows the allowed formatting and drops script and event handlers", () => {
    const stored = '<h2>Rules</h2><p>Be <em>on time</em>.</p><script>document.title = "x"</script><p onmouseover="x()">Done</p>';
    const { container } = renderWithProviders(<RichTextView value={stored} />, { user: fixtures.masterAdmin });

    expect(screen.getByRole("heading", { level: 2, name: "Rules" })).toBeInTheDocument();
    expect(screen.getByText("on time").tagName).toBe("EM");
    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("[onmouseover]")).toBeNull();
  });
});
