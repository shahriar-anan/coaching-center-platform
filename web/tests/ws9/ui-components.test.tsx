import { describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "../helpers";
import { Stepper } from "@/components/ui/stepper";
import { SummaryStrip } from "@/components/ui/summary-strip";
import { SplitBrowser, type TreeNode } from "@/components/ui/split-browser";
import { RowMenu } from "@/components/ui/row-menu";
import { ShortcutsPopover } from "@/components/ui/shortcuts-popover";
import { SectionNav } from "@/components/ui/section-nav";
import { StickyActionBar } from "@/components/ui/sticky-action-bar";
import { PrintArea } from "@/components/ui/print-area";

describe("Stepper", () => {
  it("renders each step with its state and marks an info step as the current one", () => {
    const { container } = renderWithProviders(
      <Stepper
        steps={[
          { key: "a", label: "বিবরণ", state: "done" },
          { key: "b", label: "প্রশ্ন", state: "info", note: "৩টি বাকি" },
          { key: "c", label: "প্রকাশ", state: "blocked", href: "/staff/exams/1/publish" },
        ]}
      />,
    );
    const items = container.querySelectorAll("li");
    expect(items).toHaveLength(3);
    expect(items[0]).toHaveAttribute("data-state", "done");
    expect(items[1]).toHaveAttribute("aria-current", "step");
    expect(screen.getByText("৩টি বাকি")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "প্রকাশ" })).toHaveAttribute("href", "/staff/exams/1/publish");
  });

  it("uses a vertical layout by default", () => {
    const { container } = renderWithProviders(<Stepper steps={[{ key: "a", label: "এক", state: "todo" }]} />);
    expect(container.querySelector("ol")).toHaveAttribute("data-orientation", "vertical");
  });
});

describe("SummaryStrip", () => {
  it("shows each value with its label", () => {
    renderWithProviders(
      <SummaryStrip
        items={[
          { label: "খাতা জমা", value: "12", tone: "ok" },
          { label: "মূল্যায়ন বাকি", value: "3", tone: "warn", href: "/staff/submissions" },
        ]}
      />,
    );
    expect(screen.getByText("খাতা জমা")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /মূল্যায়ন বাকি/ })).toHaveAttribute("href", "/staff/submissions");
  });

  it("hides the values and marks the strip busy while loading", () => {
    const { container } = renderWithProviders(
      <SummaryStrip items={[{ label: "শিক্ষার্থী", value: "40" }]} loading />,
    );
    expect(screen.queryByText("40")).not.toBeInTheDocument();
    expect(container.querySelector("dl")).toHaveAttribute("aria-busy", "true");
  });
});

describe("SplitBrowser", () => {
  const tree: TreeNode[] = [
    {
      id: "science",
      label: "বিজ্ঞান",
      count: 5,
      children: [{ id: "physics", label: "পদার্থ", count: 2 }],
    },
    { id: "arts", label: "মানবিক", count: 3 },
  ];

  it("calls onSelect with the id of the node clicked, including nested nodes", async () => {
    const onSelect = vi.fn();
    renderWithProviders(
      <SplitBrowser tree={tree} selectedId="arts" onSelect={onSelect}>
        <p>ডান পাশ</p>
      </SplitBrowser>,
    );
    await userEvent.click(screen.getByRole("button", { name: /পদার্থ/ }));
    expect(onSelect).toHaveBeenCalledWith("physics");
    expect(screen.getByRole("button", { name: /মানবিক/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("ডান পাশ")).toBeInTheDocument();
  });

  it("hides a group's children when its chevron is closed, and shows them again", async () => {
    renderWithProviders(
      <SplitBrowser tree={tree} selectedId={null} onSelect={vi.fn()}>
        <p>ডান পাশ</p>
      </SplitBrowser>,
    );
    // A top-level group starts open.
    expect(screen.getByRole("button", { name: /পদার্থ/ })).toBeInTheDocument();
    const hide = screen.getByRole("button", { name: "Hide বিজ্ঞান" });
    expect(hide).toHaveAttribute("aria-expanded", "true");
    await userEvent.click(hide);
    expect(screen.queryByRole("button", { name: /পদার্থ/ })).toBeNull();
    const show = screen.getByRole("button", { name: "Show বিজ্ঞান" });
    expect(show).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(show);
    expect(screen.getByRole("button", { name: /পদার্থ/ })).toBeInTheDocument();
  });

  it("opens the groups above the selected node by itself", () => {
    renderWithProviders(
      <SplitBrowser tree={tree} selectedId="physics" onSelect={vi.fn()}>
        <p>ডান পাশ</p>
      </SplitBrowser>,
    );
    expect(screen.getByRole("button", { name: /পদার্থ/ })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Hide বিজ্ঞান" })).toHaveAttribute("aria-expanded", "true");
  });

  it("uses the labels it is given for the chevrons and shows the heading above the tree", () => {
    renderWithProviders(
      <SplitBrowser
        tree={tree}
        selectedId={null}
        onSelect={vi.fn()}
        heading="মূল্যায়ন বাকি"
        toggleLabel={(label, expanded) => (expanded ? `${label} লুকান` : `${label} দেখান`)}
      >
        <p>ডান পাশ</p>
      </SplitBrowser>,
    );
    expect(screen.getByText("মূল্যায়ন বাকি")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "বিজ্ঞান লুকান" })).toBeInTheDocument();
  });
});

describe("RowMenu", () => {
  it("opens from a labelled button and runs the chosen action", async () => {
    const onClick = vi.fn();
    renderWithProviders(
      <RowMenu
        label="পরীক্ষার কাজ"
        items={[
          { label: "সম্পাদনা", href: "/staff/exams/1" },
          { label: "মুছুন", onClick, tone: "danger" },
        ]}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: "পরীক্ষার কাজ" }));
    await userEvent.click(await screen.findByRole("menuitem", { name: "মুছুন" }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});

describe("ShortcutsPopover", () => {
  it("lists the shortcuts with their keys once opened", async () => {
    renderWithProviders(
      <ShortcutsPopover shortcuts={[{ keys: ["/"], label: "অনুসন্ধান" }]} />,
    );
    await userEvent.click(screen.getByRole("button", { name: "কী-বোর্ড শর্টকাট" }));
    expect(await screen.findByText("অনুসন্ধান")).toBeInTheDocument();
    expect(screen.getByText("/")).toBeInTheDocument();
  });
});

describe("SectionNav", () => {
  it("links each section and marks its state", () => {
    renderWithProviders(
      <SectionNav
        sections={[
          { id: "basic", label: "মূল তথ্য", state: "done" },
          { id: "price", label: "মূল্য", state: "error" },
        ]}
      />,
    );
    const nav = screen.getByRole("navigation", { name: "সূচি" });
    expect(within(nav).getByRole("link", { name: "মূল্য" })).toHaveAttribute("href", "#price");
    expect(nav.querySelector("[data-state='error']")).not.toBeNull();
  });
});

describe("StickyActionBar", () => {
  it("renders its actions", () => {
    renderWithProviders(
      <StickyActionBar>
        <button type="button">সেভ করুন</button>
      </StickyActionBar>,
    );
    expect(screen.getByRole("button", { name: "সেভ করুন" })).toBeInTheDocument();
  });
});

describe("PrintArea", () => {
  it("marks the printable part", () => {
    const { container } = renderWithProviders(
      <PrintArea>
        <p>রসিদ</p>
      </PrintArea>,
    );
    expect(container.querySelector("[data-print-area]")).toHaveTextContent("রসিদ");
  });
});
