"use client";

import Link from "next/link";
import { Check, Copy } from "lucide-react";
import { useEffect, useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { copyText, readCodeFromHash, readHashParams, scrubHash } from "@/app/(auth)/_components/code-hash";

type Purpose = "activate" | "reset" | "verify";

const NEXT_PATH: Record<Purpose, (code: string) => string> = {
  activate: (code) => `/activate#code=${encodeURIComponent(code)}`,
  reset: (code) => `/reset-password#code=${encodeURIComponent(code)}`,
  verify: () => "/account",
};

function purposeOf(value: string | null): Purpose {
  return value === "reset" || value === "verify" ? value : "activate";
}

/**
 * Shows the one-time code from a mail's link in large type, with a copy button that really uses the clipboard (a mail cannot).
 * The code is read from the address fragment, kept in memory, and removed from the address bar.
 */
export function CodeCard() {
  const { t } = useLocale();
  const [found, setFound] = useState<{ code: string | null; purpose: Purpose } | null>(null);
  const [copied, setCopied] = useState<"idle" | "done" | "failed">("idle");

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- the address fragment is only readable in the browser, after load
    setFound({ code: readCodeFromHash(), purpose: purposeOf(readHashParams().get("for")) });
    scrubHash();
  }, []);

  async function copy(code: string) {
    setCopied((await copyText(code)) ? "done" : "failed");
  }

  if (!found) {
    return <Skeleton variant="card" />;
  }
  if (!found.code) {
    return (
      <>
        <PageHeader title={t("auth.code.title")} />
        <EmptyState
          title={t("auth.code.noneTitle")}
          body={t("auth.code.noneBody")}
          action={<Button href="/login">{t("auth.code.toLogin")}</Button>}
        />
      </>
    );
  }
  const code = found.code;
  return (
    <>
      <PageHeader title={t("auth.code.title")} />
      <Card padding="lg" className="grid justify-items-center gap-5 text-center">
        <p className="text-body text-muted">{t("auth.code.lead")}</p>
        <output
          aria-label={t("auth.code.title")}
          className="select-all rounded-card border-2 border-dashed border-accent bg-bg px-6 py-4 font-mono text-[34px] font-bold tracking-[0.25em] text-text"
        >
          {code}
        </output>
        <Button variant="secondary" onClick={() => copy(code)} aria-describedby="copy-status">
          {copied === "done" ? <Check aria-hidden="true" size={18} /> : <Copy aria-hidden="true" size={18} />}
          {copied === "done" ? t("auth.code.copied") : t("auth.code.copy")}
        </Button>
        <p id="copy-status" role="status" aria-live="polite" className="min-h-6 text-body text-muted">
          {copied === "done" ? t("auth.code.copiedNote") : copied === "failed" ? t("auth.code.failed") : ""}
        </p>
        <Link
          href={NEXT_PATH[found.purpose](code)}
          className="font-semibold text-text-accent underline underline-offset-4"
        >
          {t(`auth.code.next.${found.purpose}`)}
        </Link>
      </Card>
    </>
  );
}
