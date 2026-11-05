"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import {
  PAYOUT_ACCOUNT_LIMIT,
  useMakePreferredPayoutAccount,
  usePayoutAccounts,
  useRemovePayoutAccount,
  type PayoutAccount,
} from "@/lib/api/exam/payout-accounts";
import { PayoutAccountDialog } from "./payout-account-dialog";

/**
 * The owner's payout accounts on the staff account page: the list (numbers masked), add, edit, make preferred and remove.
 * Only System Admins and instructors see it; the page decides that. Nothing here is required to use the site.
 */
export function PayoutAccountsSection() {
  const { t } = useLocale();
  const { toast } = useToast();
  const query = usePayoutAccounts(true);
  const makePreferred = useMakePreferredPayoutAccount();
  const remove = useRemovePayoutAccount();
  const [dialog, setDialog] = useState<{ account?: PayoutAccount } | null>(null);
  const [removing, setRemoving] = useState<PayoutAccount | null>(null);

  const accounts = query.data ?? [];
  const full = accounts.length >= PAYOUT_ACCOUNT_LIMIT;

  async function prefer(account: PayoutAccount) {
    try {
      await makePreferred.mutateAsync(account.id);
      toast({ title: t("payoutAccounts.preferredDone"), tone: "ok" });
    }
    catch {
      toast({ title: t("common.error.body"), tone: "danger" });
    }
  }

  async function confirmRemove() {
    if (!removing) {
      return;
    }
    try {
      await remove.mutateAsync(removing.id);
      toast({ title: t("payoutAccounts.removed"), tone: "ok" });
    }
    catch {
      toast({ title: t("common.error.body"), tone: "danger" });
    }
    setRemoving(null);
  }

  return (
    <Card id="payout-accounts">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1">
          <h2 className="text-section text-text">{t("payoutAccounts.title")}</h2>
          <p className="text-meta text-muted">{t("payoutAccounts.intro")}</p>
        </div>
        <Button variant="secondary" disabled={full || query.isPending} onClick={() => setDialog({})}>
          {t("payoutAccounts.add")}
        </Button>
      </div>

      <div className="mt-4">
        {query.isPending ? (
          <Skeleton variant="rows" count={2} />
        ) : query.isError ? (
          <ErrorState onRetry={() => void query.refetch()} />
        ) : accounts.length === 0 ? (
          <div className="grid gap-1">
            <p className="text-text">{t("payoutAccounts.emptyTitle")}</p>
            <p className="text-meta text-muted">{t("payoutAccounts.emptyBody")}</p>
          </div>
        ) : (
          <ul className="grid">
            {accounts.map((account) => (
              <li key={account.id} className="grid gap-2 border-b border-border py-3 last:border-b-0">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="grid gap-0.5">
                    <p className="text-text">
                      {account.kind === "BANK" ? account.bankName ?? "" : t(`payoutAccounts.kind.${account.kind}`)}
                    </p>
                    <p className="font-latin text-meta text-muted">{account.maskedNumber}</p>
                    {account.kind === "BANK" && account.accountName ? (
                      <p className="text-meta text-muted">{account.accountName}</p>
                    ) : null}
                  </div>
                  {account.preferred ? <Pill tone="ok">{t("payoutAccounts.preferred")}</Pill> : null}
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" variant="secondary" onClick={() => setDialog({ account })}>
                    {t("payoutAccounts.edit")}
                  </Button>
                  {!account.preferred ? (
                    <Button size="sm" variant="ghost" loading={makePreferred.isPending} onClick={() => void prefer(account)}>
                      {t("payoutAccounts.makePreferred")}
                    </Button>
                  ) : null}
                  <Button size="sm" variant="ghost" onClick={() => setRemoving(account)}>
                    {t("payoutAccounts.remove")}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
        {full ? <p className="mt-3 text-meta text-muted">{t("payoutAccounts.limitReached")}</p> : null}
      </div>

      {dialog ? (
        <PayoutAccountDialog
          key={dialog.account?.id ?? "new"}
          open
          account={dialog.account}
          onOpenChange={(open) => {
            if (!open) {
              setDialog(null);
            }
          }}
        />
      ) : null}

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => {
          if (!open) {
            setRemoving(null);
          }
        }}
        title={t("payoutAccounts.removeDialog.title")}
        body={t("payoutAccounts.removeDialog.body")}
        confirmLabel={t("payoutAccounts.removeDialog.confirm")}
        tone="danger"
        onConfirm={confirmRemove}
      />
    </Card>
  );
}
