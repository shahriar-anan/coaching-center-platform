"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  useCheckoutEnabled,
  useMyExam,
  usePublicExam,
  usePublicModulesById,
  type StudentExam,
} from "@/lib/api/exam/catalog";
import { ApiError } from "@/lib/api/errors";
import { useSession } from "@/lib/auth/session-context";
import { PurchaseSheet } from "@/components/student/purchase/purchase-sheet";
import { OpenOrderLine, PurchasedLine, useBoughtGrant } from "@/components/student/purchase/order-lines";
import { resolveAccess } from "@/components/student/purchase/access";
import { AccessBadge } from "@/components/ui/purchase/access-badge";
import { ConfigNotice } from "@/components/ui/purchase/config-notice";
import { formatDate, formatDateTime, formatDuration, formatMoney, formatNumber, formatTime, pickText, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { useServerNow } from "@/lib/time/clock";
import { cn } from "@/components/ui/cn";
import { Breadcrumbs } from "@/components/ui/breadcrumbs";
import { Button } from "@/components/ui/button";
import { Countdown } from "@/components/ui/countdown";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { PackageCover, coverSrc } from "@/components/ui/package/package-cover";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { useNotice } from "@/lib/notices/use-notice";
import { ActionBar, DetailLayout, barActionOf, detailFrame } from "./detail-layout";
import { doorLabelKey, doorPath, examPath, type Door } from "./catalog-helpers";
import { ExamPackages } from "./exam-packages";
import { FavouriteButton } from "./favourite-button";
import { PriceLine } from "./price-line";
import { chargedAmount, priceLabel, pricingOf } from "./price-view";
import { ruleLines } from "./lms-blocks";
import { StartConfirm } from "./start-confirm";
import { ExamStatusPill, TypePill } from "./status-pill";

function Fact({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="grid content-start gap-0.5">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="text-label font-bold tabular-nums">{value}</dd>
      {sub ? <dd className="text-meta text-muted">{sub}</dd> : null}
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="grid gap-3">
      <h2 className="text-section font-bold">{title}</h2>
      {children}
    </section>
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
  const [starting, setStarting] = useState<"live" | "retake" | null>(null);
  const [actionOffscreen, setActionOffscreen] = useState(false);
  const actionRef = useRef<HTMLElement | null>(null);
  const checkoutOff = useCheckoutEnabled().data === false;

  const { toast } = useToast();
  useNotice();
  const exam = usePublicExam(examId, signedIn);
  // Ownership comes from the access object on the exam response. The student's exam read is only
  // asked for once the student owns this exam, so a visitor or a non-owner makes no such request.
  const examAccess = resolveAccess(exam.data?.access);
  const mine = useMyExam(examId, isStudent && examAccess.owned);
  // The purchase of this exam, when the student bought it (S4.6): its date and a link to the payment.
  const bought = useBoughtGrant({ examId }, isStudent && examAccess.owned);

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(window.location.href);
      toast({ title: t("catalog.exam.linkCopied"), tone: "ok" });
    } catch {
      toast({ title: t("catalog.exam.linkCopyFailed"), tone: "danger" });
    }
  }
  const data = exam.data;
  // Every package the exam is in, read once for the cards and the cover (NR-01, S2.4).
  const packageRefs = data?.packages ?? [];
  const packageReads = usePublicModulesById(packageRefs.map((pkg) => pkg.id));

  // The sticky bar appears once the primary action has scrolled out of view. Only a phone needs it.
  const ready = Boolean(exam.data);
  useEffect(() => {
    const element = actionRef.current;
    if (!ready || !element || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(([entry]) => setActionOffscreen(!entry.isIntersecting), { threshold: 0 });
    observer.observe(element);
    return () => observer.disconnect();
  }, [ready]);

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
  const ruleItems = rules ? ruleLines(rules) : [];
  const owned: StudentExam | null = mine.data ?? null;
  const ownsExam = examAccess.owned;
  const status = owned?.myStatus ?? null;
  const pricing = pricingOf(data.pricing, data.price);
  const label = priceLabel(pricing);
  // The student can buy the exam itself only when it has a price of its own (plan Q12).
  const ownPrice = label.kind === "price" || label.kind === "discounted";
  const charged = chargedAmount(pricing, data.price);
  const expiresAt = data.access?.expiresAt ?? null;
  const endedAccess = isStudent && !ownsExam && Boolean(expiresAt);
  // The cover: the exam's own, then its first package's, then the typed placeholder (Phase 4, E1 and S4.4).
  const coverPackage = packageReads[0]?.data;
  const crumbs = [
    { label: t("catalog.home"), href: "/" },
    { label: doorLabel, href: doorPath(door) },
    { label: title },
  ];

  const startsAt = data.liveAt;
  const endsAt = data.endAt;
  const hasWindow = Boolean(startsAt && endsAt);
  const beforeStart = hasWindow && now < Date.parse(startsAt as string);
  const windowOpen = hasWindow && now >= Date.parse(startsAt as string) && now < Date.parse(endsAt as string);
  const examType = data.examType;
  const retakeOffer = status === "MISSED" && owned?.retakeAvailable === true && examType === "MCQ";
  // The sitting's deadline has passed, but the server has not posted it yet: show the result, not Resume.
  const sittingEnded = Boolean(owned?.sitting && Date.parse(owned.sitting.deadlineAt) <= now);
  const loginHref = `/login?next=${encodeURIComponent(examPath(examId))}`;
  const buyLabel = t("catalog.exam.buyExam", { price: formatMoney(charged, locale) ?? "" });

  // One primary action, chosen by state.
  let primary: ReactNode = null;
  if (!signedIn) {
    if (label.kind === "free") {
      primary = <Button fullWidth href={loginHref}>{t("catalog.exam.freeLogin")}</Button>;
    } else if (ownPrice) {
      primary = <Button fullWidth href={loginHref}>{buyLabel}</Button>;
    }
  } else if (isStudent && !ownsExam) {
    if (phoneRequired || examAccess.blockedReason === "PHONE_REQUIRED") {
      primary = <Button fullWidth href={`/set-phone?next=${encodeURIComponent(examPath(examId))}`}>{t("catalog.exam.addPhone")}</Button>;
    } else if (ownPrice) {
      primary = <Button fullWidth disabled={checkoutOff || examAccess.buyable === false} onClick={() => setBuying(true)}>{buyLabel}</Button>;
    }
  } else if (ownsExam) {
    if (status === "NOT_ANNOUNCED") {
      primary = <Button fullWidth disabled>{t("catalog.status.NOT_ANNOUNCED")}</Button>;
    } else if (status === "UPCOMING") {
      primary = <Button fullWidth disabled>{t("catalog.exam.startExam")}</Button>;
    } else if (status === "OPEN") {
      primary = <Button fullWidth onClick={() => setStarting("live")}>{examAccess.via === "FREE" ? t("catalog.exam.freeStart") : t("catalog.exam.startExam")}</Button>;
    } else if ((status === "IN_PROGRESS" || (status === "SUBMITTED" && windowOpen)) && !sittingEnded) {
      primary = <Button fullWidth href={`${examPath(examId)}/sit`}>{t(status === "SUBMITTED" ? "catalog.exam.reviewAnswers" : "catalog.exam.resume")}</Button>;
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
  // The bar repeats the card's action, and only when that action can be taken now (see barActionOf).
  const barAction = barActionOf(primary);
  const showBar = actionOffscreen && barAction !== null;

  // The timing line under the title: a countdown for an owner before the start, otherwise the start time.
  const timingLine =
    owned && beforeStart && startsAt ? (
      <>
        {t("catalog.exam.startsIn")} <Countdown deadline={startsAt} />
      </>
    ) : startsAt ? (
      t("catalog.exam.startsAt", { when: formatDateTime(startsAt, locale) })
    ) : (
      t("catalog.dateTbd")
    );

  return (
    <div className={cn(detailFrame, barAction !== null && "pb-24 md:pb-0")}>
      <Breadcrumbs items={crumbs} />
      <ConfigNotice show={checkoutOff} />

      <DetailLayout
        header={
          <header className="relative grid gap-2 pe-12">
            <div className="flex flex-wrap items-center gap-2">
              <TypePill type={examType} />
              {data.popular ? <Pill tone="accent">{t("catalog.popular.badge")}</Pill> : null}
              {owned?.myStatus ? <ExamStatusPill status={owned.myStatus} examType={examType} retakeAvailable={owned.retakeAvailable} /> : null}
              {examAccess.via ? <AccessBadge via={examAccess.via} /> : null}
            </div>
            <h1 className="text-2xl font-extrabold leading-tight tracking-tight md:text-3xl">{title}</h1>
            <p className="text-body text-muted">{timingLine}</p>
            {status === "SUBMITTED" && owned?.sitting?.submittedAt ? (
              <p className="text-body font-semibold text-ok">
                {t("catalog.exam.submittedLine", { time: formatTime(owned.sitting.submittedAt, locale), deadline: formatTime(owned.sitting.deadlineAt, locale) })}
              </p>
            ) : null}
            <FavouriteButton kind="exam" id={examId} initial={data.favourite} next={examPath(examId)} />
          </header>
        }
        card={
          <section ref={actionRef} aria-label={title} className="grid gap-4 rounded-card border border-border bg-card p-5">
            <div className="grid gap-1">
              <span className="text-meta font-semibold uppercase tracking-wide text-muted">{t("catalog.exam.price")}</span>
              <PriceLine label={label} size="detail" />
            </div>
            {ownsExam && expiresAt ? (
              <p className="text-body font-semibold text-muted">
                {t("catalog.exam.accessUntil", { date: formatDate(expiresAt, locale, { year: "auto" }) })}
              </p>
            ) : null}
            {endedAccess && expiresAt ? (
              <p className="text-body font-semibold text-warn">
                {t("catalog.exam.accessEnded", { date: formatDate(expiresAt, locale, { year: "auto" }) })}
              </p>
            ) : null}
            {ownsExam && bought ? <PurchasedLine grant={bought} /> : null}
            {primary}
            {isStudent && !ownsExam ? <OpenOrderLine filter={{ examId }} enabled /> : null}
            <Button variant="ghost" fullWidth onClick={() => void copyLink()}>
              {t("catalog.exam.copyLink")}
            </Button>
          </section>
        }
        cover={
          <PackageCover
            band
            src={coverSrc(data.coverUrl ?? coverPackage?.coverUrl)}
            alt={t("catalog.package.coverAlt", { title: pickTitle(coverPackage ?? data, locale) || title })}
            type={examType === "MCQ" ? "MCQ" : "WRITTEN"}
            title={pickTitle(coverPackage ?? data, locale) || title}
            className="rounded-card"
          />
        }
      >
        <dl className="grid grid-cols-2 gap-x-4 gap-y-4 rounded-card border border-border bg-card p-5 md:grid-cols-3">
          <Fact label={t("catalog.exam.duration")} value={formatDuration(data.durationMinutes, locale)} />
          <Fact
            label={t("catalog.exam.start")}
            value={startsAt ? formatDateTime(startsAt, locale) : t("catalog.dateTbd")}
            sub={endsAt ? `${t("catalog.exam.end")}: ${formatTime(endsAt, locale)}` : undefined}
          />
          <Fact label={t("catalog.exam.paperLanguage")} value={data.paperLanguage === "EN" ? t("catalog.exam.langEn") : t("catalog.exam.langBn")} />
          {owned && owned.maximum ? <Fact label={t("catalog.exam.fullMarks")} value={formatNumber(owned.maximum, locale)} /> : null}
        </dl>

        {description ? (
          <Section title={t("catalog.exam.description")}>
            <p className="whitespace-pre-line leading-relaxed text-muted lg:max-w-[70ch]">{description}</p>
          </Section>
        ) : null}
        {ruleItems.length > 0 ? (
          <Section title={t("catalog.exam.rules")}>
            <ol className="grid list-decimal gap-2.5 ps-5 leading-relaxed text-muted marker:font-semibold marker:text-text-accent">
              {ruleItems.map((rule, index) => (
                <li key={index}>{rule}</li>
              ))}
            </ol>
          </Section>
        ) : null}
        {examType === "WRITTEN" ? (
          <Section title={t("catalog.exam.paperTitle")}>
            <ul className="grid list-disc gap-2 ps-5 text-muted">
              <li>{t("catalog.exam.paperUnlock")}</li>
              <li>{t("catalog.exam.paperPdf")}</li>
            </ul>
          </Section>
        ) : null}
        {owned && owned.passPercent !== null && owned.passPercent !== undefined ? (
          <Section title={t("catalog.exam.passMark")}>
            <p className="text-card-title font-bold">{t("catalog.exam.passMarkValue", { p: formatNumber(owned.passPercent, locale) })}</p>
          </Section>
        ) : null}
        {ownsExam ? <p className="font-semibold text-ok">✓ {t("catalog.exam.unlockedNote")}</p> : null}
        {packageRefs.length > 0 ? (
          <Section title={t("catalog.exam.packagesTitle")}>
            <ExamPackages door={door} packages={packageRefs} reads={packageReads} />
          </Section>
        ) : null}
      </DetailLayout>

      {barAction !== null ? (
        <ActionBar show={showBar} label={title} action={barAction}>
          <span className="line-clamp-1 text-body font-semibold">{title}</span>
          {!ownsExam && ownPrice ? (
            <span className="text-card-title font-extrabold tabular-nums text-text-accent">{formatMoney(charged, locale)}</span>
          ) : null}
        </ActionBar>
      ) : null}

      {isStudent && ownPrice && !ownsExam ? (
        <PurchaseSheet
          open={buying}
          onOpenChange={setBuying}
          item={{
            kind: "exam",
            id: examId,
            title,
            price: charged ?? 0,
            listPrice: pricing?.price ?? null,
            discountPercent: pricing?.discountPercent ?? null,
            discountEndsAt: pricing?.discountEndsAt ?? null,
            packages: packageRefs.map((pkg) => ({ id: pkg.id, title: pickTitle(pkg, locale) })),
            door,
            coverUrl: coverPackage?.coverUrl ?? null,
          }}
          examMeta={{ moduleId: data.moduleId, door }}
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
    </div>
  );
}
