"use client";

import { useState } from "react";
import {
  useCheckoutEnabled,
  useMyExam,
  usePublicExam,
  usePublicModule,
  type StudentExam,
} from "@/lib/api/exam/catalog";
import { ApiError } from "@/lib/api/errors";
import { useSession } from "@/lib/auth/session-context";
import { PurchaseSheet } from "@/components/student/purchase/purchase-sheet";
import { resolveAccess } from "@/components/student/purchase/access";
import { AccessBadge } from "@/components/ui/purchase/access-badge";
import { ConfigNotice } from "@/components/ui/purchase/config-notice";
import {
  formatDateTime,
  formatDuration,
  formatMoney,
  formatNumber,
  formatTime,
  pickText,
  pickTitle,
} from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { useServerNow } from "@/lib/time/clock";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Countdown } from "@/components/ui/countdown";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { StickyActionBar } from "@/components/ui/sticky-action-bar";
import { doorLabelKey, doorPath, examPath, modulePath, type Door } from "./catalog-helpers";
import { StartConfirm } from "./start-confirm";
import { ExamStatusPill, TypePill } from "./status-pill";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-card border border-border bg-card p-4">
      <h2 className="mb-2 text-card-title font-bold">{title}</h2>
      {children}
    </section>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-control bg-bg p-3 text-center">
      <p className="text-card-title font-bold">{value}</p>
      <p className="text-meta text-muted">{label}</p>
    </div>
  );
}

export function ExamPage({ examId }: { examId: string }) {
  const { t, locale } = useLocale();
  const { session } = useSession();
  const now = useServerNow(30_000);
  const signedIn = session.status === "authenticated";
  const isStudent = signedIn && session.user.role === "STUDENT";
  const phoneRequired = isStudent && Boolean(session.user.phoneRequired);
  const [buying, setBuying] = useState(false);
  const [moduleBuying, setModuleBuying] = useState(false);
  const [starting, setStarting] = useState<"live" | "retake" | null>(null);
  const checkoutOff = useCheckoutEnabled().data === false;

  const { toast } = useToast();
  const exam = usePublicExam(examId, signedIn);
  // Ownership comes from the access object on the exam response. The student's exam read is only
  // asked for once the student owns this exam, so a visitor or a non-owner makes no such request.
  const examAccess = resolveAccess(exam.data?.access);
  const mine = useMyExam(examId, isStudent && examAccess.owned);

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(window.location.href);
      toast({ title: t("catalog.exam.linkCopied"), tone: "ok" });
    } catch {
      toast({ title: t("catalog.exam.linkCopyFailed"), tone: "danger" });
    }
  }
  const data = exam.data;
  const moduleId = data?.moduleId ?? "";
  const moduleQuery = usePublicModule(moduleId, signedIn);

  if (exam.isPending) {
    return (
      <>
        <PageHeader title={t("catalog.loading")} />
        <Skeleton variant="card" count={4} />
      </>
    );
  }
  if (exam.isError && exam.error instanceof ApiError && exam.error.code === "RESOURCE_NOT_FOUND") {
    return (
      <>
        <PageHeader title={t("catalog.notFound")} />
        <EmptyState title={t("catalog.notFound")} body={t("catalog.notFoundBody")} action={<Button variant="secondary" href="/">{t("catalog.home")}</Button>} />
      </>
    );
  }
  if (!data) {
    return (
      <>
        <PageHeader title={t("catalog.loadFailed")} />
        <ErrorState onRetry={() => void exam.refetch()} />
      </>
    );
  }

  const door: Door = data.examType === "MCQ" ? "mcq" : "written";
  const doorLabel = t(doorLabelKey(door));
  const title = pickTitle(data, locale);
  const description = pickText({ ...data }, "description", locale);
  const rules = pickText({ ...data }, "rules", locale);
  const owned: StudentExam | null = mine.data ?? null;
  const ownsExam = examAccess.owned;
  const status = owned?.myStatus ?? null;
  const moduleTitle = moduleQuery.data ? pickTitle(moduleQuery.data, locale) : "";
  const modulePrice = moduleQuery.data?.price ?? null;
  const moduleAccess = resolveAccess(moduleQuery.data?.access);
  const moduleOwned = moduleAccess.owned;
  const crumbs = [
    { label: t("catalog.home"), href: "/" },
    { label: doorLabel, href: doorPath(door) },
    ...(moduleTitle ? [{ label: moduleTitle, href: modulePath(door, moduleId) }] : []),
    { label: title },
  ];

  const startsAt = data.liveAt;
  const endsAt = data.endAt;
  const hasWindow = Boolean(startsAt && endsAt);
  const beforeStart = hasWindow && now < Date.parse(startsAt as string);
  const windowOpen = hasWindow && now >= Date.parse(startsAt as string) && now < Date.parse(endsAt as string);
  const price = data.price;
  const examType = data.examType;
  const retakeOffer = status === "MISSED" && owned?.retakeAvailable === true && examType === "MCQ";
  // The sitting's deadline has passed, but the server has not posted it yet: show the result, not Resume.
  const sittingEnded = Boolean(owned?.sitting && Date.parse(owned.sitting.deadlineAt) <= now);
  const loginHref = `/login?next=${encodeURIComponent(examPath(examId))}`;

  // One primary action, chosen by state.
  let primary: React.ReactNode = null;
  if (!signedIn) {
    primary = price !== null && price !== undefined ? (
      <Button fullWidth href={loginHref}>{t("catalog.exam.buyExam", { price: formatMoney(price, locale) ?? "" })}</Button>
    ) : null;
  } else if (isStudent && !ownsExam) {
    if (phoneRequired || examAccess.blockedReason === "PHONE_REQUIRED") {
      primary = <Button fullWidth href={`/set-phone?next=${encodeURIComponent(examPath(examId))}`}>{t("catalog.exam.addPhone")}</Button>;
    } else if (price !== null && price !== undefined) {
      primary = <Button fullWidth disabled={checkoutOff || examAccess.buyable === false} onClick={() => setBuying(true)}>{t("catalog.exam.buyExam", { price: formatMoney(price, locale) ?? "" })}</Button>;
    }
  } else if (ownsExam) {
    if (status === "NOT_ANNOUNCED") {
      primary = <Button fullWidth disabled>{t("catalog.status.NOT_ANNOUNCED")}</Button>;
    } else if (status === "UPCOMING") {
      primary = (
        <div className="grid gap-2">
          <Button fullWidth disabled>{t("catalog.exam.startExam")}</Button>
          {startsAt ? <p className="text-center text-body text-muted">{t("catalog.exam.startsAt", { when: formatDateTime(startsAt, locale) })}</p> : null}
        </div>
      );
    } else if (status === "OPEN") {
      primary = <Button fullWidth onClick={() => setStarting("live")}>{t("catalog.exam.startExam")}</Button>;
    } else if ((status === "IN_PROGRESS" || (status === "SUBMITTED" && windowOpen)) && !sittingEnded) {
      primary = <Button fullWidth href={`${examPath(examId)}/sit`}>{t("catalog.exam.resume")}</Button>;
    } else if (status === "MISSED") {
      primary = retakeOffer ? (
        <Button fullWidth variant="secondary" onClick={() => setStarting("retake")}>{t("catalog.exam.practice")}</Button>
      ) : (
        <p className="text-center text-muted">{t("catalog.exam.missedBody")}</p>
      );
    } else if (sittingEnded || status === "AWAITING_RESULT" || status === "RESULT_READY" || status === "FAILED_TO_SUBMIT" || status === "SUBMITTED") {
      primary = <Button fullWidth href={`${examPath(examId)}/result`}>{t("catalog.exam.viewResult")}</Button>;
    }
  }

  return (
    <>
      <PageHeader title={title} breadcrumbs={crumbs} />
      <ConfigNotice show={checkoutOff} />
      <div className="-mt-2 mb-3 flex flex-wrap items-center gap-2">
        <TypePill type={examType} />
        {owned?.myStatus ? <ExamStatusPill status={owned.myStatus} examType={examType} retakeAvailable={owned.retakeAvailable} /> : null}
        {examAccess.via ? <AccessBadge via={examAccess.via} /> : null}
      </div>

      <div className="mb-4 grid gap-3">
        {primary ? <StickyActionBar>{primary}</StickyActionBar> : null}
        {isStudent && !ownsExam && price !== null && price !== undefined && moduleTitle && modulePrice !== null && !moduleOwned ? (
          <Button variant="secondary" fullWidth disabled={checkoutOff || moduleAccess.buyable === false} onClick={() => setModuleBuying(true)}>
            {t("catalog.buyModuleWithPrice", { price: formatMoney(modulePrice, locale) ?? "" })}
          </Button>
        ) : null}
        <Button variant="ghost" fullWidth onClick={() => void copyLink()}>
          {t("catalog.exam.copyLink")}
        </Button>
        {owned && beforeStart && startsAt ? (
          <Card>
            <p className="text-body text-muted">{t("catalog.exam.startsIn")}</p>
            <p className="text-card-title font-bold"><Countdown deadline={startsAt} /></p>
          </Card>
        ) : null}
      </div>

      <div className="grid gap-3">
        {description ? (
          <Section title={t("catalog.exam.description")}>
            <p className="whitespace-pre-line text-muted">{description}</p>
          </Section>
        ) : null}
        {rules ? (
          <Section title={t("catalog.exam.rules")}>
            <p className="whitespace-pre-line text-muted">{rules}</p>
          </Section>
        ) : null}
        {examType === "WRITTEN" ? (
          <Section title={t("catalog.exam.paperTitle")}>
            <ul className="grid list-disc gap-1 pl-5 text-muted">
              <li>{t("catalog.exam.paperUnlock")}</li>
              <li>{t("catalog.exam.paperPdf")}</li>
            </ul>
          </Section>
        ) : null}
        <Section title={t("catalog.exam.paperLanguage")}>
          <p className="text-muted">{data.paperLanguage === "EN" ? t("catalog.exam.langEn") : t("catalog.exam.langBn")}</p>
        </Section>
        <div className="grid grid-cols-2 gap-2.5">
          <Fact label={t("catalog.exam.duration")} value={formatDuration(data.durationMinutes, locale)} />
          {owned && owned.maximum ? <Fact label={t("catalog.exam.fullMarks")} value={formatNum(owned.maximum, locale)} /> : null}
        </div>
        <Section title={t("catalog.exam.start")}>
          <p>{startsAt ? formatDateTime(startsAt, locale) : t("catalog.dateTbd")}</p>
          {endsAt ? <p className="text-muted">{t("catalog.exam.end")}: {formatTime(endsAt, locale)}</p> : null}
        </Section>
        {owned && owned.passPercent !== null && owned.passPercent !== undefined ? (
          <Section title={t("catalog.exam.passMark")}>
            <p>{t("catalog.exam.passMarkValue", { p: formatNum(owned.passPercent, locale) })}</p>
          </Section>
        ) : null}
        <Section title={t("catalog.exam.price")}>
          {ownsExam ? (
            <p className="text-ok">✓ {t("catalog.exam.unlockedNote")}</p>
          ) : price !== null && price !== undefined ? (
            <p className="font-bold">{formatMoney(price, locale)}</p>
          ) : (
            <p className="text-muted">{t("catalog.priceNotSet")}</p>
          )}
        </Section>
      </div>

      {isStudent && price !== null && price !== undefined && !ownsExam ? (
        <PurchaseSheet
          open={buying}
          onOpenChange={setBuying}
          item={{ kind: "exam", id: examId, title, price, door }}
          moduleOffer={!moduleOwned && modulePrice !== null && modulePrice !== undefined ? { price: modulePrice } : null}
          onSwitchToModule={() => {
            setBuying(false);
            setModuleBuying(true);
          }}
          examMeta={{ moduleId, door }}
        />
      ) : null}
      {isStudent && modulePrice !== null && modulePrice !== undefined && moduleTitle ? (
        <PurchaseSheet
          open={moduleBuying}
          onOpenChange={setModuleBuying}
          item={{ kind: "module", id: moduleId, title: moduleTitle, price: modulePrice, door }}
        />
      ) : null}
      {owned && starting ? (
        <StartConfirm
          open
          onOpenChange={(next) => {
            if (!next) setStarting(null);
          }}
          mode={starting}
          exam={{
            id: examId,
            title,
            examType,
            durationMinutes: data.durationMinutes,
            endAt: endsAt,
            rules,
          }}
        />
      ) : null}
      <div className="h-28 md:hidden" aria-hidden="true" />
    </>
  );
}

function formatNum(value: number, locale: "bn" | "en") {
  return formatNumber(value, locale);
}
