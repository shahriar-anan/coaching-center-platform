"use client";

import { Suspense } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { RowMenu } from "@/components/ui/row-menu";
import { Table, type Column } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { TextInput } from "@/components/ui/text-input";
import { DraftsPanel } from "@/components/staff/participation/drafts-panel";
import { useDraftContent } from "@/components/staff/participation/use-drafts";
import {
  FOCUSES,
  focusTotals,
  isFocus,
  matchesFocus,
  paperCounts,
  phaseOf,
  stepOf,
  type Focus,
} from "@/components/staff/participation/exam-step";
import { ALL_STAFF, RequireStaff, useRole } from "@/lib/auth/guards";
import { useModuleOptions, useParticipation, type ParticipationRow } from "@/lib/api/exam/staff-people";
import { useFormat } from "@/lib/format/use-format";
import { useServerNow } from "@/lib/time/clock";
import { useListParams } from "@/lib/url/list-params";
import { useLocale } from "@/i18n/locale-context";

export default function StaffParticipationPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={ALL_STAFF}>
        <Participation />
      </RequireStaff>
    </Suspense>
  );
}

/** A date in Dhaka time, at the start or the end of the day, for the from and to filters. */
function dhakaDay(date: string, end: boolean): string {
  return end ? `${date}T23:59:59+06:00` : `${date}T00:00:00+06:00`;
}

/** A tab loads up to this many exams and filters them here, so a tab needs no paging. */
const FOCUS_SIZE = 100;

/**
 * Participation: one row per exam with its next step and the button for it. The Master Admin
 * sees what to release; the tabs count the papers waiting, across every written exam.
 */
function Participation() {
  const { t } = useLocale();
  const format = useFormat();
  const { canPublishResult } = useRole();
  const now = useServerNow(60_000);
  const [params, setParams] = useListParams({
    page: 0,
    size: 20,
    moduleId: "",
    type: "",
    from: "",
    to: "",
    q: "",
    sort: "liveAt",
    focus: "",
  });
  const focus: Focus = isFocus(params.focus) ? params.focus : "";
  const rows = useParticipation({
    page: focus ? 0 : params.page,
    size: focus ? FOCUS_SIZE : params.size,
    moduleId: params.moduleId,
    type: params.type,
    from: params.from ? dhakaDay(params.from, false) : "",
    to: params.to ? dhakaDay(params.to, true) : "",
    q: params.q,
    sort: params.sort,
  });
  // The tab counts cover every written exam, whatever the filters below say.
  const written = useParticipation({ page: 0, size: FOCUS_SIZE, moduleId: "", type: "WRITTEN", from: "", to: "", q: "", sort: "liveAt" });
  const modules = useModuleOptions();
  const drafts = useDraftContent();
  const showDrafts = focus === "drafts";
  const totals = focusTotals(written.data?.content ?? []);
  const shown = (rows.data?.content ?? []).filter((row) => matchesFocus(row, focus));

  const tabBadge: Record<Focus, number | undefined> = {
    "": undefined,
    release: totals.release,
    mark: totals.mark,
    changes: totals.changes,
    drafts: drafts.pending ? undefined : drafts.items.length,
  };

  const n = (value: number) => format.formatNumber(value);

  const columns: Column<ParticipationRow>[] = [
    {
      key: "exam",
      header: t("staffPeople.participation.columns.exam"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <span className="font-medium text-text">{format.pickTitle({ titleEn: row.titleEn, titleBn: row.titleBn })}</span>
          <span className="text-meta text-muted">
            {format.pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn })} ·{" "}
            {t(row.examType === "MCQ" ? "staffPeople.participation.type.MCQ" : "staffPeople.participation.type.WRITTEN")}
          </span>
        </div>
      ),
    },
    {
      key: "when",
      header: t("staffPeople.participation.columnsNew.when"),
      cell: (row) => {
        const phase = phaseOf(row, now);
        if (phase === "unset") {
          return <span className="text-meta text-muted">{t("common.dateTime.notSet")}</span>;
        }
        return (
          <div className="grid justify-items-start gap-1">
            <Pill tone={phase === "live" ? "ok" : "muted"}>{t(`staffPeople.participation.phase.${phase}`)}</Pill>
            <span className="text-meta text-muted">
              {t("staffPeople.participation.starts", { time: format.formatDateTime(row.liveAt ?? "") })}
            </span>
            <span className="text-meta text-muted">
              {t("staffPeople.participation.ends", { time: format.formatDateTime(row.endAt ?? "") })}
            </span>
          </div>
        );
      },
    },
    {
      key: "progress",
      header: t("staffPeople.participation.columnsNew.progress"),
      cell: (row) => {
        const counts = paperCounts(row);
        const parts = [t("staffPeople.participation.progress.submitted", { n: n(counts.submitted) })];
        if (row.examType === "WRITTEN") {
          parts.push(t("staffPeople.participation.progress.marked", { n: n(counts.marked) }));
          parts.push(t("staffPeople.participation.progress.released", { n: n(counts.released) }));
        }
        if (counts.notSubmitted > 0) {
          parts.push(t("staffPeople.participation.progress.notSubmitted", { n: n(counts.notSubmitted) }));
        }
        return <span className="text-meta text-text">{parts.join(" · ")}</span>;
      },
    },
    {
      key: "next",
      header: t("staffPeople.participation.columnsNew.next"),
      cell: (row) => {
        const step = stepOf(row, now);
        const counts = paperCounts(row);
        let text: string;
        if (step === "mark") {
          text =
            counts.toRelease > 0
              ? t("staffPeople.participation.step.markAndRelease", { n: n(counts.toMark), m: n(counts.toRelease) })
              : t("staffPeople.participation.step.mark", { n: n(counts.toMark) });
        } else if (step === "release") {
          if (!canPublishResult) {
            text = t("staffPeople.participation.step.releaseOther", { n: n(counts.toRelease) });
          } else if (counts.changes > 0) {
            text = t("staffPeople.participation.step.releaseChanges", { n: n(counts.toRelease), c: n(counts.changes) });
          } else {
            text = t("staffPeople.participation.step.release", { n: n(counts.toRelease) });
          }
        } else {
          text = t(`staffPeople.participation.step.${step}`);
        }
        const tone = step === "mark" || step === "release" ? "warn" : step === "done" ? "ok" : step === "open" ? "accent" : "muted";
        return <Pill tone={tone}>{text}</Pill>;
      },
    },
    {
      key: "action",
      header: "",
      align: "end",
      nowrap: true,
      cell: (row) => {
        const step = stepOf(row, now);
        const results = `/staff/exams/${row.examId}/results`;
        const scripts = `/staff/submissions?module=${row.moduleId}&exam=${row.examId}`;
        const canRelease = row.examType === "WRITTEN" && canPublishResult && paperCounts(row).toRelease > 0;
        const primary = canRelease
          ? { label: t("staffPeople.participation.action.release"), href: `${results}#release`, variant: "primary" as const }
          : step === "mark"
            ? { label: t("staffPeople.participation.action.mark"), href: `${scripts}&status=AWAITING`, variant: "secondary" as const }
            : { label: t("staffPeople.participation.action.results"), href: results, variant: "secondary" as const };
        return (
          <div className="flex items-center justify-end gap-2">
            <Button variant={primary.variant} href={primary.href}>
              {primary.label}
            </Button>
            <RowMenu
              label={t("staffPeople.participation.rowMenu.label", {
                name: format.pickTitle({ titleEn: row.titleEn, titleBn: row.titleBn }),
              })}
              items={[
                { label: t("staffResults.rowMenu.openResults"), href: results },
                { label: t("staffResults.rowMenu.openLeaderboard"), href: `/staff/exams/${row.examId}/leaderboard` },
                ...(row.examType === "WRITTEN" ? [{ label: t("staffPeople.participation.openSubmissions"), href: scripts }] : []),
              ]}
            />
          </div>
        );
      },
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffPeople.participation.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffPeople.participation.title") }]}
      />

      <div className="grid gap-3">
        <Tabs
          ariaLabel={t("staffPeople.participation.focus.label")}
          tabs={FOCUSES.map((value) => ({
            value: value || "all",
            label: t(`staffPeople.participation.focus.${value || "all"}`),
            badge: tabBadge[value],
          }))}
          value={focus || "all"}
          onChange={(value) => setParams({ focus: value === "all" ? "" : value })}
        />
        <p className="text-meta text-muted">
          {showDrafts
            ? t("staffPeople.participation.drafts.guide")
            : focus === "changes"
              ? t("staffPeople.participation.guideChanges")
              : t(canPublishResult ? "staffPeople.participation.guideMaster" : "staffPeople.participation.guideOther")}
        </p>
        {canPublishResult && totals.release > 0 && focus !== "release" && !showDrafts ? (
          <Card>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-body text-text">
                {t("staffPeople.participation.releaseSummary", { papers: n(totals.release), exams: n(totals.releaseExams) })}
              </p>
              <Button variant="primary" onClick={() => setParams({ focus: "release" })}>
                {t("staffPeople.participation.focus.release")}
              </Button>
            </div>
          </Card>
        ) : null}
      </div>

      {showDrafts ? (
        <DraftsPanel items={drafts.items} loading={drafts.pending} failed={drafts.failed} />
      ) : (
        <>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Field label={t("staffPeople.participation.filters.module")}>
          <Select value={params.moduleId} onChange={(event) => setParams({ moduleId: event.target.value })}>
            <option value="">{t("staffPeople.participation.filters.allModules")}</option>
            {(modules.data?.content ?? []).map((module) => (
              <option key={module.id} value={module.id}>
                {format.pickTitle({ titleEn: module.titleEn, titleBn: module.titleBn })}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("staffPeople.participation.filters.type")}>
          <Select value={params.type} onChange={(event) => setParams({ type: event.target.value })}>
            <option value="">{t("staffPeople.participation.filters.allTypes")}</option>
            <option value="MCQ">{t("staffPeople.participation.type.MCQ")}</option>
            <option value="WRITTEN">{t("staffPeople.participation.type.WRITTEN")}</option>
          </Select>
        </Field>
        <Field label={t("staffPeople.participation.filters.sort")}>
          <Select value={params.sort} onChange={(event) => setParams({ sort: event.target.value })}>
            <option value="liveAt">{t("staffPeople.participation.sort.date")}</option>
            <option value="title">{t("staffPeople.participation.sort.title")}</option>
            <option value="sitting">{t("staffPeople.participation.sort.sitting")}</option>
          </Select>
        </Field>
        <Field label={t("staffPeople.participation.filters.from")}>
          <TextInput type="date" value={params.from} onChange={(event) => setParams({ from: event.target.value })} />
        </Field>
        <Field label={t("staffPeople.participation.filters.to")}>
          <TextInput type="date" value={params.to} onChange={(event) => setParams({ to: event.target.value })} />
        </Field>
        <Field label={t("staffPeople.participation.filters.text")}>
          <SearchInput id="participation-q" value={params.q} onChange={(q) => setParams({ q })} placeholder={t("staffPeople.participation.filters.textPlaceholder")} />
        </Field>
      </div>

      {rows.isError ? (
        <ErrorState onRetry={() => rows.refetch()} />
      ) : (
        <>
          <Table
            columns={columns}
            rows={shown}
            rowKey={(row) => row.examId}
            loading={rows.isPending}
            empty={
              focus ? (
                <EmptyState title={t("staffPeople.participation.emptyFocus")} body={t("staffPeople.participation.emptyFocusBody")} />
              ) : (
                <EmptyState title={t("staffPeople.participation.empty")} body={t("staffPeople.participation.emptyBody")} />
              )
            }
          />
          {rows.data && !focus ? (
            <Pagination page={rows.data.page} totalPages={rows.data.totalPages} onPage={(page) => setParams({ page })} />
          ) : null}
        </>
      )}
        </>
      )}
    </div>
  );
}
