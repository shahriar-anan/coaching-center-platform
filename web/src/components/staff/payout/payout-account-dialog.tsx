"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import {
  PAYOUT_ACCOUNT_KINDS,
  useSavePayoutAccount,
  type PayoutAccount,
  type PayoutAccountKind,
} from "@/lib/api/exam/payout-accounts";
import {
  checkDraft,
  draftFrom,
  emptyDraft,
  inputFrom,
  type PayoutAccountDraft,
  type PayoutAccountDraftErrors,
} from "./payout-account-form";

/**
 * Adds an account, or changes `account` when one is given. The owner's edit form shows the stored numbers in full. Mounted
 * fresh for each open (the parent keys it), so an old draft never shows.
 */
export function PayoutAccountDialog({
  open,
  onOpenChange,
  account,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  account?: PayoutAccount;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const save = useSavePayoutAccount();
  const [draft, setDraft] = useState<PayoutAccountDraft>(() => (account ? draftFrom(account) : emptyDraft()));
  const [errors, setErrors] = useState<PayoutAccountDraftErrors>({});
  const [formError, setFormError] = useState<string | undefined>();

  function set<K extends keyof PayoutAccountDraft>(key: K, value: PayoutAccountDraft[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  function fieldError(key: keyof PayoutAccountDraft): string | undefined {
    const code = errors[key];
    if (!code) {
      return undefined;
    }
    return code === "required"
      ? t("payoutAccounts.errors.required")
      : t(`payoutAccounts.errors.${code}`);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found = checkDraft(draft);
    setErrors(found);
    setFormError(undefined);
    if (Object.keys(found).length > 0) {
      return;
    }
    try {
      await save.mutateAsync({ id: account?.id, input: inputFrom(draft) });
      toast({ title: t("payoutAccounts.saved"), tone: "ok" });
      onOpenChange(false);
    }
    catch (caught) {
      setFormError(messageFor(caught, t));
    }
  }

  const bank = draft.kind === "BANK";

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t(account ? "payoutAccounts.dialog.editTitle" : "payoutAccounts.dialog.addTitle")}>
      <form className="grid gap-4" onSubmit={submit} noValidate>
        <Field label={t("payoutAccounts.kindLabel")} required>
          <Select value={draft.kind} onChange={(event) => set("kind", event.target.value as PayoutAccountKind)}>
            {PAYOUT_ACCOUNT_KINDS.map((kind) => (
              <option key={kind} value={kind}>
                {t(`payoutAccounts.kind.${kind}`)}
              </option>
            ))}
          </Select>
        </Field>

        {bank ? (
          <>
            <Field label={t("payoutAccounts.field.accountName")} error={fieldError("accountName")} required>
              <TextInput value={draft.accountName} onChange={(event) => set("accountName", event.target.value)} invalid={Boolean(errors.accountName)} />
            </Field>
            <Field label={t("payoutAccounts.field.accountNumber")} hint={t("payoutAccounts.hint.accountNumber")} error={fieldError("accountNumber")} required>
              <TextInput inputMode="numeric" autoComplete="off" className="font-latin" value={draft.accountNumber} onChange={(event) => set("accountNumber", event.target.value)} invalid={Boolean(errors.accountNumber)} />
            </Field>
            <Field label={t("payoutAccounts.field.bankName")} error={fieldError("bankName")} required>
              <TextInput value={draft.bankName} onChange={(event) => set("bankName", event.target.value)} invalid={Boolean(errors.bankName)} />
            </Field>
            <Field label={t("payoutAccounts.field.branch")} error={fieldError("branch")} required>
              <TextInput value={draft.branch} onChange={(event) => set("branch", event.target.value)} invalid={Boolean(errors.branch)} />
            </Field>
            <Field label={t("payoutAccounts.field.routingNumber")} hint={t("payoutAccounts.hint.routingNumber")} error={fieldError("routingNumber")} required>
              <TextInput inputMode="numeric" autoComplete="off" className="font-latin" value={draft.routingNumber} onChange={(event) => set("routingNumber", event.target.value)} invalid={Boolean(errors.routingNumber)} />
            </Field>
          </>
        ) : (
          <Field label={t("payoutAccounts.field.walletNumber")} hint={t("payoutAccounts.hint.walletNumber")} error={fieldError("walletNumber")} required>
            <TextInput inputMode="tel" autoComplete="off" className="font-latin" value={draft.walletNumber} onChange={(event) => set("walletNumber", event.target.value)} invalid={Boolean(errors.walletNumber)} />
          </Field>
        )}

        {formError ? (
          <p role="alert" className="text-meta text-danger">
            {formError}
          </p>
        ) : null}

        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            {t("payoutAccounts.dialog.cancel")}
          </Button>
          <Button type="submit" loading={save.isPending}>
            {t("payoutAccounts.dialog.save")}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** The message for a failed save. The limit and a bad entry have their own; anything else is the general one. */
function messageFor(caught: unknown, t: (key: string) => string): string {
  if (caught instanceof ApiError) {
    if (caught.code === "PAYOUT_ACCOUNT_LIMIT") return t("payoutAccounts.errors.limit");
    if (caught.code === "VALIDATION_FAILED") return t("payoutAccounts.errors.invalid");
  }
  return t("payoutAccounts.errors.generic");
}
