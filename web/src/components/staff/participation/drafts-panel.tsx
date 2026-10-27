"use client";

import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Pill } from "@/components/ui/pill";
import { Table, type Column } from "@/components/ui/table";
import type { DraftItem } from "@/components/staff/participation/use-drafts";
import { useFormat } from "@/lib/format/use-format";
import { useLocale } from "@/i18n/locale-context";

/**
 * The drafts tab: modules and exams that are not published yet, so students cannot see them.
 * A saved one is ready to publish; an unsaved one still needs work. Each row opens where the
 * work is done, the module page or the exam editor.
 */
export function DraftsPanel({ items, loading, failed }: { items: DraftItem[]; loading: boolean; failed: boolean }) {
  const { t } = useLocale();
  const format = useFormat();

  const columns: Column<DraftItem>[] = [
    {
      key: "item",
      header: t("staffPeople.participation.drafts.columns.item"),
      cell: (item) => (
        <div className="grid gap-0.5">
          <span className="font-medium text-text">{format.pickTitle(item)}</span>
          <span className="text-meta text-muted">
            {item.kind === "module"
              ? t("staffPeople.participation.drafts.moduleLine", { n: format.formatNumber(item.examCount) })
              : `${format.pickTitle({ titleEn: item.moduleTitleEn, titleBn: item.moduleTitleBn })} · ${t(
                  item.examType === "MCQ" ? "staffPeople.participation.type.MCQ" : "staffPeople.participation.type.WRITTEN",
                )}`}
          </span>
        </div>
      ),
    },
    {
      key: "when",
      header: t("staffPeople.participation.columnsNew.when"),
      cell: (item) => (
        <span className="text-meta text-muted">
          {item.kind === "exam" && item.liveAt
            ? t("staffPeople.participation.starts", { time: format.formatDateTime(item.liveAt) })
            : t("common.dateTime.notSet")}
        </span>
      ),
    },
    {
      key: "status",
      header: t("staffPeople.participation.columnsNew.next"),
      cell: (item) => (
        <Pill tone={item.finallySaved ? "accent" : "warn"}>
          {t(item.finallySaved ? "staffPeople.participation.drafts.ready" : "staffPeople.participation.drafts.needsWork")}
        </Pill>
      ),
    },
    {
      key: "action",
      header: "",
      align: "end",
      nowrap: true,
      cell: (item) => (
        <Button
          variant={item.finallySaved ? "primary" : "secondary"}
          href={item.kind === "module" ? `/staff/modules/${item.id}` : `/staff/exams/${item.id}`}
        >
          {t(item.kind === "module" ? "staffPeople.participation.drafts.openModule" : "staffPeople.participation.drafts.openExam")}
        </Button>
      ),
    },
  ];

  return (
    <div className="grid gap-3">
      {failed ? <p className="text-meta text-danger">{t("staffPeople.participation.drafts.loadError")}</p> : null}
      <Table
        columns={columns}
        rows={items}
        rowKey={(item) => `${item.kind}:${item.id}`}
        loading={loading}
        empty={
          <EmptyState
            title={t("staffPeople.participation.drafts.empty")}
            body={t("staffPeople.participation.drafts.emptyBody")}
          />
        }
      />
    </div>
  );
}
