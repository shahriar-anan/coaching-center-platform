"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, type UseQueryResult } from "@tanstack/react-query";
import { BookOpen, Download, FileText, Package, Printer, Wallet } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { formatDate, formatMoney, formatNumber, pickTitle } from "@/lib/format";
import { serverNow, useServerNow } from "@/lib/time/clock";
import { ADMISSION_HOME, isPendingAdmission } from "@/lib/admission/gate";
import { nextDueOf, visiblePayments } from "@/lib/admission/my-admission";
import { useMyAdmission, type AdmissionView } from "@/lib/api/exam/offline-admission";
import { useMyAdmissionResults } from "@/lib/api/exam/my-results";
import { BatchOffers } from "@/components/public-admission/batch-offers";
import {
  downloadAdmissionReceipt,
  useMyAdmissionFees,
  useMyAdmissionItems,
  useMyAdmissionRoutine,
  useMyAdmissions,
  type AdmissionItem,
  type StudentDue,
  type StudentPayment,
  type StudentSchedule,
} from "@/lib/api/exam/my-admission";
import { examPath, modulePath } from "@/components/student/catalog/catalog-helpers";
import { AdmissionCard, GlanceStrip, OverdueAlert } from "@/components/student/my-admission/admission-overview";
import { RoutineSection, monthOf, nextClassOf, shiftMonth } from "@/components/student/my-admission/routine-section";
import { ResultsSection } from "@/components/student/my-admission/results-section";
import { IconTile, SectionCard, SectionEmpty } from "@/components/student/my-admission/section-card";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { ErrorState } from "@/components/ui/error-state";
import { NotFoundState } from "@/components/ui/not-found-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";

function Loading() {
  const { t } = useLocale();
  return (
    <div className="py-6" aria-busy="true">
      <span className="sr-only">{t("common.states.loading")}</span>
      <Skeleton variant="card" count={3} />
    </div>
  );
}

/** A small heading inside a section card, over one of its lists. */
function SubHeading({ children }: { children: string }) {
  return <h3 className="text-label font-bold text-text">{children}</h3>;
}

/** One figure of the payments summary, the mockup's `.stat` cell: the label, then the amount. */
function StatCell({ label, value, tone, className }: { label: string; value: string; tone?: "ok" | "warn"; className?: string }) {
  return (
    <div className={cn("grid min-w-0 content-start gap-0.5 rounded-control bg-bg px-2.5 py-2.5 text-center", className)}>
      <dt className="text-meta text-muted">{label}</dt>
      <dd
        className={cn(
          // A sum never breaks between its digits: the tiles make room for it instead.
          "whitespace-nowrap text-label font-bold tabular-nums",
          tone === "ok" ? "text-ok-text" : tone === "warn" ? "text-warn" : "text-text",
        )}
      >
        {value}
      </dd>
    </div>
  );
}

type DueStatus = "PAID" | "PART_PAID" | "UPCOMING" | "OVERDUE" | "NEXT";

const DUE_TONE: Record<DueStatus, PillTone> = {
  PAID: "ok",
  PART_PAID: "accent",
  UPCOMING: "muted",
  OVERDUE: "warn",
  NEXT: "accent",
};

/** The word a due shows: paid, overdue, the next one to pay, part paid, or still to come. */
function dueStatus(due: StudentDue, next: StudentDue | null): DueStatus {
  if (due.owed <= 0) return "PAID";
  if (due.overdue) return "OVERDUE";
  if (next && due.id === next.id) return "NEXT";
  if (due.paid > 0) return "PART_PAID";
  return "UPCOMING";
}

/** The instalments as one divided list: the number and the date on the left, the amount and its status on the right. */
function ScheduleList({ schedule }: { schedule: StudentSchedule }) {
  const { t, locale } = useLocale();
  const next = nextDueOf(schedule.dues);
  const dues = [...schedule.dues].sort((a, b) => a.seq - b.seq);
  return (
    <ol className="divide-y divide-border rounded-control border border-border">
      {dues.map((due) => {
        const status = dueStatus(due, next);
        const partly = due.owed > 0 && due.paid > 0;
        return (
          <li key={due.id} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1.5 px-3 py-2.5">
            <div className="grid min-w-0 gap-0.5">
              <span className="font-semibold text-text">{t("myAdmission.payments.installment", { no: formatNumber(due.seq, locale) })}</span>
              <span className="text-meta tabular-nums text-muted">
                {t("myAdmission.payments.dueOnShort", { date: formatDate(due.dueOn, locale, { year: "auto" }) })}
              </span>
            </div>
            <div className="grid justify-items-end gap-1 text-end">
              <div className="flex flex-wrap items-center justify-end gap-2">
                <span className="font-semibold tabular-nums text-text">{formatMoney(due.amount, locale)}</span>
                <Pill tone={DUE_TONE[status]}>{t(`myAdmission.payments.status.${status}`)}</Pill>
              </div>
              {partly ? (
                <span className="text-meta tabular-nums text-muted">
                  {t("myAdmission.payments.left", { amount: formatMoney(due.owed, locale) ?? "" })}
                </span>
              ) : null}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

function PaymentRow({ payment, onFailed }: { payment: StudentPayment; onFailed: () => void }) {
  const { t, locale } = useLocale();
  const download = useMutation({
    mutationFn: () => downloadAdmissionReceipt(payment.id, payment.receiptNo),
    onError: onFailed,
  });
  return (
    <li className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 px-3 py-2.5">
      <div className="grid min-w-0 gap-0.5">
        <span className="font-semibold tabular-nums text-text">{formatMoney(payment.amount, locale)}</span>
        <span className="text-meta tabular-nums text-muted [overflow-wrap:anywhere]">
          {formatDate(payment.paidOn, locale, { year: "auto" })} · {payment.receiptNo}
        </span>
      </div>
      <Button variant="secondary" size="sm" loading={download.isPending} onClick={() => download.mutate()}>
        <Download aria-hidden="true" className="size-4" />
        {t("myAdmission.payments.download")}
      </Button>
    </li>
  );
}

/**
 * The payments card: how much of the whole fee is paid (a bar and three figures), the instalments with a status each, then the
 * approved payments with their receipts. The next payment and any overdue amount are already at the top of the page.
 */
function PaymentsSection({ fees, monthlyFee }: { fees: UseQueryResult<StudentSchedule>; monthlyFee: number | null }) {
  const { t, locale } = useLocale();
  const [downloadFailed, setDownloadFailed] = useState(false);

  let body;
  if (fees.isPending) {
    body = <Skeleton variant="card" />;
  } else if (fees.isError || !fees.data) {
    body = <ErrorState error={fees.error} onRetry={() => void fees.refetch()} />;
  } else {
    const schedule = fees.data;
    const total = schedule.dues.reduce((sum, due) => sum + due.amount, 0);
    const paid = schedule.dues.reduce((sum, due) => sum + due.paid, 0);
    const percent = total > 0 ? Math.min(100, Math.round((paid / total) * 100)) : 0;
    const payments = visiblePayments(schedule.payments);
    body = (
      <div className="grid gap-5">
        {total > 0 ? (
          <div className="grid gap-2">
            <div className="flex flex-wrap items-baseline justify-between gap-2 text-meta">
              <span className="text-muted">
                {t("myAdmission.payments.paidOf", {
                  paid: formatMoney(paid, locale) ?? "",
                  total: formatMoney(total, locale) ?? "",
                })}
              </span>
              <span className="font-semibold tabular-nums text-text">{formatNumber(percent, locale)}%</span>
            </div>
            <div
              role="progressbar"
              aria-label={t("myAdmission.payments.progress")}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={percent}
              className="h-2 overflow-hidden rounded-full bg-bg"
            >
              <div className="h-full rounded-full bg-ok" style={{ width: `${percent}%` }} />
            </div>
          </div>
        ) : null}

        {/* Two tiles a row on a phone (the monthly fee then takes a row of its own), three from sm up. */}
        <dl className={cn("grid gap-2", monthlyFee != null ? "grid-cols-2 sm:grid-cols-3" : "grid-cols-2")}>
          <StatCell label={t("myAdmission.payments.paidSoFar")} value={formatMoney(paid, locale) ?? "—"} tone="ok" />
          <StatCell
            label={t("myAdmission.payments.stillOwed")}
            value={formatMoney(schedule.owed, locale) ?? "—"}
            tone={schedule.overdue > 0 ? "warn" : undefined}
          />
          {monthlyFee != null ? (
            <StatCell
              label={t("myAdmission.payments.monthlyFee")}
              value={formatMoney(monthlyFee, locale) ?? "—"}
              className="col-span-2 sm:col-span-1"
            />
          ) : null}
        </dl>

        <div className="grid gap-2">
          <SubHeading>{t("myAdmission.payments.schedule")}</SubHeading>
          {schedule.dues.length === 0 ? (
            <SectionEmpty>{t("myAdmission.payments.noSchedule")}</SectionEmpty>
          ) : (
            <ScheduleList schedule={schedule} />
          )}
        </div>

        <div className="grid gap-2">
          <SubHeading>{t("myAdmission.payments.madeTitle")}</SubHeading>
          {payments.length === 0 ? (
            <SectionEmpty>{t("myAdmission.payments.none")}</SectionEmpty>
          ) : (
            <ul className="divide-y divide-border rounded-control border border-border">
              {payments.map((payment) => (
                <PaymentRow key={payment.id} payment={payment} onFailed={() => setDownloadFailed(true)} />
              ))}
            </ul>
          )}
          {downloadFailed ? (
            <p role="alert" className="text-body text-danger">
              {t("myAdmission.payments.downloadFailed")}
            </p>
          ) : null}
        </div>
      </div>
    );
  }

  return (
    <SectionCard id="my-admission-payments" icon={Wallet} tint="payments" title={t("myAdmission.payments.title")}>
      {body}
    </SectionCard>
  );
}

/** One exam or package: a type icon, the title (a link to it), the type, the package's exam count and the access end date. */
function ItemRow({ item }: { item: AdmissionItem }) {
  const { t, locale } = useLocale();
  const isExam = item.scope === "EXAM";
  const href = isExam ? (item.examId ? examPath(item.examId) : undefined) : item.moduleId ? modulePath("mcq", item.moduleId) : undefined;
  const title = pickTitle({ titleEn: item.titleEn, titleBn: item.titleBn }, locale);
  const content = (
    <>
      <IconTile icon={isExam ? FileText : Package} tint={isExam ? "form" : "items"} size="sm" />
      <span className="grid min-w-0 flex-1 gap-0.5">
        <span className="break-words font-semibold text-text">{title}</span>
        <span className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-meta text-muted">
          <span>{isExam ? t("myAdmission.items.exam") : t("myAdmission.items.package")}</span>
          {!isExam && item.packageExamCount != null ? (
            <>
              <span aria-hidden="true">·</span>
              <span>{t("myAdmission.items.examCount", { n: formatNumber(item.packageExamCount, locale) })}</span>
            </>
          ) : null}
          {item.accessEndsAt ? (
            <>
              <span aria-hidden="true">·</span>
              <span className="tabular-nums">
                {t("myAdmission.items.accessUntil", { date: formatDate(item.accessEndsAt, locale, { year: "auto" }) })}
              </span>
            </>
          ) : null}
        </span>
      </span>
    </>
  );
  const row = "flex min-h-14 items-center gap-3 px-4 py-3 sm:px-5";
  return (
    <li>
      {href ? (
        <Link
          href={href}
          prefetch
          className={cn(row, "transition-colors hover:bg-text/[0.04] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent")}
        >
          {content}
        </Link>
      ) : (
        <div className={row}>{content}</div>
      )}
    </li>
  );
}

/** The exams and packages of the admission as one list, the packages first since they hold the most. */
function ItemsSection({ items }: { items: UseQueryResult<AdmissionItem[]> }) {
  const { t } = useLocale();
  let body;
  if (items.isPending) {
    body = (
      <div className="p-4 sm:p-5">
        <Skeleton variant="card" />
      </div>
    );
  } else if (items.isError || !items.data) {
    body = (
      <div className="p-4 sm:p-5">
        <ErrorState error={items.error} onRetry={() => void items.refetch()} />
      </div>
    );
  } else if (items.data.length === 0) {
    body = (
      <div className="p-4 sm:p-5">
        <SectionEmpty>{t("myAdmission.items.empty")}</SectionEmpty>
      </div>
    );
  } else {
    const rows = [...items.data].sort((a, b) => (a.scope === b.scope ? 0 : a.scope === "MODULE" ? -1 : 1));
    body = (
      <ul className="divide-y divide-border">
        {rows.map((item) => (
          <ItemRow key={`${item.scope}-${item.examId ?? item.moduleId}`} item={item} />
        ))}
      </ul>
    );
  }
  return (
    <SectionCard id="my-admission-items" icon={BookOpen} tint="items" title={t("myAdmission.items.title")} flush>
      {body}
    </SectionCard>
  );
}

/** The form as submitted: four facts in two columns from small width up, when it was sent, and the printable copy. */
function FormSection({ admission }: { admission: AdmissionView }) {
  const { t, locale } = useLocale();
  const form = admission.form;
  const facts: [string, string | null | undefined, boolean?][] = [
    [t("myAdmission.form.name"), form.fullName],
    [t("myAdmission.form.mobile"), form.mobile, true],
    [t("myAdmission.form.university"), form.university],
    [t("myAdmission.form.semester"), form.semesterBatch],
  ];
  return (
    <SectionCard
      id="my-admission-form"
      icon={FileText}
      tint="form"
      title={t("myAdmission.form.title")}
      action={
        <Button variant="secondary" size="sm" href="/admission/print">
          <Printer aria-hidden="true" className="size-4" />
          {t("myAdmission.form.view")}
        </Button>
      }
    >
      <div className="grid gap-4">
        <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
          {facts.map(([label, value, numeric]) => (
            <div key={label} className="grid min-w-0 gap-0.5">
              <dt className="text-meta text-muted">{label}</dt>
              <dd className={cn("min-w-0 break-words text-body text-text", numeric && "tabular-nums")}>{value || "—"}</dd>
            </div>
          ))}
        </dl>
        <p className="text-meta text-muted">
          {admission.submittedAt
            ? t("myAdmission.form.submitted", { date: formatDate(admission.submittedAt, locale, { year: "auto" }) })
            : t("myAdmission.form.notSubmitted")}
        </p>
      </div>
    </SectionCard>
  );
}

/**
 * The sections of one admission: the admission card with the course timeline, the overdue warning, a glance strip, then the routine,
 * payments, exams and packages, and the form. `admissionId` is set only when the student has several admissions (plan P11); with one,
 * the routes answer the newest admission, as before.
 */
function AdmissionBlock({ admission, admissionId }: { admission: AdmissionView; admissionId?: string }) {
  const open = !isPendingAdmission(admission.state);
  const active = open && admission.state !== "CLOSED";
  const fees = useMyAdmissionFees({ enabled: open, admissionId });
  const items = useMyAdmissionItems({ enabled: open, admissionId });

  // The next class for the glance strip: this month's routine (the same request the routine section makes), then next month's
  // when nothing is left this month.
  const now = useServerNow(60_000);
  const [month] = useState(() => monthOf(serverNow()));
  const withRoutine = active && admission.hasRoutine === true;
  const thisMonth = useMyAdmissionRoutine(month, { enabled: withRoutine, admissionId });
  const nextThisMonth = thisMonth.data ? nextClassOf(thisMonth.data, now) : null;
  const askNextMonth = withRoutine && thisMonth.isSuccess && !nextThisMonth;
  const nextMonth = useMyAdmissionRoutine(shiftMonth(month, 1), { enabled: askNextMonth, admissionId });
  const nextClass = nextThisMonth ?? (askNextMonth && nextMonth.data ? nextClassOf(nextMonth.data, now) : null);
  const classesLoading = withRoutine && (thisMonth.isPending || (askNextMonth && nextMonth.isPending));

  // The published results: read for an onboarded or closed admission, for the results section and the glance strip.
  const results = useMyAdmissionResults(admission.id, open);

  return (
    <>
      <AdmissionCard admission={admission} now={now} />
      <OverdueAlert schedule={fees.data} />
      <GlanceStrip admission={admission} nextClass={nextClass} classesLoading={classesLoading} fees={fees} items={items} results={results} now={now} />

      <RoutineSection admission={admission} admissionId={admissionId} />
      {open ? <ResultsSection admissionId={admission.id} results={results} /> : null}
      <PaymentsSection fees={fees} monthlyFee={admission.monthlyFee ?? null} />
      <ItemsSection items={items} />
      <FormSection admission={admission} />
    </>
  );
}

/** A batch the student has applied to that is not onboarded yet: its name and where it stands. Shown beside the open ones. */
function PendingBatchCard({ admission }: { admission: AdmissionView }) {
  const { t, locale } = useLocale();
  const batch = pickTitle({ titleEn: admission.batchNameEn, titleBn: admission.batchNameBn }, locale) || t("myAdmission.batchUnnamed");
  const note =
    admission.state === "CHANGES_REQUESTED"
      ? t("myAdmission.pending.changes")
      : admission.state === "AWAITING_FORM"
        ? t("myAdmission.pending.form")
        : t("myAdmission.pending.review");
  return (
    <section aria-label={batch} className="grid gap-1 rounded-card border border-border bg-card p-4">
      <p className="text-card-title font-bold text-text">{batch}</p>
      <p className="text-body text-muted">{note}</p>
    </section>
  );
}

/**
 * The admissions as sections (plan P11, P15): one block for a single batch; with several, one section per batch under its name. A batch
 * still under review is a card. Each open batch reads its own fees, exams and packages, and routine.
 */
export function AdmissionSections({ admissions }: { admissions: AdmissionView[] }) {
  const { t, locale } = useLocale();
  if (admissions.length === 1) {
    const only = admissions[0];
    return isPendingAdmission(only.state) ? <PendingBatchCard admission={only} /> : <AdmissionBlock admission={only} />;
  }
  return (
    <>
      {admissions.map((item) => {
        const name = pickTitle({ titleEn: item.batchNameEn, titleBn: item.batchNameBn }, locale) || t("myAdmission.batchUnnamed");
        if (isPendingAdmission(item.state)) {
          return <PendingBatchCard key={item.id} admission={item} />;
        }
        return (
          <section key={item.id} aria-label={name} className="mt-8 grid gap-6 first:mt-0">
            <h2 className="text-section font-bold tracking-tight text-text">{name}</h2>
            <AdmissionBlock admission={item} admissionId={item.id} />
          </section>
        );
      })}
    </>
  );
}

/**
 * The page of a student's offline life (plan 6.4, P15). Here it is `/offline-activities`; `title` and `offers` set it up for that page.
 * The admission card, the glance strip and the sections come first, in the order a student uses them: routine, payments, exams and
 * packages, the form. A closed admission shows the same history with no next class. `offers` adds the published batches the student is
 * not already in, and with no admission at all shows only those. Without `offers`, a student with no admission gets the not-found page.
 * A student with nothing open (every application still under review) is sent to the form (plan 4.5).
 */
export function MyAdmissionPage({ title, offers = false }: { title?: string; offers?: boolean } = {}) {
  const { t } = useLocale();
  const router = useRouter();
  const admission = useMyAdmission();
  const data = admission.data;
  const list = useMyAdmissions({ enabled: data != null });
  // With several admissions the list has them all; if it cannot be read, the newest one stands alone.
  const admissions = list.data && list.data.length > 0 ? list.data : data ? [data] : [];
  const openAdmissions = admissions.filter((item) => !isPendingAdmission(item.state));
  const pending = data != null && openAdmissions.length === 0;
  const pageTitle = title ?? t("myAdmission.title");

  useEffect(() => {
    if (pending) {
      router.replace(ADMISSION_HOME);
    }
  }, [pending, router]);

  if (admission.isPending || (data != null && list.isPending)) {
    return <Loading />;
  }
  if (admission.isError) {
    return <ErrorState onRetry={() => void admission.refetch()} error={admission.error} />;
  }
  const crumbs = [{ label: t("catalog.home"), href: "/" }, { label: pageTitle }];
  if (!data) {
    if (offers) {
      return (
        <>
          <PageHeader title={pageTitle} breadcrumbs={crumbs} />
          <BatchOffers excludeBatchIds={[]} />
        </>
      );
    }
    return (
      <NotFoundState
        title={t("common.notFound.title")}
        body={t("common.notFound.body")}
        backHref="/"
        backLabel={t("common.guard.goHome")}
      />
    );
  }
  if (pending) {
    return null;
  }

  return (
    <>
      <PageHeader title={pageTitle} breadcrumbs={crumbs} />
      <AdmissionSections admissions={admissions} />
      {offers ? <BatchOffers excludeBatchIds={admissions.map((item) => item.batchId).filter((id): id is string => Boolean(id))} /> : null}
    </>
  );
}
