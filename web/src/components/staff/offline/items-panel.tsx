"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Pill } from "@/components/ui/pill";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { apiRequest } from "@/lib/api/client";
import { useCategoryTree, type CategoryTreeNode, type StaffExam, type StaffModule } from "@/lib/api/exam/staff-catalog";
import { useRemoveItem, type AdmissionDetail, type AdmissionItem, type ItemOverlap } from "@/lib/api/exam/offline-admin";
import { ItemAddDialog } from "./items-add-dialog";
import { offlineErrorText } from "./offline-error";
import { SetupCard } from "./setup-card";

/**
 * Card 2, "Exams and packages": the chosen exams and packages as cards, each with its type, category, exam count (packages), the date
 * its access ends, and any access the student already holds that overlaps it. Overlaps never block; they are shown so one is seen before
 * it is granted twice. Before onboarding the review can change the list; after it, only the Master Admin can (plan D16). Adding opens the
 * shared picker in its multiple mode.
 */
export function ItemsPanel({
  id = "items-title",
  step,
  admission,
  canEdit,
  accessUntil,
}: {
  id?: string;
  step?: number;
  admission: AdmissionDetail;
  canEdit: boolean;
  /** Shown beside each item: the course end date, which is when access ends (plan C1). Empty when unknown. */
  accessUntil?: string | null;
}) {
  const { t } = useLocale();
  const { formatDate, pickTitle } = useFormat();
  const { toast } = useToast();
  const categories = useCategoryTree();
  const remove = useRemoveItem(admission.id);
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<AdmissionItem | null>(null);

  const titles = useMemo(() => categoryTitleMap(categories.data, pickTitle), [categories.data, pickTitle]);
  const heldIds = admission.items.map((item) => (item.scope === "MODULE" ? item.moduleId : item.examId)).filter((value): value is string => Boolean(value));
  const overlapsByItem = useMemo(() => new Map(admission.overlaps.map((entry) => [entry.itemId, entry.warnings])), [admission.overlaps]);

  function removeItem(item: AdmissionItem) {
    remove.mutate(item.id, {
      onSuccess: () => toast({ title: t("staffOffline.items.removed"), tone: "ok" }),
      onError: (error) => toast({ title: offlineErrorText(error, t), tone: "danger" }),
    });
  }

  return (
    <SetupCard
      id={id}
      step={step}
      title={t("staffOffline.setupScreen.cards.items")}
      action={
        canEdit ? (
          <Button variant="secondary" size="sm" onClick={() => setAdding(true)}>
            {t("staffOffline.setupScreen.items.add")}
          </Button>
        ) : null
      }
    >
      {admission.items.length === 0 ? (
        <p className="rounded-control border border-dashed border-border p-4 text-meta text-muted">{t("staffOffline.items.empty")}</p>
      ) : (
        <ul className="grid gap-3">
          {admission.items.map((item) => (
            <ItemCard
              key={item.id}
              item={item}
              canEdit={canEdit}
              accessUntil={accessUntil ? formatDate(accessUntil) : null}
              categoryTitle={(categoryId) => (categoryId ? (titles.get(categoryId) ?? "") : "")}
              warnings={overlapsByItem.get(item.id) ?? []}
              onRemove={() => (admission.state === "ONBOARDED" ? setRemoving(item) : removeItem(item))}
            />
          ))}
        </ul>
      )}

      {adding ? <ItemAddDialog admissionId={admission.id} heldIds={heldIds} onOpenChange={setAdding} /> : null}

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
    </SetupCard>
  );
}

/** One chosen exam or package: its type, category, exam count, access end, overlaps and Remove. */
function ItemCard({
  item,
  canEdit,
  accessUntil,
  categoryTitle,
  warnings,
  onRemove,
}: {
  item: AdmissionItem;
  canEdit: boolean;
  accessUntil: string | null;
  categoryTitle: (categoryId: string | null) => string;
  warnings: ItemOverlap[];
  onRemove: () => void;
}) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const info = useItemInfo(item);
  const isPackage = item.scope === "MODULE";
  const category = categoryTitle(info.data?.categoryId ?? null);
  const examCount = isPackage ? packageExamCount(info.data) : null;

  return (
    <li className="grid gap-2 rounded-control border border-border-control bg-bg p-3">
      <div className="flex flex-wrap items-start gap-2">
        <Pill tone={isPackage ? "accent" : "muted"}>{t(isPackage ? "staffOffline.items.package" : "staffOffline.items.exam")}</Pill>
        <span className="min-w-0 flex-1 break-words font-bold text-text">{pickTitle({ titleEn: item.titleEn, titleBn: item.titleBn })}</span>
        {canEdit ? (
          <Button variant="danger" size="sm" onClick={onRemove}>
            {t("staffOffline.items.remove")}
          </Button>
        ) : null}
      </div>

      {category || examCount !== null || accessUntil ? (
        <p className="flex flex-wrap gap-x-3 gap-y-1 text-meta text-muted">
          {category ? <span>{category}</span> : null}
          {examCount !== null ? <span className="tabular-nums">{t("staffOffline.setupScreen.items.examCount", { count: examCount })}</span> : null}
          {accessUntil ? <span>{t("staffOffline.items.accessUntil", { date: accessUntil })}</span> : null}
        </p>
      ) : null}

      {warnings.length > 0 ? (
        <div className="grid gap-1 rounded-control border border-warn/40 bg-warn/10 px-3 py-2 text-meta text-warn">
          <p className="font-semibold">{t("staffOffline.setupScreen.items.overlapTitle")}</p>
          {warnings.map((warning) => {
            const via = warning.packageTitleEn || warning.packageTitleBn ? pickTitle({ titleEn: warning.packageTitleEn, titleBn: warning.packageTitleBn }) : "";
            return (
              <p key={`${warning.kind}-${warning.id}`}>
                {t("staffOffline.items.overlapLine", { title: pickTitle({ titleEn: warning.titleEn, titleBn: warning.titleBn }), via: via ? ` · ${t("staffOffline.setupScreen.items.overlapThrough", { package: via })}` : "" })}
              </p>
            );
          })}
        </div>
      ) : null}
    </li>
  );
}

/** The exam or package behind an admission item, read for its category and (for a package) its exam counts. */
type ItemInfo = Pick<StaffExam, "categoryId"> & Partial<Pick<StaffModule, "mcqCount" | "writtenCount">>;

function useItemInfo(item: AdmissionItem) {
  const isPackage = item.scope === "MODULE";
  const targetId = isPackage ? item.moduleId : item.examId;
  return useQuery({
    queryKey: ["offlineAdmin", "itemInfo", item.scope, targetId],
    queryFn: ({ signal }) =>
      apiRequest<ItemInfo>(isPackage ? `/api/v1/live-exam-modules/${targetId}` : `/api/v1/live-exams/${targetId}`, { signal }),
    enabled: Boolean(targetId),
    staleTime: 60_000,
  });
}

/** The MCQ and written exams a package holds. Null when the server does not count them. */
function packageExamCount(info: ItemInfo | undefined): number | null {
  if (!info || typeof info.mcqCount !== "number" || typeof info.writtenCount !== "number") {
    return null;
  }
  return info.mcqCount + info.writtenCount;
}

/** Every category's title by its id, from the category tree, in the active language. */
function categoryTitleMap(nodes: CategoryTreeNode[] | undefined, pickTitle: (node: CategoryTreeNode) => string): Map<string, string> {
  const map = new Map<string, string>();
  const walk = (list: CategoryTreeNode[]) => {
    for (const node of list) {
      map.set(node.id, pickTitle(node));
      walk(node.children);
    }
  };
  walk(nodes ?? []);
  return map;
}
