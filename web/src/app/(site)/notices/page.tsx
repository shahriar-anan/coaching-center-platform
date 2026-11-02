"use client";

import { Bell, CalendarClock, ChevronRight, Megaphone, Receipt, Trophy, type LucideIcon } from "lucide-react";
import { Suspense } from "react";
import Link from "next/link";
import { useState } from "react";
import { RequireStudent } from "@/lib/auth/guards";
import { useLocale } from "@/i18n/locale-context";
import { useToast } from "@/components/ui/toast";
import {
  useMarkAllNoticesRead,
  useMarkNoticeRead,
  useNotices,
  useUnreadCount,
  type Notice,
  type NoticeGroup,
} from "@/lib/api/exam/notices";
import { usePublicModulesById } from "@/lib/api/exam/catalog";
import { useListParams } from "@/lib/url/list-params";
import { formatDateTime, formatNumber, pickTitle } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { cn } from "@/components/ui/cn";
import { examPath, modulePath } from "@/components/student/catalog/catalog-helpers";
import { DoorActions } from "@/components/student/catalog/door-actions";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { noticeHref, noticeKindLabelKey } from "./_components/notice-kind";
import { announcementTarget, announcementText, packageDoor } from "./_components/announcement";
import {
  NOTICE_TABS,
  defaultNoticeTab,
  parseNoticeTab,
  groupFilter,
  paymentHref,
  rowGroup,
  type NoticeTab,
} from "./_components/notice-tabs";
import { isMissing } from "@/lib/api/missing";

export default function NoticesPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStudent>
        <NoticesView />
      </RequireStudent>
    </Suspense>
  );
}

/** Reminders and starts show the exam's start time in the meta line. */
const STARTS_KINDS = new Set(["EXAM_REMINDER", "EXAM_STARTING_SOON", "EXAM_OPEN"]);

/** A written result the Master Admin released again after a correction reads "updated", not "published". */
function kindWord(kind: string, t: (key: string) => string, updated = false): string {
  return t(kind === "WRITTEN_RESULT" && updated ? "notices.kind.WRITTEN_RESULT_UPDATED" : noticeKindLabelKey(kind));
}

/** One frame for every notice: an icon tile, the text, the date at the top right, and the actions under the text. */
const CARD = "relative flex gap-3 rounded-card border border-border bg-card p-4 transition-colors";

/** The quiet action under a card's text: accent text with a small arrow, 44 px tall to tap. */
const ACTION =
  "inline-flex min-h-11 items-center gap-1 rounded-control text-label font-semibold text-accent underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";

/** A small rounded chip: what an announcement is for, a score, a pass or fail. */
const CHIP = "inline-flex w-fit items-center rounded-full border border-border bg-bg px-2.5 py-0.5 text-pill font-medium text-muted";

type Tone = "accent" | "ok" | "warn" | "muted";

const TILE: Record<Tone, string> = {
  accent: "bg-accent/15 text-text-accent",
  ok: "bg-ok/15 text-ok-text",
  warn: "bg-warn/15 text-warn",
  muted: "bg-bg text-muted",
};

/** The icon at the start of a card, tinted by what kind of notice it is. */
function IconTile({ icon: Icon, tone }: { icon: LucideIcon; tone: Tone }) {
  return (
    <span aria-hidden="true" className={cn("flex size-10 shrink-0 items-center justify-center rounded-control", TILE[tone])}>
      <Icon className="size-5" />
    </span>
  );
}

/** The icon and tint for an exam notice: results in green, a changed time in amber, access in the accent. */
function kindIcon(kind: string): { icon: LucideIcon; tone: Tone } {
  if (kind === "MCQ_SCORE" || kind === "WRITTEN_RESULT") return { icon: Trophy, tone: "ok" };
  if (kind === "EXAM_TIME_CHANGED") return { icon: CalendarClock, tone: "warn" };
  if (kind === "PURCHASE_GRANTED" || kind === "ACCESS_GRANTED") return { icon: Receipt, tone: "accent" };
  if (STARTS_KINDS.has(kind) || kind === "EXAM_PUBLISHED" || kind === "RETAKE_AVAILABLE") return { icon: CalendarClock, tone: "accent" };
  return { icon: Bell, tone: "muted" };
}

/** The title, with an unread dot before it and the time at its right end. On a narrow screen the time drops under it. */
function TitleLine({
  title,
  srOnly,
  unread,
  time,
}: {
  title: string;
  srOnly?: string;
  unread: boolean;
  time: { iso: string; text: string };
}) {
  const { t } = useLocale();
  return (
    <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-0.5">
      <h2 className="flex min-w-0 items-center gap-2 text-body font-semibold text-text">
        {unread ? <span aria-hidden="true" className="size-2 shrink-0 rounded-full bg-accent" /> : null}
        {srOnly ? <span className="sr-only">{srOnly}: </span> : null}
        <span className="min-w-0">{title}</span>
        {unread ? <span className="sr-only">({t("notices.unreadDot")})</span> : null}
      </h2>
      <time dateTime={time.iso} className="shrink-0 text-meta text-muted">
        {time.text}
      </time>
    </div>
  );
}

/**
 * An announcement: the card shows the title, what it is for, and the first two lines. The whole card opens a dialog with the
 * full text and a link to its package or exam. Opening it marks it read. The student's language first, the other one when it
 * is blank.
 */
function AnnouncementRow({ notice, onOpen }: { notice: Notice; onOpen: (notice: Notice) => void }) {
  const { t, locale } = useLocale();
  const [open, setOpen] = useState(false);
  const unread = isMissing(notice.readAt);
  const { title, body } = announcementText(notice.announcement, locale);
  // An exam's title comes with the notice; a package's title and door come from its own read (cached per package).
  const target = announcementTarget(notice.announcement);
  const [packageRead] = usePublicModulesById(target?.kind === "package" ? [target.id] : []);
  const pkg = packageRead?.data;
  const targetTitle =
    target?.kind === "exam" ? (notice.exam ? pickTitle(notice.exam, locale) : "") : pkg ? pickTitle(pkg, locale) : "";
  const href = target?.kind === "exam" ? examPath(target.id) : target ? modulePath(packageDoor(pkg), target.id) : null;
  const linkLabel = target?.kind === "exam" ? t("announcements.openExam") : t("announcements.openPackage");
  const heading = title || t("notices.kind.ANNOUNCEMENT");
  const when = formatDateTime(notice.createdAt, locale);
  const scope = targetTitle || (notice.announcement?.audience === "ALL" ? t("notices.forEveryone") : t("notices.forYou"));

  function show() {
    if (unread) onOpen(notice);
    setOpen(true);
  }

  return (
    <>
      <article className={cn(CARD, "hover:border-accent")}>
        <IconTile icon={Megaphone} tone="accent" />
        <div className="grid min-w-0 flex-1 gap-1.5">
          <TitleLine title={heading} srOnly={t("notices.kind.ANNOUNCEMENT")} unread={unread} time={{ iso: notice.createdAt, text: when }} />
          <span className={CHIP}>{scope}</span>
          {body ? <p className="line-clamp-2 whitespace-pre-line text-body text-muted">{body}</p> : null}
          {/* The button covers the card (a stretched hit area), so a tap anywhere opens the full announcement. */}
          <button type="button" onClick={show} className={cn(ACTION, "w-fit after:absolute after:inset-0 after:content-['']")}>
            {t("notices.readAnnouncement")}
            <ChevronRight aria-hidden="true" className="size-4 rtl:rotate-180" />
          </button>
        </div>
      </article>
      <Dialog
        open={open}
        onOpenChange={setOpen}
        title={heading}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              {t("common.actions.close")}
            </Button>
            {href ? <Button href={href}>{linkLabel}</Button> : null}
          </>
        }
      >
        <div className="grid gap-4">
          <div className="flex flex-wrap items-center gap-2 text-meta text-muted">
            <span className={CHIP}>{scope}</span>
            <time dateTime={notice.createdAt}>{when}</time>
          </div>
          {body ? <p className="whitespace-pre-line text-body leading-relaxed text-text">{body}</p> : null}
        </div>
      </Dialog>
    </>
  );
}

/**
 * One exam notice (Phase 4, A8): an icon for its kind, the exam title, the kind and start time, the score and outcome as chips
 * for a result, and the actions. Opening any link marks the notice read.
 */
function NoticeRow({ notice, tab, onOpen }: { notice: Notice; tab: NoticeGroup; onOpen: (notice: Notice) => void }) {
  const { t, locale } = useLocale();
  if (notice.kind === "ANNOUNCEMENT") {
    return <AnnouncementRow notice={notice} onOpen={onOpen} />;
  }
  const unread = isMissing(notice.readAt);
  const title = notice.exam ? pickTitle(notice.exam, locale) : "";
  const kindLabel = kindWord(notice.kind, t, notice.updated === true);
  const href = noticeHref(notice);
  const startsAt = notice.exam?.liveAt && STARTS_KINDS.has(notice.kind) ? notice.exam.liveAt : null;
  const hasScore = !isMissing(notice.score) && !isMissing(notice.maximum);
  const hasPassed = !isMissing(notice.passed);
  const isPurchase = notice.kind === "PURCHASE_GRANTED";
  const actionLabel = tab === "RESULTS" ? t("notices.viewResult") : t("notices.openExamAction");
  const { icon, tone } = kindIcon(notice.kind);

  return (
    <article className={CARD}>
      <IconTile icon={icon} tone={tone} />
      <div className="grid min-w-0 flex-1 gap-1.5">
        <TitleLine
          title={title || kindLabel}
          unread={unread}
          time={{ iso: notice.createdAt, text: formatDateTime(notice.createdAt, locale) }}
        />
        <p className="text-meta text-muted">
          {kindLabel}
          {startsAt ? ` · ${t("notices.startsAt", { time: formatDateTime(startsAt, locale) })}` : ""}
        </p>
        {hasScore || hasPassed ? (
          <div className="flex flex-wrap items-center gap-2">
            {hasScore ? (
              <span className={cn(CHIP, "tabular-nums text-text")}>
                {t("notices.score", { score: formatNumber(notice.score ?? 0, locale), max: formatNumber(notice.maximum ?? 0, locale) })}
              </span>
            ) : null}
            {hasPassed ? (
              <span className={cn(CHIP, notice.passed ? "border-ok/40 bg-ok/10 text-ok-text" : "border-danger/40 bg-danger/10 text-danger")}>
                {notice.passed ? t("notices.passed") : t("notices.failed")}
              </span>
            ) : null}
          </div>
        ) : null}
        <div className="flex flex-wrap items-center gap-x-5">
          {href ? (
            <Link href={href} prefetch onClick={() => onOpen(notice)} className={ACTION} aria-label={`${title || kindLabel}. ${actionLabel}`}>
              {actionLabel}
              <ChevronRight aria-hidden="true" className="size-4 rtl:rotate-180" />
            </Link>
          ) : isPurchase ? (
            <Link href="/my-modules" prefetch onClick={() => onOpen(notice)} className={ACTION}>
              {t("notices.myExams")}
              <ChevronRight aria-hidden="true" className="size-4 rtl:rotate-180" />
            </Link>
          ) : unread ? (
            <button type="button" onClick={() => onOpen(notice)} className={ACTION}>
              {t("notices.markRead")}
            </button>
          ) : null}
          {isPurchase ? (
            <Link href={paymentHref(notice)} prefetch className={ACTION}>
              {t("notices.viewPayment")}
            </Link>
          ) : null}
        </div>
      </div>
    </article>
  );
}

/** The notices of one tab, one page at a time. The tab and the page live in the URL (`?tab=`, `?page=`). */
function NoticesView() {
  const { t } = useLocale();
  const [params, setParams] = useListParams({ page: 0, tab: "" });
  const counts = useUnreadCount();
  const byGroup = counts.data?.byGroup;
  const tab: NoticeTab = parseNoticeTab(params.tab) ?? defaultNoticeTab();
  const query = useNotices(params.page, groupFilter(tab));
  const markOne = useMarkNoticeRead();
  const markAll = useMarkAllNoticesRead();
  const { toast } = useToast();
  const rows = query.data?.content ?? [];
  const anyUnread = rows.some((row) => isMissing(row.readAt));

  // The row's link opens the exam or result page; opening it also marks the notice read.
  function open(notice: Notice) {
    if (isMissing(notice.readAt)) {
      markOne.mutate(notice.id, { onError: () => toast({ title: t("common.states.errorBody"), tone: "danger" }) });
    }
  }

  // Choosing a tab writes it to the URL, so the page does not jump to another tab when the counts change.
  function selectTab(next: NoticeTab) {
    setParams({ tab: next });
  }

  function markTab() {
    if (!params.tab) setParams({ tab });
    markAll.mutate(groupFilter(tab), {
      onSuccess: () => toast({ title: t("notices.markedAll"), tone: "ok" }),
      onError: () => toast({ title: t("common.states.errorBody"), tone: "danger" }),
    });
  }

  return (
    <>
      <PageHeader
        title={t("notices.title")}
        actions={
          anyUnread ? (
            <Button variant="secondary" loading={markAll.isPending} onClick={markTab} className="min-h-10">
              {t("notices.markTab")}
            </Button>
          ) : null
        }
      />

      <Tabs
        ariaLabel={t("notices.tabs.label")}
        tabs={NOTICE_TABS.map((group) => ({
          value: group,
          label: t(`notices.tabs.${group}`),
          badge: group === "ALL" ? (counts.data?.count ?? 0) : (byGroup?.[group] ?? 0),
        }))}
        value={tab}
        onChange={(value) => selectTab(value as NoticeTab)}
      />

      <div className="mt-4">
        {query.isPending ? (
          <Skeleton variant="card" count={3} />
        ) : query.isError && rows.length === 0 ? (
          <ErrorState error={query.error} onRetry={() => void query.refetch()} />
        ) : rows.length === 0 ? (
          params.page === 0 ? (
            <EmptyState
              title={t(`notices.emptyTab.${tab}`)}
              body={t("notices.emptyBody")}
              action={<DoorActions />}
            />
          ) : (
            <EmptyState title={t(`notices.emptyTab.${tab}`)} />
          )
        ) : (
          <div className="grid gap-4">
            {query.isError ? <ErrorState error={query.error} onRetry={() => void query.refetch()} /> : null}
            <ul className="grid gap-2.5">
              {rows.map((notice) => (
                <li key={notice.id}>
                  <NoticeRow notice={notice} tab={rowGroup(notice, tab)} onOpen={open} />
                </li>
              ))}
            </ul>
            <Pagination
              page={params.page}
              totalPages={query.data?.totalPages ?? 0}
              onPage={(page) => setParams({ page })}
            />
          </div>
        )}
      </div>
    </>
  );
}
