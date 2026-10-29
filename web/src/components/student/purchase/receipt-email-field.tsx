"use client";

import Link from "next/link";
import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";

/**
 * The optional address for the receipt, asked before paying (Phase 4B). An empty field means the receipt goes to the account email,
 * only when that email is verified. Skip clears the field and hides the question; a button brings it back.
 */
export function ReceiptEmailField({
  value,
  error,
  verified,
  onChange,
}: {
  value: string;
  error?: string;
  /** Whether the account email is verified: the receipt goes there when no address is typed. */
  verified: boolean;
  onChange: (value: string) => void;
}) {
  const { t } = useLocale();
  const [open, setOpen] = useState(true);

  if (!open) {
    return (
      <div className="grid gap-2 text-meta text-muted">
        <p>{verified ? t("purchase.receipt.skippedVerified") : t("purchase.receipt.skippedUnverified")}</p>
        <Button variant="ghost" className="justify-start px-0" onClick={() => setOpen(true)}>
          {t("purchase.receipt.addAddress")}
        </Button>
      </div>
    );
  }

  return (
    <div className="grid gap-2">
      <Field label={t("purchase.receipt.label")} error={error}>
        <TextInput
          id="receipt-email"
          name="receiptEmail"
          type="email"
          inputMode="email"
          autoComplete="email"
          autoCapitalize="none"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          invalid={Boolean(error)}
        />
      </Field>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-meta text-muted">
          {verified ? (
            t("purchase.receipt.helper")
          ) : (
            <>
              {t("purchase.receipt.helperUnverified")}{" "}
              <Link href="/account" className="font-semibold text-text-accent underline underline-offset-4">
                {t("purchase.receipt.verifyLink")}
              </Link>
            </>
          )}
        </p>
        <Button
          variant="ghost"
          className="min-h-11 px-2"
          onClick={() => {
            onChange("");
            setOpen(false);
          }}
        >
          {t("purchase.receipt.skip")}
        </Button>
      </div>
    </div>
  );
}
