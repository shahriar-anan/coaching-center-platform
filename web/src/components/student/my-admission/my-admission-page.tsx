"use client";

import { useEffect, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useMutation, type UseQueryResult } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { formatDate, formatMoney, formatNumber, pickTitle } from "@/lib/format";
import { ADMISSION_HOME, isPendingAdmission } from "@/lib/admission/gate";
import { nextDueOf, overdueLineOf, visiblePayments } from "@/lib/admission/my-admission";
import { apiBlob } from "@/lib/api/client";
import { useMyAdmission, type AdmissionView } from "@/lib/api/exam/offline-admission";
import {
  ADMISSION_ROUTINE_PATH,
  downloadAdmissionReceipt,
  useMyAdmissionFees,
  useMyAdmissionItems,
  type AdmissionItem,
  type StudentPayment,
  type StudentSchedule,
} from "@/lib/api/exam/my-admission";
import { examPath, modulePath } from "@/components/student/catalog/catalog-helpers";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/error-state";
import { NotFoundState } from "@/components/ui/not-found-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";

const STATE_TONE: Record<"ONBOARDED" | "CLOSED", PillTone> = { ONBOARDED: "ok", CLOSED: "muted" };

/** The routine is fetched with the caller's auth, like every other student file. */
const loadRoutine = () => apiBlob(ADMISSION_ROUTINE_PATH);

function Loading() {
  const { t } = useLocale();
  return (
    <div className="py-6" aria-busy="true">
      <span className="sr-only">{t("common.states.loading")}</span>
      <Skeleton variant="card" count={3} />
    </div>
  );
}

/** A labelled value: the label above it on a phone, beside it from small width up. */
function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid min-w-0 gap-0.5 sm:grid-cols-[9rem_1fr] sm:gap-3">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="min-w-0 break-words text-body text-text">{children}</dd>
    </div>
  );
}

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="mt-6 grid gap-3">
      <h2 id={id} className="text-card-title font-bold">
        {title}
      </h2>
      {children}
    </section>
  );
}

/** A section with nothing to show yet: the empty text, and nothing else. */
function Waiting({ text }: { text: string }) {
  return (
    <Card>
      <p className="text-body text-muted">{text}</p>
    </Card>
  );
}

/** The batch, the state, and the dates of the admission. The fee is in the payments card, not here. */
function AdmissionHeader({ admission }: { admission: AdmissionView }) {
  const { t, locale } = useLocale();
  const stateKey = admission.state === "CLOSED" ? "CLOSED" : "ONBOARDED";
  const batch = pickTitle({ titleEn: admission.batchNameEn, titleBn: admission.batchNameBn }, locale);
  return (
    <Card padding="lg" className="grid gap-4">
      <div>
        <Pill tone={STATE_TONE[stateKey]}>{t(`myAdmission.state.${stateKey}`)}</Pill>
      </div>
      <dl className="grid gap-3">
        {batch ? <Fact label={t("myAdmission.batch")}>{batch}</Fact> : null}
        {admission.admittedOn ? (
          <Fact label={t("myAdmission.admittedOn")}>
            <span className="tabular-nums">{formatDate(admission.admittedOn, locale, { year: "auto" })}</span>
          </Fact>
        ) : null}
        {admission.courseEndsOn ? (
          <Fact label={t("myAdmission.courseEndsOn")}>
            <span className="tabular-nums">{formatDate(admission.courseEndsOn, locale, { year: "auto" })}</span>
          </Fact>
        ) : null}
      </dl>
    </Card>
  );
}

function ScheduleList({ schedule }: { schedule: StudentSchedule }) {
  const { t, locale } = useLocale();
  return (
    <ol className="grid gap-2">
      {schedule.dues.map((due) => (
        <li key={due.id} className="grid gap-1 rounded-control border border-border px-3 py-2.5">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <span className="font-semibold">{t("myAdmission.payments.installment", { no: due.seq })}</span>
            <span className="text-body tabular-nums text-muted">{formatDate(due.dueOn, locale, { year: "auto" })}</span>
          </div>
          <dl className="flex flex-wrap gap-x-4 gap-y-1 text-body tabular-nums">
            <div className="flex gap-1.5">
              <dt className="text-muted">{t("myAdmission.payments.amount")}</dt>
              <dd>{formatMoney(due.amount, locale)}</dd>
            </div>
            <div className="flex gap-1.5">
              <dt className="text-muted">{t("myAdmission.payments.paid")}</dt>
              <dd>{formatMoney(due.paid, locale)}</dd>
            </div>
            <div className="flex gap-1.5">
              <dt className="text-muted">{t("myAdmission.payments.owed")}</dt>
              <dd>{formatMoney(due.owed, locale)}</dd>
            </div>
          </dl>
        </li>
      ))}
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
    <li className="flex flex-wrap items-center justify-between gap-2 rounded-control border border-border px-3 py-2.5">
      <div className="grid min-w-0 gap-0.5">
        <span className="font-semibold tabular-nums">{formatMoney(payment.amount, locale)}</span>
        <span className="text-meta tabular-nums text-muted">
          {formatDate(payment.paidOn, locale, { year: "auto" })} · {payment.receiptNo}
        </span>
      </div>
      <Button variant="secondary" size="sm" loading={download.isPending} onClick={() => download.mutate()}>
        {t("myAdmission.payments.download")}
      </Button>
    </li>
  );
}

function PaymentsSection({ fees, monthlyFee }: { fees: UseQueryResult<StudentSchedule>; monthlyFee: number | null }) {
  const { t, locale } = useLocale();
  const [downloadFailed, setDownloadFailed] = useState(false);
  if (fees.isPending) {
    return <Skeleton variant="card" />;
  }
  if (fees.isError || !fees.data) {
    return <ErrorState error={fees.error} onRetry={() => void fees.refetch()} />;
  }
  const schedule = fees.data;
  const next = nextDueOf(schedule.dues);
  const overdue = overdueLineOf(schedule);
  const payments = visiblePayments(schedule.payments);
  return (
    <>
      <Card padding="lg" className="grid gap-4">
        {next ? (
          <div className="grid gap-1">
            <span className="text-meta text-muted">{t("myAdmission.payments.next")}</span>
            <span className="text-card-title font-bold tabular-nums">{formatMoney(next.owed, locale)}</span>
            <span className="text-body text-muted">
              {t("myAdmission.payments.nextDue", { date: formatDate(next.dueOn, locale, { year: "auto" }) })}
            </span>
          </div>
        ) : (
          <p className="text-body text-muted">{t("myAdmission.payments.allPaid")}</p>
        )}
        {monthlyFee != null ? (
          <div className="grid gap-1">
            <span className="text-meta text-muted">{t("myAdmission.payments.monthlyFee")}</span>
            <span className="text-body font-semibold tabular-nums">{formatMoney(monthlyFee, locale)}</span>
          </div>
        ) : null}
        {overdue ? (
          <p className="text-body font-semibold text-warn">
            {t("myAdmission.payments.overdue", {
              amount: formatMoney(overdue.amount, locale) ?? "",
              date: formatDate(overdue.dueOn, locale, { year: "auto" }),
            })}
          </p>
        ) : null}
      </Card>

      <div className="grid gap-2">
        <h3 className="text-label font-semibold text-text">{t("myAdmission.payments.schedule")}</h3>
        {schedule.dues.length === 0 ? (
          <p className="text-body text-muted">{t("myAdmission.payments.none")}</p>
        ) : (
          <ScheduleList schedule={schedule} />
        )}
      </div>

      <div className="grid gap-2">
        <h3 className="text-label font-semibold text-text">{t("myAdmission.payments.madeTitle")}</h3>
        {payments.length === 0 ? (
          <p className="text-body text-muted">{t("myAdmission.payments.none")}</p>
        ) : (
          <ul className="grid gap-2">
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
    </>
  );
}

/** One exam or package the student can open: its title, which links to it, and the access end date. */
function ItemRow({ item }: { item: AdmissionItem }) {
  const { t, locale } = useLocale();
  const isExam = item.scope === "EXAM";
  const href = isExam ? (item.examId ? examPath(item.examId) : undefined) : item.moduleId ? modulePath("mcq", item.moduleId) : undefined;
  const title = pickTitle({ titleEn: item.titleEn, titleBn: item.titleBn }, locale);
  const kind = isExam
    ? t("myAdmission.items.exam")
    : [
        t("myAdmission.items.package"),
        item.packageExamCount != null ? t("myAdmission.items.examCount", { n: formatNumber(item.packageExamCount, locale) }) : null,
      ]
        .filter(Boolean)
        .join(" · ");
  return (
    <li>
      <Card href={href} padding="sm">
        <div className="grid min-w-0 gap-0.5">
          <span className="font-semibold break-words">{title}</span>
          <span className="text-meta text-muted">{kind}</span>
          {item.accessEndsAt ? (
            <span className="text-meta tabular-nums text-muted">
              {t("myAdmission.items.accessUntil", { date: formatDate(item.accessEndsAt, locale, { year: "auto" }) })}
            </span>
          ) : null}
        </div>
      </Card>
    </li>
  );
}

/** The exams or the packages of the admission, from the items list (one scope at a time). */
function ItemsSection({
  items,
  scope,
  emptyKey,
}: {
  items: UseQueryResult<AdmissionItem[]>;
  scope: AdmissionItem["scope"];
  emptyKey: "myAdmission.exams.empty" | "myAdmission.packages.empty";
}) {
  const { t } = useLocale();
  if (items.isPending) {
    return <Skeleton variant="card" />;
  }
  if (items.isError || !items.data) {
    return <ErrorState error={items.error} onRetry={() => void items.refetch()} />;
  }
  const rows = items.data.filter((item) => item.scope === scope);
  if (rows.length === 0) {
    return <Waiting text={t(emptyKey)} />;
  }
  return (
    <ul className="grid gap-2">
      {rows.map((item) => (
        <ItemRow key={`${item.scope}-${item.examId ?? item.moduleId}`} item={item} />
      ))}
    </ul>
  );
}

/** The batch routine PDF when the batch has one; otherwise the empty text. */
function RoutineSection({ admission }: { admission: AdmissionView }) {
  const { t, locale } = useLocale();
  if (!admission.hasRoutine) {
    return <Waiting text={t("myAdmission.routine.empty")} />;
  }
  const batch = pickTitle({ titleEn: admission.batchNameEn, titleBn: admission.batchNameBn }, locale);
  return (
    <PdfViewer
      load={loadRoutine}
      allowDownload
      fileName={`${batch || t("myAdmission.routine.title")}.pdf`}
      label={t("myAdmission.routine.title")}
    />
  );
}

function FormSummary({ admission }: { admission: AdmissionView }) {
  const { t, locale } = useLocale();
  const form = admission.form;
  return (
    <Card padding="lg" className="grid gap-4">
      <dl className="grid gap-3">
        <Fact label={t("myAdmission.form.name")}>{form.fullName || "—"}</Fact>
        <Fact label={t("myAdmission.form.mobile")}>
          <span className="tabular-nums">{form.mobile || "—"}</span>
        </Fact>
        <Fact label={t("myAdmission.form.university")}>{form.university || "—"}</Fact>
        <Fact label={t("myAdmission.form.semester")}>{form.semesterBatch || "—"}</Fact>
      </dl>
      <p className="text-meta text-muted">
        {admission.submittedAt
          ? t("myAdmission.form.submitted", { date: formatDate(admission.submittedAt, locale, { year: "auto" }) })
          : t("myAdmission.form.notSubmitted")}
      </p>
      <Button variant="secondary" href="/admission/print" fullWidth>
        {t("myAdmission.form.view")}
      </Button>
    </Card>
  );
}

/**
 * `/my-admission` (plan 6.4): the student's offline admission as read-only history, in plan order: header, payments,
 * exams, packages, routine, my form. A closed admission shows the same history with no payment actions. A student with no
 * admission gets the not-found page. A pending admission is sent to the form (plan 4.5).
 */
export function MyAdmissionPage() {
  const { t } = useLocale();
  const router = useRouter();
  const admission = useMyAdmission();
  const data = admission.data;
  const pending = data != null && isPendingAdmission(data.state);
  const open = data != null && !pending;
  const fees = useMyAdmissionFees({ enabled: open });
  const items = useMyAdmissionItems({ enabled: open });

  useEffect(() => {
    if (pending) {
      router.replace(ADMISSION_HOME);
    }
  }, [pending, router]);

  if (admission.isPending) {
    return <Loading />;
  }
  if (admission.isError) {
    return <ErrorState onRetry={() => void admission.refetch()} error={admission.error} />;
  }
  if (!data) {
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

  const crumbs = [{ label: t("catalog.home"), href: "/" }, { label: t("myAdmission.title") }];
  return (
    <>
      <PageHeader title={t("myAdmission.title")} breadcrumbs={crumbs} />

      <AdmissionHeader admission={data} />

      <Section id="my-admission-payments" title={t("myAdmission.payments.title")}>
        <PaymentsSection fees={fees} monthlyFee={data.monthlyFee ?? null} />
      </Section>

      <Section id="my-admission-exams" title={t("myAdmission.exams.title")}>
        <ItemsSection items={items} scope="EXAM" emptyKey="myAdmission.exams.empty" />
      </Section>

      <Section id="my-admission-packages" title={t("myAdmission.packages.title")}>
        <ItemsSection items={items} scope="MODULE" emptyKey="myAdmission.packages.empty" />
      </Section>

      <Section id="my-admission-routine" title={t("myAdmission.routine.title")}>
        <RoutineSection admission={data} />
      </Section>

      <Section id="my-admission-form" title={t("myAdmission.form.title")}>
        <FormSummary admission={data} />
      </Section>
    </>
  );
}
