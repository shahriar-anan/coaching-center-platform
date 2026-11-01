"use client";

import { useId, useState } from "react";
import { Megaphone } from "lucide-react";
import { announcementText, isLongBody, previewBody } from "@/app/(site)/notices/_components/announcement";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { useMyAnnouncements, type AnnouncementKind, type MyAnnouncement } from "@/lib/api/exam/announcements";
import { formatDate } from "@/lib/format";

/** The announcements shown before "Show all". */
const SHOWN_FIRST = 3;

/**
 * The announcements of a package or an exam, under the page header (announcements plan, section 9). Only the latest three show,
 * newest first; "Show all" reveals the rest. It renders nothing when there are none, when the page did not ask (`enabled`), or
 * when the read fails. The text is plain: a body with markup shows the tags as text.
 */
export function AnnouncementCard({ kind, id, enabled }: { kind: AnnouncementKind; id: string; enabled: boolean }) {
  const { t } = useLocale();
  const headingId = useId();
  const [showAll, setShowAll] = useState(false);
  const query = useMyAnnouncements(kind, id, enabled);
  const items = query.data ?? [];

  if (items.length === 0) return null;
  const shown = showAll ? items : items.slice(0, SHOWN_FIRST);

  return (
    <section aria-labelledby={headingId} className="grid gap-3 rounded-card border border-border bg-card p-4">
      <h2 id={headingId} className="flex items-center gap-2 text-section font-bold">
        <Megaphone aria-hidden="true" className="size-4 shrink-0 text-accent" />
        {t("announcements.title")}
      </h2>

      <ul className="grid gap-4">
        {shown.map((item) => (
          <li key={item.id}>
            <AnnouncementItem announcement={item} />
          </li>
        ))}
      </ul>

      {items.length > SHOWN_FIRST ? (
        <div className="flex justify-center">
          <Button variant="secondary" aria-expanded={showAll} onClick={() => setShowAll((value) => !value)}>
            {showAll ? t("announcements.showFewer") : t("announcements.showAll")}
          </Button>
        </div>
      ) : null}
    </section>
  );
}

/** One announcement: the date, the title, then the body. A long body shows its first lines and expands on request. */
function AnnouncementItem({ announcement }: { announcement: MyAnnouncement }) {
  const { t, locale } = useLocale();
  const [expanded, setExpanded] = useState(false);
  const { title, body } = announcementText(announcement, locale);
  const long = isLongBody(body);

  return (
    <article className="grid gap-1.5">
      <time dateTime={announcement.createdAt} className="text-meta text-muted">
        {formatDate(announcement.createdAt, locale, { year: "auto" })}
      </time>
      {title ? <h3 className="text-body font-semibold text-text">{title}</h3> : null}
      {body ? (
        <p className="whitespace-pre-line text-body text-text">{expanded || !long ? body : previewBody(body)}</p>
      ) : null}
      {long ? (
        <button
          type="button"
          aria-expanded={expanded}
          onClick={() => setExpanded((value) => !value)}
          className="justify-self-start font-semibold text-accent underline underline-offset-4"
        >
          {expanded ? t("announcements.readLess") : t("announcements.readMore")}
        </button>
      ) : null}
    </article>
  );
}
