import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { DetailLayout, barActionOf } from "@/components/student/catalog/detail-layout";
import { Button } from "@/components/ui/button";
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

/** True when each node comes after the one before it in the document. */
function order(nodes: HTMLElement[]) {
  return nodes.every((node, index) => index === 0 || (nodes[index - 1].compareDocumentPosition(node) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0);
}

describe("the shared detail layout (Phase 4, S4.3)", () => {
  it("reads the header, the details, the cover and then the buy card in document order", () => {
    render(<Sample />);
    const header = screen.getByRole("heading", { name: "Package title" });
    const card = screen.getByRole("region", { name: "Buy" });
    const cover = screen.getByTestId("cover");
    const details = screen.getByText("Details");
    expect(order([header, details, cover, card])).toBe(true);
  });

  it("shows the cover, the header, the details, then the buy card on a phone, through the order classes", () => {
    render(<Sample />);
    const cover = screen.getByTestId("cover").parentElement;
    const header = screen.getByRole("heading", { name: "Package title" }).parentElement;
    const details = screen.getByText("Details").parentElement;
    const card = screen.getByRole("region", { name: "Buy" }).parentElement;
    expect(cover).toHaveClass("order-1");
    expect(header).toHaveClass("order-2");
    expect(details).toHaveClass("order-3");
    expect(card).toHaveClass("order-4");
  });

  it("keeps the cover and the buy card in one sticky column on desktop", () => {
    render(<Sample />);
    const cover = screen.getByTestId("cover");
    // The cover sits in its own wrapper, and the wrapper sits in the sticky column with the card.
    const column = cover.parentElement?.parentElement;
    expect(column).toHaveClass("lg:sticky", "lg:top-20", "lg:self-start");
    expect(column?.contains(screen.getByRole("region", { name: "Buy" }))).toBe(true);
    expect(column?.contains(screen.getByText("Details"))).toBe(false);
  });
});

describe("the phone bar repeats only an action that can be taken now (Phase 4, S4.3)", () => {
  it("gives the card's enabled button, and no bar for a disabled button or a line of text", () => {
    const enabled = <Button>Buy</Button>;
    expect(barActionOf(enabled)).toBe(enabled);
    expect(barActionOf(<Button disabled>Buy</Button>)).toBeNull();
    expect(barActionOf(<p>Missed</p>)).toBeNull();
    expect(barActionOf(null)).toBeNull();
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
