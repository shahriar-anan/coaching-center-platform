"use client";

import { useState } from "react";
import Link from "next/link";
import { useQueries } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import {
  hasStagedChanges,
  pendingChangesQuery,
  usePublishStagedPackage,
  useStagedSummary,
  type ChangeKind,
} from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { changeRows, teacherWords } from "@/components/staff/catalog/change-list-dialog";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

type Part = { kind: ChangeKind; id: string; title: string };

/**
 * The one publish of a package's waiting changes: its own, each exam's and any teacher change, listed before the Master Admin
 * publishes. Publishing sends the revision ids that were read, so nothing that changed after the read goes out.
 */
export function StagedPublishDialog({
  moduleId,
  open,
  onOpenChange,
}: {
  moduleId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t, locale } = useLocale();
  const { formatNumber } = useFormat();
  const { toast } = useToast();
  const summary = useStagedSummary(moduleId);
  const publish = usePublishStagedPackage(moduleId);
  const [error, setError] = useState<string | null>(null);

  const parts: Part[] = [];
  if (summary.data?.packageChanges) {
    parts.push({ kind: "package", id: moduleId, title: t("staffCatalog.staged.packageRow") });
  }
  for (const exam of summary.data?.exams ?? []) {
    parts.push({ kind: "exam", id: exam.id, title: locale === "bn" ? exam.titleBn : exam.titleEn });
  }
  const lists = useQueries({
    queries: parts.map((part) => ({ ...pendingChangesQuery(part.kind, part.id), enabled: open })),
  });
  const failed = lists.some((query) => query.isError);
  // Publishing needs every list read, and each one must still name a revision.
  const ready = parts.length > 0 && lists.every((query) => query.isSuccess && query.data);

  const words = { empty: t("staffCatalog.changes.empty"), yes: t("staffCatalog.changes.yes"), no: t("staffCatalog.changes.no") };

  async function onPublish() {
    setError(null);
    const body: { revisionId?: string; examRevisionIds: Record<string, string> } = { examRevisionIds: {} };
    parts.forEach((part, index) => {
      const list = lists[index]?.data;
      if (!list) {
        return;
      }
      if (part.kind === "package") {
        body.revisionId = list.revisionId;
      }
      else {
        body.examRevisionIds[part.id] = list.revisionId;
      }
    });
    try {
      await publish.mutateAsync(body);
      toast({ title: t("staffCatalog.staged.published"), tone: "ok" });
      onOpenChange(false);
    } catch (e) {
      setError(t(catalogErrorKey(e)));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffCatalog.staged.dialogTitle")}
      description={t("staffCatalog.staged.dialogBody")}
      className="sm:max-w-2xl"
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)} disabled={publish.isPending}>
            {t("common.actions.cancel")}
          </Button>
          {parts.length > 0 ? (
            <Button loading={publish.isPending} disabled={!ready || publish.isPending} onClick={() => void onPublish()}>
              {t("staffCatalog.changes.publishChanges")}
            </Button>
          ) : null}
        </>
      }
    >
      {parts.length === 0 ? (
        <p className="text-meta text-muted">{t("staffCatalog.staged.dialogEmpty")}</p>
      ) : (
        <div className="grid gap-6 text-body">
          {parts.map((part, index) => {
            const list = lists[index]?.data ?? null;
            const rows = list
              ? changeRows(teacherWords(list.fields, t("staffCatalog.staged.teacherAssigned"), t("staffCatalog.staged.teacherNone")), words)
              : [];
            return (
              <section key={`${part.kind}-${part.id}`} className="grid gap-2" aria-label={part.title}>
                <h3 className="text-label font-semibold">{part.title}</h3>
                {!list ? (
                  <p className="text-meta text-muted">{t("staffCatalog.staged.loadError")}</p>
                ) : rows.length === 0 ? (
                  <p className="text-meta text-muted">{t("staffCatalog.changes.noFields")}</p>
                ) : (
                  <table className="w-full text-start text-meta">
                    <thead>
                      <tr className="text-muted">
                        <th scope="col" className="py-1 pe-3 text-start font-medium">{t("staffCatalog.changes.field")}</th>
                        <th scope="col" className="py-1 pe-3 text-start font-medium">{t("staffCatalog.changes.before")}</th>
                        <th scope="col" className="py-1 text-start font-medium">{t("staffCatalog.changes.after")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((row) => (
                        <tr key={row.field} className="border-t border-border align-top">
                          <th scope="row" className="py-2 pe-3 text-start font-medium">
                            {t(`staffCatalog.changes.fields.${row.field}`)}
                          </th>
                          <td className="py-2 pe-3 text-start text-muted">{row.before}</td>
                          <td className="py-2 text-start">{row.after}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
                {list && list.questions.length > 0 ? (
                  <p className="text-meta">{t("staffCatalog.changes.questions", { count: formatNumber(list.questions.length) })}</p>
                ) : null}
              </section>
            );
          })}
          {failed ? (
            <p role="alert" className="text-meta text-danger">
              {t("staffCatalog.staged.loadError")}
            </p>
          ) : null}
          {error ? (
            <p role="alert" className="text-meta text-danger">
              {error}
            </p>
          ) : null}
        </div>
      )}
    </Dialog>
  );
}

/**
 * The changes waiting on the package and its exams, shown at the top of the package screen to everyone who can author it. The
 * Master Admin publishes them from here; a System Admin is told that the Master Admin does. Nothing is shown when nothing waits.
 */
export function StagedChangesPanel({ moduleId }: { moduleId: string }) {
  const { t, locale } = useLocale();
  const { formatNumber } = useFormat();
  const role = useRole();
  const summary = useStagedSummary(moduleId);
  const [reviewing, setReviewing] = useState(false);
  const data = summary.data;

  if (!role.canAuthor || !hasStagedChanges(data)) {
    return null;
  }

  return (
    <section
      aria-labelledby="staged-changes-heading"
      className="grid max-w-3xl gap-3 rounded-card border border-warn/40 bg-warn/10 p-5"
    >
      <div className="grid gap-1">
        <h2 id="staged-changes-heading" className="text-card-title font-semibold">
          {t("staffCatalog.staged.panelTitle")}
        </h2>
        <p className="text-meta text-muted">{t("staffCatalog.staged.panelBody")}</p>
      </div>
      <ul className="grid gap-2 text-body">
        {data?.packageChanges ? <li>{t("staffCatalog.staged.packageRow")}</li> : null}
        {data?.exams.map((exam) => (
          <li key={exam.id} className="flex flex-wrap items-center justify-between gap-3">
            <span>
              {locale === "bn" ? exam.titleBn : exam.titleEn}{" "}
              <span className="text-meta text-muted">{t("staffCatalog.staged.examChanges", { count: formatNumber(exam.changes) })}</span>
            </span>
            <Link href={`/staff/exams/${exam.id}`} className="text-meta underline hover:no-underline">
              {t("staffCatalog.staged.openExam")}
            </Link>
          </li>
        ))}
        {data?.instructorChanges ? <li>{t("staffCatalog.staged.teacherRow")}</li> : null}
      </ul>
      {role.canPublishExam ? (
        <div>
          <Button onClick={() => setReviewing(true)}>{t("staffCatalog.staged.review")}</Button>
        </div>
      ) : (
        <p className="text-meta text-muted">{t("staffCatalog.staged.masterOnly")}</p>
      )}
      {role.canPublishExam ? <StagedPublishDialog moduleId={moduleId} open={reviewing} onOpenChange={setReviewing} /> : null}
    </section>
  );
}
