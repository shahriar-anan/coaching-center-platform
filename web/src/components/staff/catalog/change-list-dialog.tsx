"use client";

import { useMemo, useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useCategoryTree, type PendingChangeList, type PendingFieldChange } from "@/lib/api/exam/staff-catalog";
import { categoryNames } from "@/components/staff/exams/exam-groups";
import { plainExcerpt } from "@/lib/format/rich-text";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

/** The rich text fields. They show as plain text in the change list (plan 7.2). */
const RICH_FIELDS = new Set(["descriptionEn", "descriptionBn", "rulesEn", "rulesBn"]);

/** One line of the change list: the field, and its published and staged values as plain text. */
export type ChangeRow = { field: string; before: string; after: string };

/**
 * The plain text of a value in the change list. Rich text loses its tags, a missing value shows as a dash, and a
 * boolean reads as yes or no. The caller passes the words for those.
 */
export function changeValueText(field: string, value: unknown, words: { empty: string; yes: string; no: string }): string {
  if (value === null || value === undefined || value === "") {
    return words.empty;
  }
  if (typeof value === "boolean") {
    return value ? words.yes : words.no;
  }
  if (RICH_FIELDS.has(field)) {
    return plainExcerpt(String(value), 2000) || words.empty;
  }
  return String(value);
}

/**
 * Every changed field as a row, in the order the server gives. A category shows its name when `categoryName` knows the
 * id (NR-01 UI Step 1 fix); an id it does not know shows as it is.
 */
export function changeRows(
  fields: PendingFieldChange[],
  words: { empty: string; yes: string; no: string },
  categoryName?: (id: string) => string | undefined,
): ChangeRow[] {
  const text = (field: string, value: unknown) =>
    field === "categoryId" && typeof value === "string" && categoryName
      ? (categoryName(value) ?? value)
      : changeValueText(field, value, words);
  return fields.map((change) => ({
    field: change.field,
    before: text(change.field, change.before),
    after: text(change.field, change.after),
  }));
}

/** The impact lines: how many papers are to be re-scored, and how many students are to be told. Empty when there is none. */
export function impactCounts(changes: PendingChangeList): { papers: number; students: number; notify: boolean; questions: number; packageExams: number } {
  return {
    papers: changes.impact.papersToRescore,
    students: changes.impact.studentsToNotify,
    notify: changes.impact.notifiesStudents,
    questions: changes.questions.length,
    packageExams: changes.packageExams.length,
  };
}

/**
 * The staged changes of a published exam or package: every field before and after, the question changes, the paper
 * version and the impact. The Master Admin publishes them with the revision the list was read at. A stale list is
 * closed with a message to read it again (PENDING_CHANGES_STALE).
 */
export function ChangeListDialog({
  open,
  onOpenChange,
  changes,
  onPublish,
  onFailed,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  changes: PendingChangeList | null;
  onPublish: (revisionId: string) => Promise<unknown>;
  onFailed: (message: string) => void;
}) {
  const { t, locale } = useLocale();
  const { formatNumber } = useFormat();
  const [pending, setPending] = useState(false);
  const words = { empty: t("staffCatalog.changes.empty"), yes: t("staffCatalog.changes.yes"), no: t("staffCatalog.changes.no") };

  async function publish() {
    if (!changes) {
      return;
    }
    setPending(true);
    try {
      await onPublish(changes.revisionId);
      onOpenChange(false);
    } catch (e) {
      onOpenChange(false);
      onFailed(t(catalogErrorKey(e)));
    } finally {
      setPending(false);
    }
  }

  const tree = useCategoryTree();
  const categories = useMemo(() => categoryNames(tree.data), [tree.data]);
  const categoryName = (id: string) => {
    const found = categories.get(id);
    return found ? (locale === "bn" ? found.titleBn : found.titleEn) : undefined;
  };
  const rows = changes ? changeRows(changes.fields, words, categoryName) : [];
  const impact = changes ? impactCounts(changes) : null;

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffCatalog.changes.title")}
      description={t("staffCatalog.changes.body")}
      className="sm:max-w-2xl"
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)} disabled={pending}>
            {t("common.actions.cancel")}
          </Button>
          <Button loading={pending} disabled={pending || !changes} onClick={publish}>
            {t("staffCatalog.changes.publish")}
          </Button>
        </>
      }
    >
      {!changes ? (
        <p className="text-meta text-muted">{t("staffCatalog.changes.none")}</p>
      ) : (
        <div className="grid gap-5 text-body">
          {rows.length === 0 ? (
            <p className="text-meta text-muted">{t("staffCatalog.changes.noFields")}</p>
          ) : (
            <table className="w-full text-start text-meta">
              <thead>
                <tr className="text-muted">
                  <th scope="col" className="py-1 pe-3 font-medium">{t("staffCatalog.changes.field")}</th>
                  <th scope="col" className="py-1 pe-3 font-medium">{t("staffCatalog.changes.before")}</th>
                  <th scope="col" className="py-1 font-medium">{t("staffCatalog.changes.after")}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.field} className="border-t border-border align-top">
                    <th scope="row" className="py-2 pe-3 font-medium">
                      {t(`staffCatalog.changes.fields.${row.field}`)}
                    </th>
                    <td className="py-2 pe-3 text-muted">{row.before}</td>
                    <td className="py-2">{row.after}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {impact ? (
            <ul className="grid gap-1 text-meta" lang={locale}>
              {impact.questions > 0 ? <li>{t("staffCatalog.changes.questions", { count: formatNumber(impact.questions) })}</li> : null}
              {impact.packageExams > 0 ? <li>{t("staffCatalog.changes.packageExams", { count: formatNumber(impact.packageExams) })}</li> : null}
              {changes?.paper ? (
                <li>
                  {t("staffCatalog.changes.paper", {
                    before: String(changes.paper.beforeVersion ?? "—"),
                    after: String(changes.paper.afterVersion ?? "—"),
                  })}
                </li>
              ) : null}
              {impact.papers > 0 ? <li>{t("staffCatalog.changes.papers", { count: formatNumber(impact.papers) })}</li> : null}
              <li>
                {impact.notify
                  ? t("staffCatalog.changes.students", { count: formatNumber(impact.students) })
                  : t("staffCatalog.changes.noStudents")}
              </li>
            </ul>
          ) : null}
        </div>
      )}
    </Dialog>
  );
}
