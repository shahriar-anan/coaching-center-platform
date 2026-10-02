"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";

type Props = {
  code: string;
};

export function ActivationCodePanel({ code }: Props) {
  const { t } = useLocale();
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div
      className="mt-6 rounded-lg border border-amber-300 bg-amber-50 p-4 text-amber-950"
      role="status"
    >
      <p className="font-semibold">{t("activation.title")}</p>
      <p className="mt-1 text-sm">{t("activation.hint")}</p>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <code className="rounded bg-white px-3 py-2 font-mono text-lg tracking-wide">
          {code}
        </code>
        <button
          type="button"
          onClick={copy}
          className="rounded-md bg-amber-900 px-3 py-2 text-sm font-medium text-white hover:bg-amber-800"
        >
          {copied ? t("common.copied") : t("common.copy")}
        </button>
      </div>
    </div>
  );
}
