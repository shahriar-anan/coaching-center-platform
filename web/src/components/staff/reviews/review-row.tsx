"use client";

import { useState } from "react";
import Link from "next/link";
import { Avatar } from "@/components/ui/file/avatar";
import { Button } from "@/components/ui/button";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Stars } from "@/components/ui/stars";
import { useLocale } from "@/i18n/locale-context";
import { useAuthorizedObjectUrl } from "@/lib/hooks/use-authorized-object-url";
import { useFormat } from "@/lib/format/use-format";
import type { ReviewStatus } from "@/lib/api/exam/reviews";
import type { StaffReviewRow } from "@/lib/api/exam/staff-reviews";

/** A comment longer than this many characters is cut short until "Show more" is pressed. */
const PREVIEW_CHARS = 240;

const STATUS_TONE: Record<ReviewStatus, PillTone> = {
  PENDING: "warn",
  APPROVED: "ok",
  HIDDEN: "danger",
};

/**
 * One review on the Reviews page: stars, status, the comment (long ones expand), the student and the exam or package it is
 * about, the hide reason, and the actions the person may take. Teachers get no actions (`canModerate` is false).
 */
export function ReviewRow({
  row,
  canModerate,
  busy,
  onApprove,
  onApproveAndFeature,
  onHide,
  onFeature,
}: {
  row: StaffReviewRow;
  canModerate: boolean;
  /** An action on this row is being saved. */
  busy: boolean;
  onApprove: (row: StaffReviewRow) => void;
  /** Publishes a waiting or hidden review that has a comment and puts it on the landing page in one step. */
  onApproveAndFeature: (row: StaffReviewRow) => void;
  onHide: (row: StaffReviewRow) => void;
  onFeature: (row: StaffReviewRow, featured: boolean) => void;
}) {
  const { t } = useLocale();
  const { pickTitle, formatDateTime, formatNumber } = useFormat();
  const [expanded, setExpanded] = useState(false);
  // The picture needs the sign-in token, so it is fetched as an object URL (null until it arrives).
  const photo = useAuthorizedObjectUrl(row.photoUrl);

  const comment = row.comment;
  const long = comment !== null && comment.length > PREVIEW_CHARS;
  const shown = comment !== null && long && !expanded ? `${comment.slice(0, PREVIEW_CHARS).trimEnd()}…` : comment;

  const canApprove = canModerate && (row.status === "PENDING" || row.status === "HIDDEN");
  const canApproveAndFeature = canApprove && comment !== null;
  const canHide = canModerate && (row.status === "PENDING" || row.status === "APPROVED");
  const canFeature = canModerate && row.status === "APPROVED" && comment !== null;
  const hasActions = canApprove || canHide || canFeature;

  const targetTitle = pickTitle({ titleEn: row.targetTitleEn, titleBn: row.targetTitleBn });
  const targetHref =
    row.kind && row.targetId ? `/staff/${row.kind === "exam" ? "exams" : "modules"}/${row.targetId}` : null;

  return (
    <li className="grid gap-3 rounded-card border border-border bg-card p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <Stars value={row.rating} label={t("staffReviews.row.stars", { count: formatNumber(row.rating) })} />
          <Pill tone={STATUS_TONE[row.status]}>{t(`staffReviews.status.${row.status}`)}</Pill>
          {row.featured ? <Pill tone="accent">{t("staffReviews.row.featured")}</Pill> : null}
        </div>
        <time dateTime={row.createdAt} className="text-meta text-muted">
          {formatDateTime(row.createdAt)}
        </time>
      </div>

      {comment === null ? (
        <p className="text-meta text-muted">{t("staffReviews.row.starsOnly")}</p>
      ) : (
        <div className="grid gap-1">
          <p className="whitespace-pre-line text-body text-text">{shown}</p>
          {long ? (
            <button
              type="button"
              aria-expanded={expanded}
              onClick={() => setExpanded((open) => !open)}
              className="justify-self-start rounded-control text-meta font-medium text-accent-text underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-accent"
            >
              {expanded ? t("staffReviews.row.collapse") : t("staffReviews.row.expand")}
            </button>
          ) : null}
        </div>
      )}

      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-meta text-muted">
        <Avatar src={photo} name={row.studentName} size={28} />
        <Link href={`/staff/students/${row.studentId}`} prefetch className="font-medium text-text underline-offset-2 hover:underline">
          {row.studentName || t("staffReviews.row.unnamed")}
        </Link>
        {row.kind ? (
          <span>
            {t(`staffReviews.kind.${row.kind}`)}:{" "}
            {targetHref ? (
              <Link href={targetHref} prefetch className="font-medium text-text underline-offset-2 hover:underline">
                {targetTitle || t("staffReviews.row.removedTarget")}
              </Link>
            ) : (
              <span>{targetTitle || t("staffReviews.row.removedTarget")}</span>
            )}
          </span>
        ) : null}
      </p>

      {row.status === "HIDDEN" && row.hideReason ? (
        <p className="text-meta text-danger">{t("staffReviews.row.hiddenReason", { reason: row.hideReason })}</p>
      ) : null}

      {hasActions ? (
        <div className="flex flex-wrap gap-2 border-t border-border pt-3">
          {canApproveAndFeature ? (
            <Button loading={busy} onClick={() => onApproveAndFeature(row)}>
              {t("staffReviews.actions.approveAndFeature")}
            </Button>
          ) : null}
          {canApprove ? (
            <Button variant="secondary" loading={busy} onClick={() => onApprove(row)}>
              {t("staffReviews.actions.approve")}
            </Button>
          ) : null}
          {canHide ? (
            <Button variant="secondary" disabled={busy} onClick={() => onHide(row)}>
              {t("staffReviews.actions.hide")}
            </Button>
          ) : null}
          {canFeature ? (
            <Button variant="secondary" disabled={busy} onClick={() => onFeature(row, !row.featured)}>
              {row.featured ? t("staffReviews.actions.unfeature") : t("staffReviews.actions.feature")}
            </Button>
          ) : null}
        </div>
      ) : null}
    </li>
  );
}
