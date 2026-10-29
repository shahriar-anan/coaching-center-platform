import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DetailLayout } from "@/components/student/catalog/detail-layout";
import { PackageCover } from "@/components/ui/package/package-cover";

function Sample() {
  return (
    <DetailLayout
      header={<h1>Package title</h1>}
      card={<section aria-label="Buy">Price and buy</section>}
      cover={<div data-testid="cover">Cover</div>}
    >
      <p>Details</p>
    </DetailLayout>
  );
}

/** The document order of the four slots is the phone order: header, card, cover, details (Phase 4, S4.3). */
function order(nodes: HTMLElement[]) {
  return nodes.every((node, index) => index === 0 || (nodes[index - 1].compareDocumentPosition(node) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0);
}

describe("the shared detail layout (Phase 4, S4.3)", () => {
  it("puts the header, then the buy card, then the cover, then the details in document order", () => {
    render(<Sample />);
    const header = screen.getByRole("heading", { name: "Package title" });
    const card = screen.getByRole("region", { name: "Buy" });
    const cover = screen.getByTestId("cover");
    const details = screen.getByText("Details");
    expect(order([header, card, cover, details])).toBe(true);
  });

  it("keeps the cover and the buy card in one sticky column on desktop", () => {
    render(<Sample />);
    const cover = screen.getByTestId("cover");
    // The cover sits in its own wrapper, and the wrapper sits in the sticky column with the card.
    const column = cover.parentElement?.parentElement;
    expect(column).toHaveClass("lg:sticky", "lg:top-20");
    expect(column?.contains(screen.getByRole("region", { name: "Buy" }))).toBe(true);
    expect(column?.contains(screen.getByText("Details"))).toBe(false);
  });
});

describe("the package cover band (Phase 4, S4.3)", () => {
  it("is 160 px tall on a phone and 16:9 from lg up, with no fixed aspect ratio on a phone", () => {
    const { container } = render(
      <PackageCover src={null} alt="" type="MCQ" title="Demo" band />,
    );
    const frame = container.firstElementChild as HTMLElement;
    expect(frame).toHaveClass("h-40", "lg:aspect-video", "lg:h-auto");
    expect(frame).not.toHaveClass("aspect-video");
  });

  it("keeps the 16:9 frame when no band is asked for", () => {
    const { container } = render(<PackageCover src={null} alt="" type="MCQ" title="Demo" />);
    const frame = container.firstElementChild as HTMLElement;
    expect(frame).toHaveClass("aspect-video");
    expect(frame).not.toHaveClass("h-40");
  });
});
