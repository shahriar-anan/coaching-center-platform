import { createElement, type ReactNode } from "react";
import { cn } from "@/components/ui/cn";
import { hasMarkup, sanitizeRichText, type RichNode } from "@/lib/format/rich-text";

/** The look of rich text, shared by the viewer and the editor. Nested tags are styled by their parent, so no tag needs its own class. */
export const richTextBody =
  "grid gap-2 text-body leading-relaxed text-text [&_a]:text-action [&_a]:underline [&_a]:underline-offset-2 [&_h2]:text-label [&_h2]:font-semibold [&_h3]:font-semibold [&_ol]:list-decimal [&_ol]:ps-5 [&_ul]:list-disc [&_ul]:ps-5";

function renderNodes(nodes: RichNode[]): ReactNode[] {
  return nodes.map((node, index) => {
    if (node.type === "text") {
      return node.text;
    }
    const props = {
      key: index,
      style: node.align ? { textAlign: node.align } : undefined,
      href: node.href,
      rel: node.href ? "noopener nofollow" : undefined,
    };
    // A void element takes no children at all, not even an empty list.
    return node.tag === "br" ? createElement("br", props) : createElement(node.tag, props, renderNodes(node.children));
  });
}

/**
 * Shows a stored description or rules text. HTML (a value with tags) is cleaned to the allowed tags
 * and shown as elements, so no script can run. Plain text (old rows) keeps its line breaks, as before.
 */
export function RichTextView({ value, className }: { value: string | null | undefined; className?: string }) {
  if (!value || !value.trim()) {
    return null;
  }
  if (!hasMarkup(value)) {
    return <div className={cn("whitespace-pre-line text-body leading-relaxed text-text", className)}>{value}</div>;
  }
  return <div className={cn(richTextBody, className)}>{renderNodes(sanitizeRichText(value))}</div>;
}
