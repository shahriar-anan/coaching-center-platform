"use client";

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { useRole } from "@/lib/auth/guards";
import { useCategoryTree, useModules, type CategoryTreeNode, type StaffModule } from "@/lib/api/exam/staff-catalog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Select } from "@/components/ui/select";
import { SearchInput } from "@/components/ui/search-input";
import { Table, type Column } from "@/components/ui/table";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { Button } from "@/components/ui/button";

type CategoryName = { id: string; titleEn: string; titleBn: string; depth: number };

function flattenNames(nodes: CategoryTreeNode[], depth = 0): CategoryName[] {
  return nodes.flatMap((node) => [
    { id: node.id, titleEn: node.titleEn, titleBn: node.titleBn, depth },
    ...flattenNames(node.children, depth + 1),
  ]);
}

/** The module list. The server filters by category; search and status narrow the loaded page. */
export function ModuleList() {
  const { t, locale } = useLocale();
  const { pickTitle, formatMoney } = useFormat();
  const role = useRole();
  const router = useRouter();
  const [params, setParams] = useListParams({ q: "", status: "", category: "" });
  const tree = useCategoryTree();
  const modules = useModules({ categoryId: params.category || undefined });

  const categoryNames = useMemo(() => flattenNames(tree.data ?? []), [tree.data]);
  const categoryLabel = (id: string) => {
    const found = categoryNames.find((c) => c.id === id);
    if (!found) {
      return "";
    }
    return locale === "bn" ? found.titleBn : found.titleEn;
  };

  const rows = useMemo(() => {
    const q = params.q.trim().toLowerCase();
    return (modules.data?.content ?? []).filter((m) => {
      if (params.status && m.status !== params.status) {
        return false;
      }
      if (q && !`${m.titleEn} ${m.titleBn}`.toLowerCase().includes(q)) {
        return false;
      }
      return true;
    });
  }, [modules.data, params.q, params.status]);

  const columns: Column<StaffModule>[] = [
    {
      key: "name",
      header: t("staffCatalog.modules.columns.name"),
      cell: (m) => <span className="font-medium">{pickTitle(m)}</span>,
    },
    {
      key: "category",
      header: t("staffCatalog.modules.columns.category"),
      cell: (m) => categoryLabel(m.categoryId),
    },
    ...(role.canEditPrice || role.isSystemAdmin
      ? [
          {
            key: "price",
            header: t("staffCatalog.modules.columns.price"),
            cell: (m: StaffModule) => formatMoney(m.price) ?? t("staffCatalog.modules.noPrice"),
          },
        ]
      : []),
    {
      key: "status",
      header: t("staffCatalog.modules.columns.status"),
      cell: (m) => <StatusPill status={m.status} />,
    },
  ];

  const canOpen = role.canAuthor;

  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-[1fr_auto_auto] sm:items-end">
        <SearchInput value={params.q} onChange={(q) => setParams({ q })} placeholder={t("staffCatalog.modules.searchPlaceholder")} />
        <Select
          aria-label={t("staffCatalog.modules.filterCategory")}
          value={params.category}
          onChange={(e) => setParams({ category: e.target.value })}
          className="sm:w-56"
        >
          <option value="">{t("staffCatalog.modules.allCategories")}</option>
          {categoryNames.map((c) => (
            <option key={c.id} value={c.id}>
              {`${"— ".repeat(c.depth)}${locale === "bn" ? c.titleBn : c.titleEn}`}
            </option>
          ))}
        </Select>
        <Select
          aria-label={t("staffCatalog.modules.filterStatus")}
          value={params.status}
          onChange={(e) => setParams({ status: e.target.value })}
          className="sm:w-44"
        >
          <option value="">{t("staffCatalog.modules.allStatuses")}</option>
          <option value="DRAFT">{t("staffCatalog.status.DRAFT")}</option>
          <option value="PUBLISHED">{t("staffCatalog.status.PUBLISHED")}</option>
          <option value="ARCHIVED">{t("staffCatalog.status.ARCHIVED")}</option>
        </Select>
      </div>

      {modules.isError && !modules.data ? (
        <ErrorState onRetry={() => modules.refetch()} message={t("staffCatalog.modules.loadError")} />
      ) : (
        <>
          {modules.isError ? (
            <p role="alert" className="text-sm text-danger">
              {t("staffCatalog.modules.staleError")}
            </p>
          ) : null}
          <Table
            columns={columns}
            rows={rows}
            rowKey={(m) => m.id}
            loading={modules.isPending}
            onRowClick={canOpen ? (m) => router.push(`/staff/modules/${m.id}`) : undefined}
            empty={
              <EmptyState
                title={t("staffCatalog.modules.emptyTitle")}
                body={role.canAuthor ? t("staffCatalog.modules.emptyBody") : t("staffCatalog.modules.emptyInstructorBody")}
                action={canOpen ? <Button href="/staff/modules/new">{t("staffCatalog.modules.new")}</Button> : undefined}
              />
            }
          />
        </>
      )}
    </div>
  );
}
