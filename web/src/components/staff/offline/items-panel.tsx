"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { SearchInput } from "@/components/ui/search-input";
import { Pill } from "@/components/ui/pill";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import {
  useAddItem,
  useItemOptions,
  useRemoveItem,
  type AdmissionDetail,
  type AdmissionItem,
  type ItemAdded,
} from "@/lib/api/exam/offline-admin";
import { offlineErrorText } from "./offline-error";

/**
 * The exams and packages of an admission. Before onboarding they are a list the review can change; after it, only the Master Admin can
 * (plan D16). The overlaps the setup shows are the admission's own, read with it: any access the student already holds that overlaps an
 * item. They never block; they are shown so an overlap is seen before it is granted twice.
 */
export function ItemsPanel({
  admission,
  canEdit,
  accessUntil,
}: {
  admission: AdmissionDetail;
  canEdit: boolean;
  /** Shown beside each item after onboarding: the course end date, which is when access ends (plan C1). */
  accessUntil?: string | null;
}) {
  const { t } = useLocale();
  const { pickTitle, formatDate } = useFormat();
  const { toast } = useToast();
  const options = useItemOptions(canEdit);
  const add = useAddItem(admission.id);
  const remove = useRemoveItem(admission.id);
  const [q, setQ] = useState("");
  const [alreadyHeld, setAlreadyHeld] = useState(false);
  const [removing, setRemoving] = useState<AdmissionItem | null>(null);

  const held = useMemo(
    () => new Set(admission.items.map((item) => (item.scope === "EXAM" ? `E:${item.examId}` : `M:${item.moduleId}`))),
    [admission.items],
  );
  const warnings = useMemo(
    () =>
      admission.overlaps.flatMap((item) => item.warnings.map((warning) => ({ key: `${item.itemId}-${warning.kind}-${warning.id}`, warning }))),
    [admission.overlaps],
  );
  const needle = q.trim().toLowerCase();
  const matches = (titleEn: string, titleBn: string) =>
    !needle || titleEn.toLowerCase().includes(needle) || titleBn.toLowerCase().includes(needle);

  const packages = (options.data?.packages ?? []).filter((item) => matches(item.titleEn, item.titleBn) && !held.has(`M:${item.id}`));
  const exams = (options.data?.exams ?? []).filter((item) => matches(item.titleEn, item.titleBn) && !held.has(`E:${item.id}`));

  function addOption(scope: "EXAM" | "MODULE", id: string) {
    setAlreadyHeld(false);
    add.mutate(scope === "EXAM" ? { scope, examId: id } : { scope, moduleId: id }, {
      onSuccess: (result: ItemAdded) => {
        setAlreadyHeld(result.alreadyHeld);
        toast({ title: t("staffOffline.items.added"), tone: "ok" });
      },
      onError: (error) => toast({ title: offlineErrorText(error, t), tone: "danger" }),
    });
  }

  function removeItem(item: AdmissionItem) {
    remove.mutate(item.id, {
      onSuccess: () => toast({ title: t("staffOffline.items.removed"), tone: "ok" }),
    });
  }

  return (
    <section aria-labelledby="items-title" className="grid gap-3">
      <h3 id="items-title" className="text-card-title text-text">
        {t("staffOffline.items.title")}
      </h3>

      {admission.items.length === 0 ? (
        <p className="text-meta text-muted">{t("staffOffline.items.empty")}</p>
      ) : (
        <ul className="grid gap-2">
          {admission.items.map((item) => (
            <li key={item.id} className="flex flex-wrap items-center gap-2 rounded-control border border-border-control bg-card px-3 py-2">
              <Pill tone={item.scope === "MODULE" ? "accent" : "muted"}>
                {item.scope === "MODULE" ? t("staffOffline.items.package") : t("staffOffline.items.exam")}
              </Pill>
              <span className="min-w-0 flex-1 break-words text-body text-text">{pickTitle({ titleEn: item.titleEn, titleBn: item.titleBn })}</span>
              {accessUntil ? (
                <span className="text-meta text-muted">{t("staffOffline.items.accessUntil", { date: formatDate(accessUntil) })}</span>
              ) : null}
              {canEdit ? (
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => (admission.state === "ONBOARDED" ? setRemoving(item) : removeItem(item))}
                >
                  {t("staffOffline.items.remove")}
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      {warnings.length > 0 || alreadyHeld ? (
        <div role="alert" className="grid gap-1 rounded-control border border-warn/40 bg-warn/10 px-3 py-2 text-meta text-warn">
          <p className="font-semibold">{t("staffOffline.items.overlapTitle")}</p>
          {warnings.map(({ key, warning }) => (
            <p key={key}>
              {t("staffOffline.items.overlapLine", {
                title: pickTitle({ titleEn: warning.titleEn, titleBn: warning.titleBn }),
                via: warning.packageTitleEn || warning.packageTitleBn ? pickTitle({ titleEn: warning.packageTitleEn, titleBn: warning.packageTitleBn }) : "",
              })}
            </p>
          ))}
          {alreadyHeld && warnings.length === 0 ? <p>{t("staffOffline.items.alreadyHeld")}</p> : null}
        </div>
      ) : null}

      {canEdit ? (
        <div className="grid gap-3 rounded-card border border-border-control bg-card p-3">
          <SearchInput id="items-q" value={q} onChange={setQ} placeholder={t("staffOffline.items.searchPlaceholder")} />
          {options.isPending ? (
            <p className="text-meta text-muted">{t("staffOffline.items.loading")}</p>
          ) : (
            <div className="grid gap-3 md:grid-cols-2">
              <div className="grid content-start gap-2">
                <h4 className="text-meta font-semibold text-text">{t("staffOffline.items.packages")}</h4>
                {packages.length === 0 ? <p className="text-meta text-muted">{t("staffOffline.items.noPackages")}</p> : null}
                {packages.map((item) => (
                  <PickRow key={item.id} title={pickTitle({ titleEn: item.titleEn, titleBn: item.titleBn })} onAdd={() => addOption("MODULE", item.id)} busy={add.isPending} />
                ))}
              </div>
              <div className="grid content-start gap-2">
                <h4 className="text-meta font-semibold text-text">{t("staffOffline.items.exams")}</h4>
                {exams.length === 0 ? <p className="text-meta text-muted">{t("staffOffline.items.noExams")}</p> : null}
                {exams.map((item) => (
                  <PickRow key={item.id} title={pickTitle({ titleEn: item.titleEn, titleBn: item.titleBn })} onAdd={() => addOption("EXAM", item.id)} busy={add.isPending} />
                ))}
              </div>
            </div>
          )}
        </div>
      ) : null}

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => {
          if (!open) setRemoving(null);
        }}
        title={t("staffOffline.items.removeTitle", { title: removing ? pickTitle({ titleEn: removing.titleEn, titleBn: removing.titleBn }) : "" })}
        body={t("staffOffline.items.removeBody")}
        confirmLabel={t("staffOffline.items.removeConfirm")}
        tone="danger"
        onConfirm={async () => {
          if (removing) {
            await remove.mutateAsync(removing.id);
          }
        }}
      />
    </section>
  );
}

function PickRow({ title, onAdd, busy }: { title: string; onAdd: () => void; busy: boolean }) {
  const { t } = useLocale();
  return (
    <div className="flex items-center gap-2 rounded-control border border-border-control px-3 py-2">
      <span className="min-w-0 flex-1 break-words text-body text-text">{title}</span>
      <Button size="sm" variant="secondary" onClick={onAdd} disabled={busy}>
        {t("staffOffline.items.add")}
      </Button>
    </div>
  );
}
