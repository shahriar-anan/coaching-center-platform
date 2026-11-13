"use client";

import { useLocale } from "@/i18n/locale-context";
import type { FieldProblem } from "@/lib/api/exam/offline-admin";
import { checklistRows } from "./offline-model";
import { checklistLabelKey } from "./labels";

/**
 * The requirements for "ready" (plan 6.3, principle 2): each one ticked, or shown missing with what the API says is wrong. The
 * Ready button stays off until every line is ticked, and this list says why.
 */
export function ReadyChecklist({ problems }: { problems: FieldProblem[] }) {
  const { t } = useLocale();
  const rows = checklistRows(problems);
  const complete = problems.length === 0;
  return (
    <section aria-labelledby="ready-checklist-title" className="grid gap-3">
      <h3 id="ready-checklist-title" className="text-card-title text-text">
        {t("staffOffline.checklist.title")}
      </h3>
      <ul className="grid gap-2">
        {rows.map((row) => {
          const ok = row.missing.length === 0;
          return (
            <li key={row.key} className="grid gap-0.5 rounded-control border border-border-control bg-card px-3 py-2">
              <div className="flex items-center gap-2 text-body text-text">
                <span
                  aria-hidden="true"
                  className={ok ? "grid size-5 place-items-center rounded-full bg-ok/15 text-meta text-ok-text" : "grid size-5 place-items-center rounded-full bg-warn/15 text-meta text-warn"}
                >
                  {ok ? "✓" : "!"}
                </span>
                <span className="flex-1">{t(checklistLabelKey(row.key))}</span>
                <span className={ok ? "text-meta text-ok-text" : "text-meta text-warn"}>
                  {ok ? t("staffOffline.checklist.ok") : t("staffOffline.checklist.missing")}
                </span>
              </div>
              {row.missing.map((problem) => (
                <p key={`${problem.field}-${problem.message}`} className="ps-7 text-meta text-muted">
                  {problem.message}
                </p>
              ))}
            </li>
          );
        })}
      </ul>
      <p className={complete ? "text-meta text-ok-text" : "text-meta text-muted"}>
        {complete ? t("staffOffline.checklist.allReady") : t("staffOffline.checklist.why")}
      </p>
    </section>
  );
}
