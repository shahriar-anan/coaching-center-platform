"use client";

import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { EditorContent, useEditor, useEditorState } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import TextAlign from "@tiptap/extension-text-align";
import {
  Bold,
  Heading2,
  Heading3,
  Italic,
  Link2,
  Link2Off,
  List,
  ListOrdered,
  RemoveFormatting,
  TextAlignCenter,
  TextAlignEnd,
  TextAlignJustify,
  TextAlignStart,
  Underline,
} from "lucide-react";
import { cn } from "@/components/ui/cn";
import { controlBase, controlBorder } from "@/components/ui/control";
import { richTextBody } from "@/components/ui/rich-text-view";
import { useLocale } from "@/i18n/locale-context";
import { htmlFromStored, isSafeHref, linkFromInput } from "@/lib/format/rich-text";

/** The R2 set only: headings, bold, italic, underline, lists, alignment and links. No images, quotes, code or strikethrough. */
const extensions = [
  StarterKit.configure({
    heading: { levels: [2, 3] },
    blockquote: false,
    code: false,
    codeBlock: false,
    horizontalRule: false,
    strike: false,
    link: {
      openOnClick: false,
      autolink: false,
      HTMLAttributes: { rel: "noopener nofollow" },
      isAllowedUri: (url) => isSafeHref(url),
    },
  }),
  TextAlign.configure({ types: ["heading", "paragraph"] }),
];

const editorClass = `${richTextBody} min-h-32 px-1.5 py-1 outline-none`;
const secondaryButton =
  "min-h-11 rounded-control border border-border-control bg-field px-4 text-body font-medium text-text transition-colors hover:border-muted disabled:cursor-not-allowed disabled:opacity-60";

/** One toolbar button. A 44 px square; `pressed` marks the formats that are on at the cursor. */
function Tool({
  label,
  icon,
  pressed,
  disabled,
  onPress,
}: {
  label: string;
  icon: ReactNode;
  pressed?: boolean;
  disabled?: boolean;
  onPress: () => void;
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      aria-pressed={pressed}
      disabled={disabled}
      onClick={onPress}
      className={cn(
        "grid min-h-11 min-w-11 place-items-center rounded-control border transition-colors disabled:cursor-not-allowed disabled:opacity-50",
        pressed ? "border-action bg-action text-on-action" : "border-transparent text-text hover:bg-field",
      )}
    >
      {icon}
    </button>
  );
}

/**
 * A rich text field: a label, a toolbar (headings, bold, italic, underline, lists, alignment, link,
 * clear formatting) and the editable text. The value is an HTML string in and out. Plain text from
 * an old row opens as one paragraph per line. Images are not offered.
 */
export function RichTextEditor({
  value,
  onChange,
  label,
  invalid,
  disabled,
  className,
}: {
  value: string;
  onChange: (html: string) => void;
  label: string;
  invalid?: boolean;
  disabled?: boolean;
  className?: string;
}) {
  const { t } = useLocale();
  const labelId = useId();
  // The last HTML this field sent or was given. A different value from outside replaces the text.
  const sent = useRef(value);
  const latestChange = useRef(onChange);
  const [linkOpen, setLinkOpen] = useState(false);
  const [linkText, setLinkText] = useState("");
  const [linkInvalid, setLinkInvalid] = useState(false);

  useEffect(() => {
    latestChange.current = onChange;
  }, [onChange]);

  const editor = useEditor({
    extensions,
    content: htmlFromStored(value),
    editable: !disabled,
    // The editor is made in the browser only, so the server render and the first client render match.
    immediatelyRender: false,
    editorProps: { attributes: { class: editorClass, "aria-labelledby": labelId } },
    onUpdate: ({ editor: current }) => {
      const html = current.getHTML();
      sent.current = html;
      latestChange.current(html);
    },
  });

  useEffect(() => {
    if (!editor || value === sent.current) {
      return;
    }
    sent.current = value;
    editor.commands.setContent(htmlFromStored(value), { emitUpdate: false });
  }, [editor, value]);

  // Tiptap's setEditable emits an update by default, which reported the stored value as a change when the field opened.
  // The false stops that emit: a change of editability is not an edit.
  useEffect(() => {
    editor?.setEditable(!disabled, false);
  }, [editor, disabled]);

  // Which formats are on at the cursor, so the toolbar shows them pressed. Re-read on every change.
  const on = useEditorState({
    editor,
    selector: ({ editor: current }) => ({
      bold: current?.isActive("bold") ?? false,
      italic: current?.isActive("italic") ?? false,
      underline: current?.isActive("underline") ?? false,
      heading2: current?.isActive("heading", { level: 2 }) ?? false,
      heading3: current?.isActive("heading", { level: 3 }) ?? false,
      bulletList: current?.isActive("bulletList") ?? false,
      orderedList: current?.isActive("orderedList") ?? false,
      alignLeft: current?.isActive({ textAlign: "left" }) ?? false,
      alignCenter: current?.isActive({ textAlign: "center" }) ?? false,
      alignRight: current?.isActive({ textAlign: "right" }) ?? false,
      alignJustify: current?.isActive({ textAlign: "justify" }) ?? false,
      link: current?.isActive("link") ?? false,
    }),
  });

  function openLink() {
    setLinkText((editor?.getAttributes("link").href as string | undefined) ?? "");
    setLinkInvalid(false);
    setLinkOpen(true);
  }

  function applyLink() {
    const href = linkFromInput(linkText);
    if (!editor || !href) {
      setLinkInvalid(true);
      return;
    }
    editor.chain().focus().extendMarkRange("link").setLink({ href }).run();
    setLinkOpen(false);
  }

  function removeLink() {
    editor?.chain().focus().extendMarkRange("link").unsetLink().run();
    setLinkOpen(false);
  }

  function clearFormatting() {
    if (!editor) {
      return;
    }
    editor.chain().focus().unsetAllMarks().clearNodes().run();
    // Clearing does not take a paragraph out of a list, so the list is switched off as well.
    if (editor.isActive("bulletList")) {
      editor.chain().focus().toggleBulletList().run();
    }
    if (editor.isActive("orderedList")) {
      editor.chain().focus().toggleOrderedList().run();
    }
  }

  const off = !editor;

  return (
    <div className={cn("grid gap-2", className)}>
      <span id={labelId} className="text-meta text-muted">
        {label}
      </span>
      <div
        className={cn("grid gap-2 rounded-control border bg-field p-2 focus-within:border-accent", controlBorder(invalid))}
      >
        <div role="toolbar" aria-label={t("common.richText.toolbar")} className="flex flex-wrap items-center gap-1">
          <Tool
            label={t("common.richText.heading2")}
            icon={<Heading2 aria-hidden="true" className="size-5" />}
            pressed={on?.heading2}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().toggleHeading({ level: 2 }).run()}
          />
          <Tool
            label={t("common.richText.heading3")}
            icon={<Heading3 aria-hidden="true" className="size-5" />}
            pressed={on?.heading3}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().toggleHeading({ level: 3 }).run()}
          />
          <span aria-hidden="true" className="mx-1 h-6 w-px bg-border" />
          <Tool
            label={t("common.richText.bold")}
            icon={<Bold aria-hidden="true" className="size-5" />}
            pressed={on?.bold}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().toggleBold().run()}
          />
          <Tool
            label={t("common.richText.italic")}
            icon={<Italic aria-hidden="true" className="size-5" />}
            pressed={on?.italic}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().toggleItalic().run()}
          />
          <Tool
            label={t("common.richText.underline")}
            icon={<Underline aria-hidden="true" className="size-5" />}
            pressed={on?.underline}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().toggleUnderline().run()}
          />
          <span aria-hidden="true" className="mx-1 h-6 w-px bg-border" />
          <Tool
            label={t("common.richText.bulletList")}
            icon={<List aria-hidden="true" className="size-5" />}
            pressed={on?.bulletList}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().toggleBulletList().run()}
          />
          <Tool
            label={t("common.richText.orderedList")}
            icon={<ListOrdered aria-hidden="true" className="size-5" />}
            pressed={on?.orderedList}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().toggleOrderedList().run()}
          />
          <span aria-hidden="true" className="mx-1 h-6 w-px bg-border" />
          <Tool
            label={t("common.richText.alignLeft")}
            icon={<TextAlignStart aria-hidden="true" className="size-5" />}
            pressed={on?.alignLeft}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().setTextAlign("left").run()}
          />
          <Tool
            label={t("common.richText.alignCenter")}
            icon={<TextAlignCenter aria-hidden="true" className="size-5" />}
            pressed={on?.alignCenter}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().setTextAlign("center").run()}
          />
          <Tool
            label={t("common.richText.alignRight")}
            icon={<TextAlignEnd aria-hidden="true" className="size-5" />}
            pressed={on?.alignRight}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().setTextAlign("right").run()}
          />
          <Tool
            label={t("common.richText.alignJustify")}
            icon={<TextAlignJustify aria-hidden="true" className="size-5" />}
            pressed={on?.alignJustify}
            disabled={off || disabled}
            onPress={() => editor?.chain().focus().setTextAlign("justify").run()}
          />
          <span aria-hidden="true" className="mx-1 h-6 w-px bg-border" />
          <Tool
            label={t("common.richText.link")}
            icon={<Link2 aria-hidden="true" className="size-5" />}
            pressed={on?.link}
            disabled={off || disabled}
            onPress={openLink}
          />
          <Tool
            label={t("common.richText.clearFormatting")}
            icon={<RemoveFormatting aria-hidden="true" className="size-5" />}
            disabled={off || disabled}
            onPress={clearFormatting}
          />
        </div>
        {linkOpen ? (
          <div role="group" aria-label={t("common.richText.link")} className="grid gap-2 sm:flex sm:items-end">
            <label className="grid flex-1 gap-1 text-meta text-muted">
              <span>{t("common.richText.linkUrl")}</span>
              <input
                type="text"
                inputMode="url"
                autoComplete="off"
                value={linkText}
                aria-invalid={linkInvalid || undefined}
                onChange={(e) => {
                  setLinkText(e.target.value);
                  setLinkInvalid(false);
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    applyLink();
                  }
                  if (e.key === "Escape") {
                    setLinkOpen(false);
                  }
                }}
                className={cn(controlBase, controlBorder(linkInvalid))}
              />
            </label>
            <div className="flex flex-wrap gap-2">
              <button type="button" onClick={applyLink} className="min-h-11 rounded-control bg-action px-4 text-body font-medium text-on-action">
                {t("common.richText.linkApply")}
              </button>
              {on?.link ? (
                <button type="button" onClick={removeLink} className={cn(secondaryButton, "inline-flex items-center gap-2")}>
                  <Link2Off aria-hidden="true" className="size-4" />
                  {t("common.richText.linkRemove")}
                </button>
              ) : null}
              <button type="button" onClick={() => setLinkOpen(false)} className={secondaryButton}>
                {t("common.richText.linkCancel")}
              </button>
            </div>
            {linkInvalid ? (
              <p role="alert" className="text-meta text-danger">
                {t("common.richText.linkInvalid")}
              </p>
            ) : null}
          </div>
        ) : null}
        <EditorContent editor={editor} />
      </div>
    </div>
  );
}
