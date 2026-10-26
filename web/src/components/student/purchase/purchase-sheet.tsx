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
import { Button } from "@/components/ui/button";
import { Sheet } from "@/components/ui/sheet";
import { PackageCover, coverSrc } from "@/components/ui/package/package-cover";
import { IncludedList } from "@/components/student/catalog/included-list";

export interface PurchaseItem {
  kind: "exam" | "module";
  id: string;
  title: string;
  price: number;
  /** Door to return to after paying (module purchase). */
  door?: "mcq" | "written";
  /** The package cover path, for the summary card. */
  coverUrl?: string | null;
  /** How many exams the package holds now, for the included list. */
  examCount?: number;
}

export const ORDER_MEMO_PREFIX = "exam-portal.order.";

export interface OrderMemo {
  scope: "EXAM" | "MODULE" | "BUNDLE";
  examId: string | null;
  moduleId: string | null;
  door: "mcq" | "written" | null;
  title: string;
}

function remember(tranId: string, memo: OrderMemo) {
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

  function close(next: boolean) {
    if (!next) {
      setProblem(null);
      start.reset();
    }
    onOpenChange(next);
  }

  async function pay() {
    setProblem(null);
    try {
      const order = await start.mutateAsync(
        item.kind === "exam" ? { examId: item.id } : { moduleId: item.id },
      );
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
      });
      setLeaving(true);
      window.location.assign(order.redirectUrl);
    } catch (error) {
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
            <p className="text-card-title font-extrabold tabular-nums text-text-accent">{formatMoney(item.price, locale)}</p>
          </div>
        </div>
        <p className="font-semibold">{t(item.kind === "exam" ? "purchase.unlockExam" : "purchase.unlockModule")}</p>
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
        <p className="text-body text-muted">🔒 {t("purchase.trust")}</p>
        <p className="text-body text-muted">
          {t("purchase.leaving")}{" "}
          <Link href="/refund" className="text-accent underline" onClick={() => close(false)}>
            {t("purchase.refundLink")}
          </Link>
        </p>
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
        {problem?.kind === "owned" ? (
          <Button fullWidth onClick={() => close(false)}>
            {t("catalog.unlocked")}
          </Button>
        ) : (
          <Button fullWidth loading={busy} onClick={() => void pay()}>
            {busy ? t("purchase.redirecting") : t("purchase.pay")}
          </Button>
        )}
        <Button variant="secondary" fullWidth onClick={() => close(false)}>
          {t("purchase.cancel")}
        </Button>
      </div>
    </Sheet>
  );
}
