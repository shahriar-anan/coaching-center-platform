"use client";

import { useState, type FormEvent } from "react";
import { ArrowDown, ArrowUp } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { IconButton } from "@/components/ui/icon-button";
import { ListCard } from "@/components/ui/list-card";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useAccountAction, useAccounts, useReorderAccounts, useSaveAccount, type AccountBody } from "@/lib/api/accounting/accounts";
import type { AccountKind, MoneyAccount } from "@/lib/api/accounting/types";
import { accountLabel, moveId, writeErrorText } from "../accounting-model";

const KINDS: AccountKind[] = ["CASH", "BKASH", "NAGAD", "ROCKET", "BANK"];

/** Settings, Money accounts tab (accounting-plan 4.6): add, rename, kind, note, order, archive and restore. */
export function AccountsSettings() {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const [showArchived, setShowArchived] = useState(false);
  const accounts = useAccounts(showArchived);
  const liveAccounts = useAccounts(false);
  const reorder = useReorderAccounts();
  const action = useAccountAction();
  const [editing, setEditing] = useState<MoneyAccount | "new" | null>(null);
  const [archiving, setArchiving] = useState<MoneyAccount | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const liveIds = (liveAccounts.data ?? []).map((account) => account.id);

  async function move(account: MoneyAccount, step: -1 | 1) {
    const next = moveId(liveIds, account.id, step);
    if (!next) {
      return;
    }
    setActionError(null);
    try {
      await reorder.mutateAsync(next);
    } catch (error) {
      setActionError(writeErrorText(error, t));
    }
  }

  async function run(account: MoneyAccount, verb: "archive" | "restore") {
    setActionError(null);
    try {
      await action.mutateAsync({ id: account.id, action: verb });
      setArchiving(null);
      toast({ title: t(verb === "archive" ? "accounting.common.archived" : "accounting.settings.accounts.saved"), tone: "ok" });
    } catch (error) {
      setArchiving(null);
      setActionError(writeErrorText(error, t));
    }
  }

  if (accounts.isError) {
    return <ErrorState onRetry={() => void accounts.refetch()} error={accounts.error} />;
  }
  const rows = accounts.data ?? [];

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Switch checked={showArchived} onCheckedChange={setShowArchived} label={t("accounting.common.showArchived")} />
        <Button onClick={() => setEditing("new")}>{t("accounting.settings.accounts.add")}</Button>
      </div>
      {actionError ? <p role="alert" className="text-meta text-danger">{actionError}</p> : null}

      {rows.length === 0 && !accounts.isPending ? (
        <EmptyState title={t("accounting.settings.accounts.empty")} />
      ) : (
        <ListCard>
          {rows.map((account) => {
            const position = liveIds.indexOf(account.id);
            const name = accountLabel(account, locale, t);
            // A seeded account is named by its kind, so the kind line would only repeat the name.
            const kindText = t(`accounting.accountKind.${account.kind}`);
            return (
              <li key={account.id} className="grid gap-3 p-4 sm:grid-cols-[1fr_auto] sm:items-center">
                <div className="grid gap-1">
                  <p className="font-medium text-text">{name}</p>
                  {kindText !== name || account.note ? (
                    <p className="text-meta text-muted">
                      {kindText !== name ? kindText : null}
                      {account.note ? `${kindText !== name ? " · " : ""}${account.note}` : null}
                    </p>
                  ) : null}
                  {account.archived ? (
                    <div className="flex flex-wrap gap-1">
                      <Pill tone="warn">{t("accounting.common.archived")}</Pill>
                    </div>
                  ) : null}
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {account.archived ? (
                    <Button variant="secondary" size="sm" onClick={() => void run(account, "restore")}>
                      {t("accounting.common.restore")}
                    </Button>
                  ) : (
                    <>
                      <IconButton label={t("accounting.common.moveUp")} icon={<ArrowUp aria-hidden className="size-4" />} disabled={position <= 0} onClick={() => void move(account, -1)} />
                      <IconButton label={t("accounting.common.moveDown")} icon={<ArrowDown aria-hidden className="size-4" />} disabled={position >= liveIds.length - 1} onClick={() => void move(account, 1)} />
                      <Button variant="secondary" size="sm" onClick={() => setEditing(account)}>
                        {t("accounting.common.edit")}
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setArchiving(account)}>
                        {t("accounting.common.archive")}
                      </Button>
                    </>
                  )}
                </div>
              </li>
            );
          })}
        </ListCard>
      )}

      {editing ? (
        <AccountForm key={editing === "new" ? "new" : editing.id} account={editing === "new" ? null : editing} onClose={() => setEditing(null)} />
      ) : null}

      <ConfirmDialog
        open={archiving !== null}
        onOpenChange={(open) => !open && setArchiving(null)}
        title={t("accounting.settings.accounts.archiveTitle", { name: archiving ? accountLabel(archiving, locale, t) : "" })}
        body={t("accounting.settings.accounts.archiveBody")}
        confirmLabel={t("accounting.common.archive")}
        tone="danger"
        onConfirm={async () => { if (archiving) await run(archiving, "archive"); }}
      />
    </div>
  );
}

function AccountForm({ account, onClose }: { account: MoneyAccount | null; onClose: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const save = useSaveAccount();
  const [name, setName] = useState(account?.name ?? "");
  const [kind, setKind] = useState<AccountKind>(account?.kind ?? "CASH");
  const [note, setNote] = useState(account?.note ?? "");
  const [nameError, setNameError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!name.trim() || name.trim().length > 100) {
      setNameError(t("accounting.settings.accounts.name"));
      return;
    }
    setNameError(null);
    setFormError(null);
    const body: AccountBody = {
      name: name.trim(),
      kind,
      note: note.trim() || null,
      ...(account ? { version: account.version } : {}),
    };
    try {
      await save.mutateAsync({ id: account?.id, body });
      toast({ title: t("accounting.settings.accounts.saved"), tone: "ok" });
      onClose();
    } catch (error) {
      setFormError(writeErrorText(error, t));
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={account ? t("accounting.settings.accounts.editTitle") : t("accounting.settings.accounts.addTitle")}>
      <form className="grid gap-4" onSubmit={(event) => void submit(event)} noValidate>
        <Field label={t("accounting.settings.accounts.name")} required error={nameError ?? undefined}>
          <TextInput value={name} onChange={(event) => setName(event.target.value)} maxLength={100} invalid={Boolean(nameError)} />
        </Field>
        <Field label={t("accounting.settings.accounts.kind")}>
          <Select value={kind} onChange={(event) => setKind(event.target.value as AccountKind)}>
            {KINDS.map((item) => (
              <option key={item} value={item}>
                {t(`accounting.accountKind.${item}`)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("accounting.settings.accounts.note")} hint={t("accounting.settings.accounts.noteHint")}>
          <TextInput value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} />
        </Field>
        {formError ? <p role="alert" className="text-meta text-danger">{formError}</p> : null}
        <div className="sticky bottom-0 flex justify-end gap-2 bg-card pt-2">
          <Button variant="ghost" onClick={onClose}>
            {t("accounting.common.cancel")}
          </Button>
          <Button type="submit" loading={save.isPending}>
            {save.isPending ? t("accounting.common.saving") : t("accounting.common.save")}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
