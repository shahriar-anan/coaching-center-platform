/**
 * Rich text for exam and package descriptions and rules. A stored value is HTML when it has tags,
 * and plain text (with its line breaks) when it has none. Old rows stay plain text.
 */

const ALLOWED_TAGS = ["p", "br", "h2", "h3", "strong", "em", "u", "ul", "ol", "li", "a"] as const;
export type AllowedTag = (typeof ALLOWED_TAGS)[number];
export type Align = "left" | "center" | "right" | "justify";

export type RichNode =
  | { type: "text"; text: string }
  | { type: "element"; tag: AllowedTag; align?: Align; href?: string; children: RichNode[] };

/** Elements whose content is never shown, because it is code or embedded content. */
const DROP_WITH_CONTENT = new Set([
  "script",
  "style",
  "iframe",
  "object",
  "embed",
  "noscript",
  "template",
  "textarea",
  "title",
  "svg",
  "math",
  "select",
  "option",
]);
const ALIGNABLE = new Set<string>(["p", "h2", "h3", "li"]);
const TAG_PATTERN = /<\/?[a-zA-Z][^>]*>/;
const ALIGN_STYLE = /text-align\s*:\s*(left|center|right|justify)/i;
const ATTRIBUTE = /([^\s"'<>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;
// Comments, doctypes, tags (with quoted attributes that may hold `>`), text runs, and a stray `<`.
const TOKEN = /<!--[\s\S]*?-->|<![^>]*>|<(\/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^>"']|"[^"]*"|'[^']*')*)>|[^<]+|</g;

const NAMED_ENTITIES: Record<string, string> = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " " };

/** Turns the HTML entities in a text run into the characters they stand for. */
function decodeEntities(text: string): string {
  return text.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (whole, body: string) => {
    if (body[0] === "#") {
      const code = body[1] === "x" || body[1] === "X" ? parseInt(body.slice(2), 16) : parseInt(body.slice(1), 10);
      return Number.isFinite(code) && code > 0 && code <= 0x10ffff ? String.fromCodePoint(code) : whole;
    }
    return NAMED_ENTITIES[body.toLowerCase()] ?? whole;
  });
}

/** True when the value has at least one tag, so it is read as HTML. */
export function hasMarkup(value: string): boolean {
  return TAG_PATTERN.test(value);
}

/** A web address the viewer and the editor accept: http, https or mailto. Anything else is dropped. */
export function isSafeHref(url: string): boolean {
  return /^(https?:\/\/\S+|mailto:\S+)$/i.test(url.trim());
}

/** The address for a link typed in the editor. A bare domain such as `example.com` gets `https://`. Returns `null` when it is not an address. */
export function linkFromInput(text: string): string | null {
  const trimmed = text.trim();
  if (isSafeHref(trimmed)) {
    return trimmed;
  }
  if (/^[a-z0-9-]+(\.[a-z0-9-]+)+(\/\S*)?$/i.test(trimmed)) {
    return `https://${trimmed}`;
  }
  return null;
}

const escapeHtml = (text: string) =>
  text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

/** The editor's starting HTML for a stored value. HTML stays as it is; plain text becomes one paragraph per line. */
export function htmlFromStored(value: string): string {
  if (hasMarkup(value)) {
    return value;
  }
  return value
    .split(/\r?\n/)
    .map((line) => `<p>${escapeHtml(line)}</p>`)
    .join("");
}

type Frame = { name: string; children: RichNode[] };

/**
 * Reads stored HTML into a tree that keeps only the allowed tags. Other tags are dropped and their
 * text is kept. Scripts, styles and embedded content are dropped with what they hold. Links keep only
 * http, https and mailto addresses. Text alignment is kept from the `style` attribute. Nothing here
 * runs as HTML: the viewer turns the tree into React elements.
 */
export function sanitizeRichText(html: string): RichNode[] {
  const lower = html.toLowerCase();
  const root: Frame = { name: "root", children: [] };
  const stack: Frame[] = [root];
  const pattern = new RegExp(TOKEN.source, "g");
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(html)) !== null) {
    const [whole, closing, name, attributes = ""] = match;
    if (name === undefined) {
      if (!whole.startsWith("<!")) {
        stack[stack.length - 1].children.push({ type: "text", text: decodeEntities(whole) });
      }
      continue;
    }
    const tag = name.toLowerCase();
    if (!closing && DROP_WITH_CONTENT.has(tag)) {
      if (!/\/\s*$/.test(attributes)) {
        const end = lower.indexOf(`</${tag}`, pattern.lastIndex);
        if (end === -1) {
          pattern.lastIndex = html.length;
        } else {
          const close = html.indexOf(">", end);
          pattern.lastIndex = close === -1 ? html.length : close + 1;
        }
      }
      continue;
    }
    if (closing) {
      // Closes the nearest open element of the same name. A tag that was dropped has no frame, so its close is ignored.
      for (let i = stack.length - 1; i > 0; i--) {
        if (stack[i].name === tag) {
          stack.length = i;
          break;
        }
      }
      continue;
    }
    if (!(ALLOWED_TAGS as readonly string[]).includes(tag)) {
      continue;
    }
    const parent = stack[stack.length - 1];
    let align: Align | undefined;
    let href: string | undefined;
    for (const attribute of attributes.matchAll(ATTRIBUTE)) {
      const attributeName = attribute[1].toLowerCase();
      const value = decodeEntities(attribute[2] ?? attribute[3] ?? attribute[4] ?? "");
      if (attributeName === "style" && ALIGNABLE.has(tag)) {
        const found = ALIGN_STYLE.exec(value);
        align = found ? (found[1].toLowerCase() as Align) : align;
      }
      if (attributeName === "href" && tag === "a" && isSafeHref(value)) {
        href = value.trim();
      }
    }
    // A link without a safe address is kept as plain text.
    if (tag === "a" && !href) {
      continue;
    }
    const node: RichNode = { type: "element", tag: tag as AllowedTag, align, href, children: [] };
    parent.children.push(node);
    if (tag !== "br" && !/\/\s*$/.test(attributes)) {
      stack.push({ name: tag, children: node.children });
    }
  }
  return root.children;
}

/**
 * A one-line plain-text excerpt for cards, rows and search results: tags removed, entities decoded,
 * spaces collapsed, cut to `max` characters with an ellipsis. Plain text is only collapsed.
 */
export function plainExcerpt(value: string | null | undefined, max: number): string {
  if (!value) {
    return "";
  }
  const withSpaces = hasMarkup(value)
    ? decodeEntities(value.replace(/<br\s*\/?>|<\/(p|h2|h3|li|ul|ol)>/gi, " ").replace(/<[^>]*>/g, " "))
    : value;
  const text = withSpaces.replace(/\s+/g, " ").trim();
  return text.length > max ? `${text.slice(0, Math.max(0, max - 1)).trimEnd()}…` : text;
}
