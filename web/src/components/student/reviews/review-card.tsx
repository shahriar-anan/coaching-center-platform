"use client";

import { useId, useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Stars, StarInput } from "@/components/ui/stars";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import {
  REVIEW_MAX_LENGTH,
  useDeleteReview,
  useMyReview,
  useSaveReview,
  type MyReview,
  type ReviewInput,
  type ReviewKind,
} from "@/lib/api/exam/reviews";
import { formatNumber } from "@/lib/format";

/**
 * The student's own rating of an exam or a package (reviews plan, section 7). It shows only to a signed-in student who may review
 * the target, or who already has. A tap on a star saves the rating at once; a comment is added in the edit form and waits for
 * review. Renders nothing while loading or when the read fails.
 */
export function ReviewCard({ kind, id }: { kind: ReviewKind; id: string }) {
  const { session } = useSession();
  const isStudent = session.status === "authenticated" && session.user.role === "STUDENT";
  const state = useMyReview(kind, id, isStudent);

  if (!isStudent || !state.data) return null;
  if (!state.data.canReview && !state.data.reviewed) return null;
  return <ReviewPanel key={`${kind}:${id}`} kind={kind} id={id} review={state.data.review ?? null} />;
}

type Failure = { error: unknown; retry: () => void };

function ReviewPanel({ kind, id, review }: { kind: ReviewKind; id: string; review: MyReview | null }) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const headingId = useId();
  const save = useSaveReview(kind, id);
  const remove = useDeleteReview(kind, id);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ rating: review?.rating ?? 0, comment: review?.comment ?? "" });
  const [commented, setCommented] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [failure, setFailure] = useState<Failure | null>(null);

  const title = kind === "exam" ? t("reviews.card.titleExam") : t("reviews.card.titlePackage");
  const starName = (star: number) => (star === 1 ? t("reviews.stars.one") : t("reviews.stars.many", { n: star }));
  const commentLength = Array.from(draft.comment).length;

  /** Sends the review. A failure keeps the form as it is and offers to send the same review again. */
  function submit(input: ReviewInput, done?: () => void) {
    setFailure(null);
    save.mutate(input, {
      onSuccess: () => done?.(),
      onError: (error) => setFailure({ error, retry: () => submit(input, done) }),
    });
  }

  function startEditing() {
    setFailure(null);
    setDraft({ rating: review?.rating ?? 0, comment: review?.comment ?? "" });
    setEditing(true);
  }

  function saveEdit() {
    const comment = draft.comment.trim();
    const changed = comment !== (review?.comment ?? "");
    submit({ rating: draft.rating, comment: comment || null }, () => {
      setEditing(false);
      if (comment && changed) setCommented(true);
    });
  }

  const section = (children: ReactNode) => (
    <section aria-labelledby={headingId} className="grid gap-3 rounded-card border border-border bg-card p-5">
      <h2 id={headingId} className="text-section font-bold">
        {title}
      </h2>
      {children}
    </section>
  );

  // Not rated yet: five stars, and a tap saves the rating at once.
  if (!review) {
    return section(
      <>
        <StarInput
          label={t("reviews.card.ratingLabel")}
          value={null}
          labelFor={starName}
          disabled={save.isPending}
          onChange={(rating) => submit({ rating })}
        />
        {save.isPending ? <p role="status" className="text-meta text-muted">{t("reviews.card.saving")}</p> : null}
        {failure ? <ErrorState error={failure.error} onRetry={failure.retry} /> : null}
      </>,
    );
  }

  // Rated: the form to change the stars and the comment, or the saved review with its status.
  if (editing) {
    return section(
      <>
        <StarInput
          label={t("reviews.card.ratingLabel")}
          value={draft.rating}
          labelFor={starName}
          disabled={save.isPending}
          onChange={(rating) => setDraft((current) => ({ ...current, rating }))}
        />
        <Field label={t("reviews.card.commentLabel")}>
          <Textarea
            value={draft.comment}
            maxLength={REVIEW_MAX_LENGTH}
            rows={4}
            disabled={save.isPending}
            placeholder={t("reviews.card.commentPlaceholder")}
            onChange={(event) => setDraft((current) => ({ ...current, comment: event.target.value }))}
          />
        </Field>
        <p aria-live="polite" className="text-end text-meta tabular-nums text-muted">
          {t("reviews.card.commentCount", { count: commentLength, max: REVIEW_MAX_LENGTH })}
        </p>
        {failure ? <ErrorState error={failure.error} onRetry={failure.retry} /> : null}
        <div className="flex flex-wrap gap-2">
          <Button loading={save.isPending} disabled={draft.rating === 0} onClick={saveEdit}>
            {t("common.actions.save")}
          </Button>
          <Button variant="ghost" disabled={save.isPending} onClick={() => setEditing(false)}>
            {t("common.actions.cancel")}
          </Button>
        </div>
      </>,
    );
  }

  const tone: PillTone = review.status === "APPROVED" ? "ok" : review.status === "PENDING" ? "warn" : "danger";
  const statusText =
    review.status === "APPROVED"
      ? t("reviews.card.statusApproved")
      : review.status === "PENDING"
        ? t("reviews.card.statusPending")
        : review.hideReason
          ? t("reviews.card.statusHidden", { reason: review.hideReason })
          : t("reviews.card.statusHiddenNoReason");

  return (
    <>
      {section(
        <>
          <div className="grid gap-2">
            <Stars
              value={review.rating}
              size="size-6"
              label={t("reviews.stars.outOf", { n: formatNumber(review.rating, locale) })}
            />
            <div>
              <Pill tone={tone}>{statusText}</Pill>
            </div>
            {review.comment ? <p className="whitespace-pre-line text-body text-text">{review.comment}</p> : null}
            {commented ? <p role="status" className="text-body text-muted">{t("reviews.card.thanks")}</p> : null}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={startEditing}>
              {review.comment ? t("reviews.card.edit") : t("reviews.card.addComment")}
            </Button>
            <Button variant="ghost" onClick={() => setConfirmDelete(true)}>
              {t("reviews.card.delete")}
            </Button>
          </div>
        </>,
      )}
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={t("reviews.card.deleteTitle")}
        body={t("reviews.card.deleteBody")}
        confirmLabel={t("reviews.card.delete")}
        tone="danger"
        onConfirm={async () => {
          await remove.mutateAsync();
          toast({ title: t("reviews.card.deleted"), tone: "ok" });
        }}
      />
    </>
  );
}
