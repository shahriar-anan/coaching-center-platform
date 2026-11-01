"use client";

import { useMemo, useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useListParams } from "@/lib/url/list-params";
import { useExam, useModule } from "@/lib/api/exam/staff-catalog";
import {
  useReviewFeature,
  useReviewStatusChange,
  useStaffReviewCounts,
  useStaffReviews,
  type StaffReviewRow,
} from "@/lib/api/exam/staff-reviews";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { SummaryStrip } from "@/components/ui/summary-strip";
import { Tabs } from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import { ReasonDialog } from "@/components/staff/access/reason-dialog";
import type { PickedTarget } from "@/components/staff/target-picker";
import { ReviewRow } from "@/components/staff/reviews/review-row";
import { ReviewTargetDialog } from "@/components/staff/reviews/review-target-dialog";
import { canModerateReviews } from "@/components/staff/reviews/review-access";
import {
  REVIEW_PARAM_DEFAULTS,
  REVIEW_TABS,
  activeTab,
  filtersFromParams,
  hasActiveFilters,
  reviewKindOf,
} from "@/components/staff/reviews/review-params";

const SORTS = ["newest", "oldest", "lowest", "highest"] as const;

/**
 * The staff Reviews page (admin reviews plan, section 5): a summary strip, the tabs (Waiting, Published, Hidden, All), the
 * filters, and the queue with "Load more". The tab and every filter live in the URL, so a link shows the same list and the
 * browser's Back button works. Teachers read the list; only the Master Admin and System Admins act on it.
 */
export function ReviewsPage() {
  const { t } = useLocale();
  const { formatNumber, pickTitle } = useFormat();
  const { role } = useRole();
  const canModerate = canModerateReviews(role);
  const toast = useToast();

  const [params, setParams] = useListParams(REVIEW_PARAM_DEFAULTS);
  const tab = activeTab(params.tab);
  const filters = useMemo(() => filtersFromParams(params), [params]);
  const filtered = hasActiveFilters(params);

  const list = useStaffReviews(filters);
  const counts = useStaffReviewCounts(filters);
  const statusChange = useReviewStatusChange();
  const featureChange = useReviewFeature();

  const [hiding, setHiding] = useState<StaffReviewRow | null>(null);
  const [targetOpen, setTargetOpen] = useState(false);
  // The title of a target chosen on this page, so the button names it before the catalog read has come back.
  const [picked, setPicked] = useState<PickedTarget | null>(null);

  const targetKind = reviewKindOf(params.kind);
  const exam = useExam(params.targetId && targetKind === "exam" ? params.targetId : undefined);
  const pkg = useModule(params.targetId && targetKind === "package" ? params.targetId : undefined);
  const fetchedTitle = pickTitle(targetKind === "exam" ? exam.data : pkg.data);
  const targetTitle = picked && picked.id === params.targetId ? picked.title : fetchedTitle;
  const targetLabel = params.targetId
    ? targetTitle || t("staffReviews.filters.targetUnknown")
    : t("staffReviews.filters.targetAny");
  const initialTarget: PickedTarget | null =
    params.targetId && targetKind ? { kind: targetKind, id: params.targetId, title: targetTitle } : null;

  const rows = list.data?.pages.flatMap((page) => page.content) ?? [];
  const busyId = statusChange.isPending
    ? statusChange.variables?.row.id
    : featureChange.isPending
      ? featureChange.variables?.row.id
      : undefined;

  const c = counts.data;
  const shown = (value: number | undefined) => (value === undefined ? "—" : formatNumber(value));
  const tabBadges: Record<(typeof REVIEW_TABS)[number], number | undefined> = {
    waiting: c?.pending,
    published: c?.approved,
    hidden: c?.hidden,
    all: c ? c.pending + c.approved + c.hidden : undefined,
  };

  function applyTarget(target: PickedTarget | null) {
    setTargetOpen(false);
    if (target) {
      setPicked(target);
      setParams({ kind: target.kind, targetId: target.id });
    } else {
      clearTarget();
    }
  }

  function clearTarget() {
    setPicked(null);
    setParams({ targetId: "" });
  }

  function changeKind(next: string) {
    // A target belongs to one kind; a different Type drops it.
    if (params.targetId && targetKind !== next) {
      setPicked(null);
      setParams({ kind: next, targetId: "" });
      return;
    }
    setParams({ kind: next });
  }

  function resetFilters() {
    setPicked(null);
    setParams({ kind: "", targetId: "", rating: 0, featured: false, q: "", sort: "newest" });
  }

  function approve(row: StaffReviewRow) {
    statusChange.mutate(
      { row, status: "APPROVED" },
      {
        onSuccess: () => toast.toast({ title: t("staffReviews.toast.approved"), tone: "ok" }),
        onError: () => toast.toast({ title: t("staffReviews.toast.failed"), tone: "danger" }),
      },
    );
  }

  /** Publishes the review, then features it. If only the second step fails the review stays published, and the message says so. */
  async function approveAndFeature(row: StaffReviewRow) {
    let approved: StaffReviewRow;
    try {
      approved = await statusChange.mutateAsync({ row, status: "APPROVED" });
    } catch {
      toast.toast({ title: t("staffReviews.toast.failed"), tone: "danger" });
      return;
    }
    try {
      await featureChange.mutateAsync({ row: approved, featured: true });
    } catch {
      toast.toast({ title: t("staffReviews.toast.approvedNotFeatured"), tone: "danger" });
      return;
    }
    toast.toast({ title: t("staffReviews.toast.approvedFeatured"), tone: "ok" });
  }

  async function hide(reason: string) {
    if (!hiding) {
      return;
    }
    try {
      await statusChange.mutateAsync({ row: hiding, status: "HIDDEN", reason });
    } catch (error) {
      toast.toast({ title: t("staffReviews.toast.failed"), tone: "danger" });
      // The dialog keeps open and shows the message.
      throw error;
    }
    toast.toast({ title: t("staffReviews.toast.hidden"), tone: "ok" });
  }

  function feature(row: StaffReviewRow, featured: boolean) {
    featureChange.mutate(
      { row, featured },
      {
        onSuccess: () =>
          toast.toast({ title: t(featured ? "staffReviews.toast.featured" : "staffReviews.toast.unfeatured"), tone: "ok" }),
        onError: () => toast.toast({ title: t("staffReviews.toast.failed"), tone: "danger" }),
      },
    );
  }

  const emptyKey = filtered ? "filtered" : tab;

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffReviews.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/staff" }, { label: t("staffReviews.title") }]}
      />
      {/* Under the title. The header's bottom padding already leaves room, so the grid gap is pulled back. */}
      {canModerate ? null : <p className="-mt-5 text-meta text-muted">{t("staffReviews.readOnly")}</p>}

      <SummaryStrip
        loading={counts.isPending}
        items={[
          { label: t("staffReviews.summary.waiting"), value: shown(c?.pending), tone: "warn" },
          { label: t("staffReviews.summary.published"), value: shown(c?.approved), tone: "ok" },
          { label: t("staffReviews.summary.hidden"), value: shown(c?.hidden) },
          { label: t("staffReviews.summary.featured"), value: shown(c?.featured) },
        ]}
      />

      <Tabs
        ariaLabel={t("staffReviews.tabs.label")}
        value={tab}
        onChange={(next) => setParams({ tab: next })}
        tabs={REVIEW_TABS.map((value) => ({
          value,
          label: t(`staffReviews.tabs.${value}`),
          badge: tabBadges[value],
        }))}
      />

      <div role="group" aria-label={t("staffReviews.filters.label")} className="grid gap-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label={t("staffReviews.filters.kind")}>
            <Select value={params.kind} onChange={(event) => changeKind(event.target.value)}>
              <option value="">{t("staffReviews.filters.kindAll")}</option>
              <option value="exam">{t("staffReviews.filters.kindExam")}</option>
              <option value="package">{t("staffReviews.filters.kindPackage")}</option>
            </Select>
          </Field>

          <div role="group" aria-labelledby="reviews-target-label" className="grid gap-1.5">
            <span id="reviews-target-label" className="text-label font-medium text-text">
              {t("staffReviews.filters.target")}
            </span>
            <div className="flex flex-wrap items-center gap-2">
              <Button
                variant="secondary"
                aria-haspopup="dialog"
                onClick={() => setTargetOpen(true)}
                className="min-w-0 max-w-full"
              >
                <span className="truncate">{targetLabel}</span>
              </Button>
              {params.targetId ? (
                <Button variant="ghost" onClick={clearTarget}>
                  {t("staffReviews.filters.targetClear")}
                </Button>
              ) : null}
            </div>
          </div>

          <Field label={t("staffReviews.filters.rating")}>
            <Select value={String(params.rating)} onChange={(event) => setParams({ rating: Number(event.target.value) })}>
              <option value="0">{t("staffReviews.filters.ratingAny")}</option>
              {[1, 2, 3, 4, 5].map((stars) => (
                <option key={stars} value={stars}>
                  {stars === 1 ? t("staffReviews.filters.ratingOne") : t("staffReviews.filters.ratingMany", { count: formatNumber(stars) })}
                </option>
              ))}
            </Select>
          </Field>

          <Field label={t("staffReviews.filters.search")}>
            <SearchInput
              value={params.q}
              onChange={(q) => setParams({ q })}
              placeholder={t("staffReviews.filters.searchPlaceholder")}
            />
          </Field>

          <Field label={t("staffReviews.filters.sort")}>
            <Select value={params.sort} onChange={(event) => setParams({ sort: event.target.value })}>
              {SORTS.map((sort) => (
                <option key={sort} value={sort}>
                  {t(`staffReviews.filters.sort${sort.charAt(0).toUpperCase()}${sort.slice(1)}`)}
                </option>
              ))}
            </Select>
          </Field>

          <label className="flex min-h-11 items-center gap-2 self-end text-body text-text">
            <input
              type="checkbox"
              checked={params.featured}
              onChange={(event) => setParams({ featured: event.target.checked })}
              className="size-5"
            />
            {t("staffReviews.filters.featured")}
          </label>
        </div>

        {filtered ? (
          <div>
            <Button variant="ghost" onClick={resetFilters}>
              {t("staffReviews.filters.reset")}
            </Button>
          </div>
        ) : null}
      </div>

      <div className="grid gap-4" aria-busy={list.isPending || undefined}>
        {list.isError ? <ErrorState onRetry={() => void list.refetch()} error={list.error} /> : null}

        {list.isPending ? (
          <Skeleton variant="card" count={3} height="h-32" />
        ) : rows.length === 0 ? (
          list.isError ? null : (
            <EmptyState
              title={t(`staffReviews.empty.${emptyKey}.title`)}
              body={t(`staffReviews.empty.${emptyKey}.body`)}
            />
          )
        ) : (
          <ul className="grid gap-3">
            {rows.map((row) => (
              <ReviewRow
                key={row.id}
                row={row}
                canModerate={canModerate}
                busy={busyId === row.id}
                onApprove={approve}
                onApproveAndFeature={approveAndFeature}
                onHide={setHiding}
                onFeature={feature}
              />
            ))}
          </ul>
        )}

        {list.hasNextPage ? (
          <Button
            variant="secondary"
            className="justify-self-start"
            loading={list.isFetchingNextPage}
            onClick={() => void list.fetchNextPage()}
          >
            {t("staffReviews.loadMore")}
          </Button>
        ) : null}
      </div>

      {targetOpen ? (
        <ReviewTargetDialog
          kind={targetKind}
          initial={initialTarget}
          onApply={applyTarget}
          onClose={() => setTargetOpen(false)}
        />
      ) : null}

      <ReasonDialog
        open={hiding !== null}
        onOpenChange={(open) => {
          if (!open) {
            setHiding(null);
          }
        }}
        title={t("staffReviews.hide.title")}
        body={t("staffReviews.hide.body")}
        label={t("staffReviews.hide.label")}
        requiredMessage={t("staffReviews.hide.required")}
        confirmLabel={t("staffReviews.hide.confirm")}
        tone="danger"
        maxLength={300}
        onConfirm={hide}
      />
    </div>
  );
}
