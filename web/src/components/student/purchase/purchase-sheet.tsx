"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api/errors";
import {
  useCancelOrder,
  useInvalidateCatalog,
  useStartOrder,
} from "@/lib/api/exam/purchase";
import { formatMoney, formatNumber } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { Lock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet } from "@/components/ui/sheet";
import { PackageCover, coverSrc } from "@/components/ui/package/package-cover";
import { IncludedList } from "@/components/student/catalog/included-list";
import { PriceLine } from "@/components/student/catalog/price-line";
import { priceLabel } from "@/components/student/catalog/price-view";
import { ReceiptEmailField } from "@/components/student/purchase/receipt-email-field";
import { isEmail } from "@/app/(auth)/_components/validate";
import { useSession } from "@/lib/auth/session-context";
import type { PriceView } from "@/lib/api/exam/types";

export interface PurchaseItem {
  kind: "exam" | "module";
  id: string;
  title: string;
  /** The amount the order charges: the effective price after any discount (NR-01, S2.6). */
  price: number;
  /** The list price, shown struck through beside the price when the item is discounted. */
  listPrice?: number | null;
  discountPercent?: number | null;
  discountEndsAt?: string | null;
  /** The packages an exam is in, named on the sheet so the student knows where it comes from. */
  packages?: { id: string; title: string }[];
  /** Door to return to after paying (module purchase). */
  door?: "mcq" | "written";
  /** The package cover path, for the summary card. */
  coverUrl?: string | null;
  /** How many exams the package holds now, for the included list. */
  examCount?: number;
}

/** The price view the sheet shows: the list price and the charged price, so a discount shows struck through (NR-01, S2.6). */
export function sheetPriceView(item: PurchaseItem): PriceView {
  return {
    price: item.listPrice ?? item.price,
    discountPercent: item.discountPercent ?? null,
    discountEndsAt: item.discountEndsAt ?? null,
    effectivePrice: item.price,
    free: false,
    moduleOnly: false,
  };
}

export const ORDER_MEMO_PREFIX = "exam-portal.order.";

export interface OrderMemo {
  scope: "EXAM" | "MODULE" | "BUNDLE";
  examId: string | null;
  moduleId: string | null;
  door: "mcq" | "written" | null;
  title: string;
  /** The amount the student agreed to pay, shown on the success page. */
  amount?: number;
  /** The order, so the success page can link to its payment and its receipt (Phase 4, S4.6). */
  orderId?: string;
}

export function remember(tranId: string, memo: OrderMemo) {
  try {
    window.sessionStorage.setItem(`${ORDER_MEMO_PREFIX}${tranId}`, JSON.stringify(memo));
  } catch {
    /* storage blocked: the result page falls back to My modules */
  }
}

type Problem =
  | { kind: "gateway"; orderId?: string }
  | { kind: "phone" }
  | { kind: "owned" }
  | { kind: "price" }
  | { kind: "missing" }
  | { kind: "generic" }
  | { kind: "cancelled" };

function problemFrom(error: unknown): Problem {
  if (!(error instanceof ApiError)) return { kind: "generic" };
  switch (error.code) {
    case "PAYMENT_GATEWAY_UNAVAILABLE":
      return { kind: "gateway", orderId: error.orderId };
    case "PHONE_REQUIRED":
      return { kind: "phone" };
    case "ALREADY_PURCHASED":
      return { kind: "owned" };
    case "PRICE_REQUIRED":
      return { kind: "price" };
    case "RESOURCE_NOT_FOUND":
      return { kind: "missing" };
    default:
      return { kind: "generic" };
  }
}

const problemKey: Record<Problem["kind"], string> = {
  gateway: "purchase.errors.gateway",
  phone: "purchase.errors.phoneRequired",
  owned: "purchase.errors.alreadyPurchased",
  price: "purchase.errors.priceRequired",
  missing: "purchase.errors.notFound",
  generic: "purchase.errors.generic",
  cancelled: "purchase.pendingCancelled",
};

/** The payment methods shown on the sheet. A method without a logo file shows its name instead. */
const PAYMENT_METHODS: { name: string; src?: string }[] = [
  { name: "bKash", src: "/brand/payment/bkash.svg" },
  { name: "Nagad", src: "/brand/payment/nagad.svg" },
  { name: "Rocket", src: "/brand/payment/rocket.png" },
  { name: "Visa", src: "/brand/payment/visa.png" },
  { name: "Mastercard" },
];

export function PurchaseSheet({
  open,
  onOpenChange,
  item,
  moduleOffer,
  onSwitchToModule,
  examMeta,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  item: PurchaseItem;
  /** The priced module the exam belongs to, for the quiet comparison line. */
  moduleOffer?: { price: number } | null;
  onSwitchToModule?: () => void;
  examMeta?: { moduleId: string; door: "mcq" | "written" };
}) {
  const { t, locale } = useLocale();
  const pathname = usePathname();
  const start = useStartOrder();
  const cancel = useCancelOrder();
  const invalidate = useInvalidateCatalog();
  const [problem, setProblem] = useState<Problem | null>(null);
  const [leaving, setLeaving] = useState(false);
  // The receipt address is per order (Phase 4B): it is typed for this purchase only and is not saved to the profile.
  const { session } = useSession();
  const verified = session.status === "authenticated" && Boolean(session.user.emailVerifiedAt);
  const [receiptEmail, setReceiptEmail] = useState("");
  const [receiptError, setReceiptError] = useState<string | null>(null);

  function close(next: boolean) {
    if (!next) {
      setProblem(null);
      setReceiptEmail("");
      setReceiptError(null);
      start.reset();
    }
    onOpenChange(next);
  }

  function receiptFieldError(message: string) {
    setReceiptError(message);
    document.getElementById("receipt-email")?.focus();
  }

  async function pay() {
    setProblem(null);
    // The address is checked here first, so an invalid one sends no request.
    const typed = receiptEmail.trim();
    if (typed && !isEmail(typed)) {
      receiptFieldError(t("purchase.receipt.invalid"));
      return;
    }
    setReceiptError(null);
    try {
      const target = item.kind === "exam" ? { examId: item.id } : { moduleId: item.id };
      const order = await start.mutateAsync(typed ? { ...target, receiptEmail: typed } : target);
      if (!order.redirectUrl) {
        setProblem({ kind: "generic" });
        return;
      }
      remember(order.tranId, {
        scope: order.scope,
        examId: order.examId,
        moduleId: order.moduleId ?? examMeta?.moduleId ?? null,
        door: item.door ?? examMeta?.door ?? null,
        title: item.title,
        amount: item.price,
        orderId: order.orderId,
      });
      setLeaving(true);
      window.location.assign(order.redirectUrl);
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors?.some((detail) => detail.field === "receiptEmail")) {
        receiptFieldError(t("purchase.receipt.invalid"));
        return;
      }
      const next = problemFrom(error);
      if (next.kind === "owned" || next.kind === "price" || next.kind === "missing") {
        void invalidate();
      }
      setProblem(next);
    }
  }

  async function cancelPending(orderId: string) {
    try {
      await cancel.mutateAsync(orderId);
      setProblem({ kind: "cancelled" });
    } catch {
      setProblem({ kind: "generic" });
    }
  }

  const busy = start.isPending || leaving;

  return (
    <Sheet
      open={open}
      onOpenChange={close}
      title={t(item.kind === "exam" ? "purchase.sheetTitleExam" : "purchase.sheetTitleModule")}
    >
      <div className="grid gap-3">
        <div className="grid grid-cols-[96px_minmax(0,1fr)] items-center gap-3 rounded-card border border-border bg-bg p-3">
          <PackageCover
            src={coverSrc(item.coverUrl)}
            alt={item.title}
            type={item.door === "written" ? "WRITTEN" : "MCQ"}
            title={item.title}
            className="rounded-control"
          />
          <div className="grid min-w-0 gap-1">
            <p className="line-clamp-2 font-bold leading-snug">{item.title}</p>
            <PriceLine label={priceLabel(sheetPriceView(item))} size="detail" />
          </div>
        </div>
        <p className="font-semibold">{t(item.kind === "exam" ? "purchase.unlockExam" : "purchase.unlockModule")}</p>
        {item.kind === "exam" && item.packages && item.packages.length > 0 ? (
          <p className="text-body text-muted">
            {t("purchase.inPackages", { titles: item.packages.map((pkg) => pkg.title).join(", ") })}
          </p>
        ) : null}
        {item.kind === "module" ? (
          <IncludedList
            items={[
              ...(item.examCount !== undefined
                ? [t("catalog.package.currentExams", { n: formatNumber(item.examCount, locale) })]
                : []),
              t("catalog.package.futureExams"),
            ]}
          />
        ) : null}
        {item.kind === "exam" && moduleOffer && onSwitchToModule ? (
          <p className="text-body text-muted">
            {t("purchase.compare", { price: formatMoney(moduleOffer.price, locale) ?? "" })}{" "}
            <button type="button" className="font-semibold text-accent underline" onClick={onSwitchToModule}>
              {t("purchase.switchToModule")}
            </button>
          </p>
        ) : null}
        <div className="grid gap-3 rounded-card border border-border p-3">
          <ul aria-label={t("purchase.paymentsAlt")} className="flex flex-wrap items-center gap-2">
            {PAYMENT_METHODS.map((method) => (
              <li key={method.name} className="flex h-9 min-w-16 items-center justify-center rounded-control border border-border bg-card px-2.5">
                {method.src ? (
                  // The method logos are static files in the brand folder, not run through the optimiser.
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={method.src} alt={method.name} className="h-5 w-auto max-w-[72px] object-contain" />
                ) : (
                  <span className="text-meta font-semibold">{method.name}</span>
                )}
              </li>
            ))}
          </ul>
          <p className="flex items-center gap-1.5 text-meta text-muted"><Lock aria-hidden="true" className="size-3.5 shrink-0" />{t("purchase.trust")}</p>
          <ol className="grid gap-1.5 text-body text-muted">
            <li className="flex gap-2"><span className="font-bold text-text-accent">1.</span>{t("purchase.next1")}</li>
            <li className="flex gap-2"><span className="font-bold text-text-accent">2.</span>{t("purchase.next2")}</li>
            <li className="flex gap-2"><span className="font-bold text-text-accent">3.</span>{t("purchase.next3")}</li>
          </ol>
        </div>
        {problem ? (
          <div role="alert" className="rounded-control border border-border p-3 text-meta">
            <p className={problem.kind === "cancelled" || problem.kind === "owned" ? "" : "text-danger"}>
              {t(problemKey[problem.kind])}
            </p>
            {problem.kind === "phone" ? (
              <Button
                variant="secondary"
                className="mt-2"
                href={`/set-phone?next=${encodeURIComponent(pathname)}`}
              >
                {t("purchase.addPhone")}
              </Button>
            ) : null}
            {problem.kind === "gateway" && problem.orderId ? (
              <Button
                variant="ghost"
                className="mt-1 px-0"
                loading={cancel.isPending}
                onClick={() => void cancelPending(problem.orderId as string)}
              >
                {t("purchase.cancelPending")}
              </Button>
            ) : null}
          </div>
        ) : null}
        <p className="text-meta text-muted">
          <Link href="/refund" className="text-accent underline" onClick={() => close(false)}>
            {t("purchase.refundLink")}
          </Link>
        </p>
        {problem?.kind === "owned" ? (
          <Button fullWidth onClick={() => close(false)}>
            {t("catalog.unlocked")}
          </Button>
        ) : (
          <>
            <ReceiptEmailField
              value={receiptEmail}
              error={receiptError ?? undefined}
              verified={verified}
              onChange={(value) => {
                setReceiptEmail(value);
                setReceiptError(null);
              }}
            />
            <Button fullWidth loading={busy} onClick={() => void pay()}>
              {busy ? t("purchase.redirecting") : t("purchase.payWithPrice", { price: formatMoney(item.price, locale) ?? "" })}
            </Button>
          </>
        )}
        <Button variant="ghost" fullWidth onClick={() => close(false)}>
          {t("purchase.cancel")}
        </Button>
      </div>
    </Sheet>
  );
}
