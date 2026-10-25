"use client";

import { useState } from "react";
import { Check, Copy } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";

/** A read-only value with a copy button. The copy result is announced. */
export function CopyField({ value, label }: { value: string; label: string }) {
  const { t } = useLocale();
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
    } catch {
      /* clipboard blocked: the value is still selectable below */
    }
  }

  return (
    <div className="grid gap-1.5">
      <span className="text-label font-medium text-text">{label}</span>
      <div className="flex items-center gap-2">
        <code className="min-h-11 flex-1 break-all rounded-control border border-border bg-bg px-3.5 py-2.5 font-latin text-body tabular-nums text-text select-all">
          {value}
        </code>
        <Button variant="secondary" onClick={copy}>
          {copied ? (
            <Check aria-hidden="true" className="size-4" />
          ) : (
            <Copy aria-hidden="true" className="size-4" />
          )}
          {copied ? t("common.actions.copied") : t("common.actions.copy")}
        </Button>
      </div>
      <span role="status" className="sr-only">
        {copied ? t("common.actions.copied") : ""}
      </span>
    </div>
  );
}
