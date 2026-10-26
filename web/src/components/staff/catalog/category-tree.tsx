"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { ChevronDown, ChevronRight } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import {
  useArchiveCategory,
  useCategoryTree,
  useDeleteCategory,
  type CategoryTreeNode,
} from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { SearchInput } from "@/components/ui/search-input";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { StatusPill, type CatalogStatusValue } from "@/components/staff/catalog/status-pill";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

/** A category is archived, published when any module under it is published, else draft. */
export function categoryStatus(node: CategoryTreeNode): CatalogStatusValue {
  if (node.archived) {
    return "ARCHIVED";
  }
  return node.modules.some((m) => m.status === "PUBLISHED") ? "PUBLISHED" : "DRAFT";
}

function titleOf(node: CategoryTreeNode, locale: "bn" | "en") {
  return locale === "bn" ? node.titleBn : node.titleEn;
}

/** A node is kept by a search when its title or any descendant's title matches. */
function matches(node: CategoryTreeNode, query: string): boolean {
  const q = query.toLowerCase();
  const own = `${node.titleEn} ${node.titleBn}`.toLowerCase().includes(q);
  return own || node.children.some((child) => matches(child, query));
}

type RowProps = {
  node: CategoryTreeNode;
  depth: number;
  query: string;
  expanded: Set<string>;
  onToggle: (id: string) => void;
  canDelete: boolean;
};

function Row({ node, depth, query, expanded, onToggle, canDelete }: RowProps) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const archive = useArchiveCategory();
  const remove = useDeleteCategory();
  const [confirm, setConfirm] = useState<"archive" | "delete" | null>(null);
  const isOpen = query ? true : expanded.has(node.id);
  const hasChildren = node.children.length > 0;
  const name = titleOf(node, locale);
  const visibleChildren = query ? node.children.filter((c) => matches(c, query)) : node.children;

  return (
    <li>
      <div
        className="flex flex-wrap items-center gap-2 border-b border-border py-2.5 ps-[calc(var(--depth)*1.25rem)] pe-2"
        style={{ "--depth": depth } as React.CSSProperties}
      >
        {hasChildren ? (
          <button
            type="button"
            onClick={() => onToggle(node.id)}
            aria-expanded={isOpen}
            aria-label={isOpen ? t("staffCatalog.categories.collapse") : t("staffCatalog.categories.expand")}
            className="inline-flex size-10 items-center justify-center rounded-control text-muted hover:bg-bg"
          >
            {isOpen ? <ChevronDown aria-hidden="true" className="size-4" /> : <ChevronRight aria-hidden="true" className="size-4" />}
          </button>
        ) : (
          <span aria-hidden="true" className="inline-block size-10" />
        )}
        <Link href={`/staff/categories/${node.id}`} className="min-w-0 flex-1 font-medium text-text hover:underline">
          {name}
        </Link>
        <span className="text-meta text-muted">
          {t("staffCatalog.categories.moduleCount", { count: node.modules.length })}
        </span>
        <StatusPill status={categoryStatus(node)} />
        {canDelete ? (
          <span className="flex items-center gap-1">
            {!node.archived ? (
              <Button variant="ghost" className="min-h-10 px-2" onClick={() => setConfirm("archive")}>
                {t("staffCatalog.categories.archive")}
              </Button>
            ) : null}
            <Button variant="danger" className="min-h-10 px-2" onClick={() => setConfirm("delete")}>
              {t("staffCatalog.categories.delete")}
            </Button>
          </span>
        ) : null}
      </div>

      {canDelete ? (
        <>
          <ConfirmDialog
            open={confirm === "archive"}
            onOpenChange={(open) => setConfirm(open ? "archive" : null)}
            title={t("staffCatalog.categories.archiveTitle", { name })}
            body={t("staffCatalog.categories.archiveBody")}
            confirmLabel={t("staffCatalog.categories.archiveConfirm")}
            onConfirm={async () => {
              try {
                await archive.mutateAsync(node.id);
                toast({ title: t("staffCatalog.categories.archived"), tone: "ok" });
              } catch (error) {
                throw new Error(t(catalogErrorKey(error)));
              }
            }}
          />
          <ConfirmDialog
            open={confirm === "delete"}
            onOpenChange={(open) => setConfirm(open ? "delete" : null)}
            title={t("staffCatalog.categories.deleteTitle", { name })}
            body={hasChildren ? t("staffCatalog.categories.deleteHasChildren") : t("staffCatalog.categories.deleteBody")}
            confirmLabel={t("staffCatalog.categories.deleteConfirm")}
            tone="danger"
            onConfirm={async () => {
              try {
                await remove.mutateAsync(node.id);
                toast({ title: t("staffCatalog.categories.deleted"), tone: "ok" });
              } catch (error) {
                throw new Error(t(catalogErrorKey(error)));
              }
            }}
          />
        </>
      ) : null}

      {hasChildren && isOpen ? (
        <ul className="grid">
          {visibleChildren.map((child) => (
            <Row
              key={child.id}
              node={child}
              depth={depth + 1}
              query={query}
              expanded={expanded}
              onToggle={onToggle}
              canDelete={canDelete}
            />
          ))}
        </ul>
      ) : null}
    </li>
  );
}

/** The category tree. Search keeps the path to each match; actions depend on the role. */
export function CategoryTree() {
  const { t } = useLocale();
  const role = useRole();
  const tree = useCategoryTree();
  const [query, setQuery] = useState("");
  const [expanded, setExpanded] = useState<Set<string> | null>(null);

  const roots = useMemo(() => tree.data ?? [], [tree.data]);
  const openSet = useMemo(() => {
    if (expanded) {
      return expanded;
    }
    const all = new Set<string>();
    const walk = (nodes: CategoryTreeNode[]) =>
      nodes.forEach((node) => {
        all.add(node.id);
        walk(node.children);
      });
    walk(roots);
    return all;
  }, [expanded, roots]);

  function toggle(id: string) {
    setExpanded((current) => {
      const next = new Set(current ?? openSet);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  if (tree.isPending) {
    return <Skeleton variant="rows" count={6} />;
  }
  if (tree.isError) {
    return <ErrorState onRetry={() => tree.refetch()} message={t("staffCatalog.categories.loadError")} />;
  }

  const shownRoots = query ? roots.filter((node) => matches(node, query)) : roots;

  return (
    <div className="grid gap-4">
      <SearchInput value={query} onChange={setQuery} placeholder={t("staffCatalog.categories.searchPlaceholder")} />
      {roots.length === 0 ? (
        <EmptyState
          title={t("staffCatalog.categories.emptyTitle")}
          body={t("staffCatalog.categories.emptyBody")}
          action={
            role.canAuthor ? (
              <Button href="/staff/categories/new">{t("staffCatalog.categories.new")}</Button>
            ) : undefined
          }
        />
      ) : shownRoots.length === 0 ? (
        <EmptyState title={t("staffCatalog.categories.noMatch")} />
      ) : (
        <div className="rounded-card border border-border bg-card">
          <ul className="grid">
            {shownRoots.map((node) => (
              <Row
                key={node.id}
                node={node}
                depth={0}
                query={query}
                expanded={openSet}
                onToggle={toggle}
                canDelete={role.canDelete}
              />
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
