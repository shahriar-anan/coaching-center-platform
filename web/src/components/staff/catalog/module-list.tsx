"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { useRole } from "@/lib/auth/guards";
import { useCategoryTree, useModules, type CategoryTreeNode, type StaffModule } from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { RowMenu, type RowMenuItem } from "@/components/ui/row-menu";
import { Select } from "@/components/ui/select";
import { SearchInput } from "@/components/ui/search-input";
import { Table, type Column } from "@/components/ui/table";
import { StatusPill } from "@/components/staff/catalog/status-pill";

type CategoryName = { id: string; titleEn: string; titleBn: string; depth: number };

function flattenNames(nodes: CategoryTreeNode[], depth = 0): CategoryName[] {
  return nodes.flatMap((node) => [
    { id: node.id, titleEn: node.titleEn, titleBn: node.titleBn, depth },
    ...flattenNames(node.children, depth + 1),
  ]);
}

/** The module list. Category, search and status are in the URL and applied by the server (G5). */
export function ModuleList() {
  const { t, locale } = useLocale();
  const { pickTitle, formatMoney, formatNumber } = useFormat();
  const role = useRole();
  const router = useRouter();
  const [params, setParams] = useListParams({ q: "", status: "", category: "" });
  const [text, setText] = useState(params.q);
  const tree = useCategoryTree();
  const modules = useModules({ categoryId: params.category || undefined, q: params.q || undefined, status: params.status || undefined });

  // Search waits 300 ms after the last keystroke before it changes the URL and the server query.
  useEffect(() => {
    const timer = setTimeout(() => {
      if (text !== params.q) {
        setParams({ q: text });
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [text, params.q, setParams]);

  const categoryNames = useMemo(() => flattenNames(tree.data ?? []), [tree.data]);
  const categoryLabel = (id: string) => {
    const found = categoryNames.find((c) => c.id === id);
    if (!found) {
      return "";
    }
    return locale === "bn" ? found.titleBn : found.titleEn;
  };

  const rowMenu = (m: StaffModule) => {
    const items: RowMenuItem[] = [{ label: t("staffCatalog.rowMenu.edit"), href: `/staff/modules/${m.id}` }];
    items.push({ label: t("staffCatalog.rowMenu.exams"), href: `/staff/modules/${m.id}?tab=exams` });
    if (role.canManageRoster) {
      items.push({ label: t("staffCatalog.rowMenu.roster"), href: `/staff/modules/${m.id}?tab=roster` });
    }
    if (role.canAssignInstructor) {
      items.push({ label: t("staffCatalog.rowMenu.instructor"), href: `/staff/modules/${m.id}?tab=instructor` });
    }
    return (
      <div onClick={(e) => e.stopPropagation()}>
        <RowMenu items={items} label={t("staffCatalog.rowMenu.label", { name: pickTitle(m) })} />
      </div>
    );
  };

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
    {
      key: "mcq",
      header: t("staffCatalog.modules.columns.mcq"),
      align: "end",
      cell: (m) => <span className="tabular-nums">{formatNumber(m.mcqCount ?? 0)}</span>,
    },
    {
      key: "written",
      header: t("staffCatalog.modules.columns.written"),
      align: "end",
      cell: (m) => <span className="tabular-nums">{formatNumber(m.writtenCount ?? 0)}</span>,
    },
    ...(role.canEditPrice || role.isSystemAdmin
      ? [
          {
            key: "price",
            header: t("staffCatalog.modules.columns.price"),
            align: "end" as const,
            cell: (m: StaffModule) => formatMoney(m.price) ?? t("staffCatalog.modules.noPrice"),
          },
        ]
      : []),
    {
      key: "status",
      header: t("staffCatalog.modules.columns.status"),
      cell: (m) => <StatusPill status={m.status} />,
    },
    ...(role.canAuthor
      ? [
          {
            key: "actions",
            header: <span className="sr-only">{t("staffCatalog.modules.columns.actions")}</span>,
            align: "end" as const,
            cell: rowMenu,
          },
        ]
      : []),
  ];

  const canOpen = role.canAuthor;

  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-[1fr_auto_auto] sm:items-end">
        <SearchInput value={text} onChange={setText} placeholder={t("staffCatalog.modules.searchPlaceholder")} />
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
            <p role="alert" className="text-meta text-danger">
              {t("staffCatalog.modules.staleError")}
            </p>
          ) : null}
          <Table
            columns={columns}
            rows={modules.data?.content ?? []}
            rowKey={(m) => m.id}
            loading={modules.isPending}
            onRowClick={canOpen ? (m) => router.push(`/staff/modules/${m.id}`) : undefined}
            empty={
              <EmptyState
                title={params.q || params.status || params.category ? t("staffCatalog.modules.emptyFiltered") : t("staffCatalog.modules.emptyTitle")}
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
