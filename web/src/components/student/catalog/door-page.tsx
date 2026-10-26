"use client";

import { useCategoryTree, type CategoryNode, type PublicModule } from "@/lib/api/exam/catalog";
import { formatNumber, pickText, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { DoorDiscovery } from "./door-discovery";
import { PackageCard } from "@/components/ui/package/package-card";
import { coverSrc } from "@/components/ui/package/package-cover";
import {
  categoryPath,
  countModules,
  doorLabelKey,
  doorPath,
  doorType,
  findCategoryPath,
  firstLine,
  modulePath,
  moduleTypeCount,
  type Door,
} from "./catalog-helpers";

function CategoryCard({ door, node }: { door: Door; node: CategoryNode }) {
  const { t, locale } = useLocale();
  const modules = countModules(node);
  const subtitle =
    modules === 0
      ? t("catalog.comingSoon")
      : node.children?.length
        ? t("catalog.countCategories", { n: formatNumber(node.children.length, locale) })
        : t("catalog.countModules", { n: formatNumber(modules, locale) });
  return (
    <Card href={categoryPath(door, node.id)}>
      <p className="text-card-title font-bold">⚖️ {pickTitle(node, locale)}</p>
      <p className="text-body text-muted">{subtitle}</p>
    </Card>
  );
}

function ModuleCard({ door, module }: { door: Door; module: PublicModule }) {
  const { locale } = useLocale();
  const type = doorType(door);
  const count = moduleTypeCount(module, type);
  const description = firstLine(pickText({ ...module }, "description", locale));
  return (
    <PackageCard
      href={modulePath(door, module.id)}
      cover={coverSrc(module.coverUrl)}
      type={type}
      title={pickTitle(module, locale)}
      summary={description || null}
      examCount={count ?? 0}
      price={module.price ?? null}
    />
  );
}

export function ComingSoonCard() {
  const { t } = useLocale();
  return (
    <div className="rounded-card border border-border bg-card p-4">
      <Pill tone="accent">{t("catalog.comingSoon")}</Pill>
      <p className="mt-2 text-muted">{t("catalog.comingSoonBody")}</p>
    </div>
  );
}

/** `/mcq` and `/written` without a category, or a category page with one. */
export function DoorPage({ door, categoryId }: { door: Door; categoryId?: string }) {
  const { t, locale } = useLocale();
  const tree = useCategoryTree(doorType(door));
  const doorLabel = t(doorLabelKey(door));
  const doorTitle = t(door === "mcq" ? "catalog.doorTitleMcq" : "catalog.doorTitleWritten");

  if (tree.isPending) {
    return (
      <>
        <PageHeader title={doorTitle} breadcrumbs={[{ label: t("catalog.home"), href: "/" }, { label: doorLabel }]} />
        <Skeleton variant="card" count={3} />
      </>
    );
  }
  if (tree.isError && !tree.data) {
    return (
      <>
        <PageHeader title={doorTitle} breadcrumbs={[{ label: t("catalog.home"), href: "/" }, { label: doorLabel }]} />
        <ErrorState onRetry={() => void tree.refetch()} />
      </>
    );
  }
  const roots = [...(tree.data ?? [])].sort((a, b) => a.position - b.position);

  if (!categoryId) {
    return (
      <>
        <PageHeader title={doorTitle} breadcrumbs={[{ label: t("catalog.home"), href: "/" }, { label: doorLabel }]} />
        <DoorDiscovery
          door={door}
          categories={
            roots.length ? (
              <div className="grid gap-3">
                {roots.map((node) => (
                  <CategoryCard key={node.id} door={door} node={node} />
                ))}
              </div>
            ) : (
              <ComingSoonCard />
            )
          }
        />
      </>
    );
  }

  const path = findCategoryPath(roots, categoryId);
  if (!path) {
    return (
      <>
        <PageHeader title={t("catalog.notFound")} breadcrumbs={[{ label: t("catalog.home"), href: "/" }, { label: doorLabel, href: doorPath(door) }]} />
        <EmptyState
          title={t("catalog.notFound")}
          body={t("catalog.notFoundBody")}
          action={
            <Button variant="secondary" href={doorPath(door)}>
              {t("catalog.backToDoor", { door: doorLabel })}
            </Button>
          }
        />
      </>
    );
  }
  const node = path[path.length - 1];
  const children = [...(node.children ?? [])].sort((a, b) => a.position - b.position);
  const modules = [...(node.modules ?? [])].sort((a, b) => a.position - b.position);
  const breadcrumbs = [
    { label: t("catalog.home"), href: "/" },
    { label: doorLabel, href: doorPath(door) },
    ...path.slice(0, -1).map((c) => ({ label: pickTitle(c, locale), href: categoryPath(door, c.id) })),
    { label: pickTitle(node, locale) },
  ];

  return (
    <>
      <PageHeader title={pickTitle(node, locale)} breadcrumbs={breadcrumbs} />
      {children.length === 0 && modules.length === 0 ? (
        <ComingSoonCard />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {children.map((child) => (
            <CategoryCard key={child.id} door={door} node={child} />
          ))}
          {modules.map((module) => (
            <ModuleCard key={module.id} door={door} module={module} />
          ))}
        </div>
      )}
    </>
  );
}
