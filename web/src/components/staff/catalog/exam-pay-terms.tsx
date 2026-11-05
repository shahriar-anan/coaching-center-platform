"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { usePayTerms, useSavePayTerms, type PayTerms, type PayTermsChange } from "@/lib/api/exam/staff-pay-terms";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

const DASH = "—";

/**
 * The rate and the change history of a written exam, shown inside the exam's instructor section (P18). The Master Admin
 * edits the rate, which takes effect at once and is not staged. Nobody else sees the block (plan decision O1).
 */
export function ExamPayTerms({ examId }: { examId: string }) {
  const role = useRole();
  // Only the Master Admin sees or sets pay terms (plan decision O1); the API refuses anyone else.
  const terms = usePayTerms(examId, role.canAssignInstructor);
  const { t } = useLocale();

  if (!role.canAssignInstructor) {
    return null;
  }
  if (terms.isError) {
    return <p className="text-meta text-danger">{t("staffCatalog.examMeta.payTermsUnavailable")}</p>;
  }
  if (!terms.data) {
    return <Skeleton variant="text" />;
  }
  return (
    <div className="grid gap-5 border-t border-border pt-5">
      <PayRate examId={examId} terms={terms.data} canEdit={role.canAssignInstructor} />
      <ChangeHistory changes={terms.data.changes} />
    </div>
  );
}

function PayRate({ examId, terms, canEdit }: { examId: string; terms: PayTerms; canEdit: boolean }) {
  const { t } = useLocale();
  const { formatMoney } = useFormat();
  const { toast } = useToast();
  const save = useSavePayTerms(examId);
  const [draft, setDraft] = useState(terms.ownSheetRate === null ? "" : String(terms.ownSheetRate));
  const [draftError, setDraftError] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  async function store(next: number | null, done: string) {
    setFailure(null);
    try {
      await save.mutateAsync(next);
      setDraft(next === null ? "" : String(next));
      toast({ title: t(done), tone: "ok" });
    } catch (e) {
      setFailure(t(catalogErrorKey(e)));
    }
  }

  function saveRate(event: React.FormEvent) {
    event.preventDefault();
    const rate = Number(draft);
    if (!(Number.isFinite(rate) && rate > 0)) {
      setDraftError(t("staffCatalog.moduleExams.payRateInvalid"));
      return;
    }
    setDraftError(null);
    void store(rate, "staffCatalog.examMeta.payRateSaved");
  }

  return (
    <div className="grid gap-3">
      <div className="grid gap-1">
        <h3 className="text-label font-semibold text-text">{t("staffCatalog.examMeta.payRateTitle")}</h3>
        <p className="text-body text-text">{t("staffCatalog.examMeta.payRateNow", { rate: formatMoney(terms.effectiveSheetRate) ?? DASH })}</p>
        <p className="text-meta text-muted">
          {terms.ownSheetRate === null ? t("staffCatalog.examMeta.payRateInherited") : t("staffCatalog.examMeta.payRateOwn")}
        </p>
      </div>

      {canEdit ? (
        <form id="exam-pay-rate-form" onSubmit={saveRate} noValidate className="grid gap-3">
          <Field label={t("staffCatalog.moduleExams.payRate")} error={draftError ?? undefined}>
            <NumberInput
              inputMode="decimal"
              min={0}
              step={0.01}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              className="max-w-40"
            />
          </Field>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" loading={save.isPending} disabled={save.isPending}>
              {t("staffCatalog.examMeta.payRateSave")}
            </Button>
            {terms.ownSheetRate !== null ? (
              <Button
                type="button"
                variant="secondary"
                disabled={save.isPending}
                onClick={() => void store(null, "staffCatalog.examMeta.payRateCleared")}
              >
                {t("staffCatalog.examMeta.payRateClear")}
              </Button>
            ) : null}
          </div>
          {failure ? (
            <p role="alert" className="text-meta text-danger">
              {failure}
            </p>
          ) : null}
        </form>
      ) : (
        <p className="text-meta text-muted">{t("staffCatalog.examMeta.masterOnlyReason")}</p>
      )}
    </div>
  );
}

/** The exam's log, newest first: the kind, from and to, who made the change and when. */
function ChangeHistory({ changes }: { changes: PayTermsChange[] }) {
  const { t } = useLocale();
  const { formatDateTime, formatMoney } = useFormat();

  function fromTo(change: PayTermsChange): { from: string; to: string } {
    if (change.kind === "RATE_SET" || change.kind === "RATE_CHANGED") {
      const rate = (value: number | null) => (value === null ? DASH : (formatMoney(value) ?? DASH));
      return { from: rate(change.previousSheetRate), to: rate(change.sheetRate) };
    }
    return { from: change.previousInstructorName ?? DASH, to: change.instructorName ?? DASH };
  }

  return (
    <div className="grid gap-3">
      <h3 className="text-label font-semibold text-text">{t("staffCatalog.examMeta.historyTitle")}</h3>
      {changes.length === 0 ? (
        <p className="text-meta text-muted">{t("staffCatalog.examMeta.historyEmpty")}</p>
      ) : (
        <ol className="grid gap-2">
          {changes.map((change, index) => {
            const { from, to } = fromTo(change);
            return (
              <li key={`${change.createdAt}-${index}`} className="grid gap-0.5 rounded-control border border-border px-3 py-2">
                <span className="text-body font-medium text-text">{t(`staffCatalog.examMeta.history.${change.kind}`)}</span>
                <span className="text-body text-text">{`${from} → ${to}`}</span>
                <span className="text-meta text-muted">
                  {formatDateTime(change.createdAt)} ·{" "}
                  {t("staffCatalog.examMeta.historyBy", { name: change.changedByName ?? t("staffCatalog.examMeta.historyUnknown") })}
                </span>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}
