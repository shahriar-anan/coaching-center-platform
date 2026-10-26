"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { ApiError } from "@/lib/api/errors";
import {
  useDeleteAccount,
  useSetAccountStatus,
  type AccountKind,
} from "@/lib/api/exam/staff-admin";
import type { AccountStatus } from "@/lib/api/exam/types";
import { adminErrorText } from "@/components/staff/access/admin-error";

/**
 * Suspend or restore a student or an instructor. Master Admin only; for anyone
 * else this renders nothing. Suspending is quiet, so it does not ask first.
 */
export function AccountStatusToggle({
  kind,
  id,
  status,
}: {
  kind: AccountKind;
  id: string;
  status: AccountStatus;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const { isMasterAdmin } = useRole();
  const setStatus = useSetAccountStatus(kind);

  if (!isMasterAdmin || status === "PENDING_ACTIVATION") {
    return null;
  }
  const suspended = status === "INACTIVE";

  return (
    <Button
      variant="secondary"
      size="sm"
      loading={setStatus.isPending}
      onClick={async () => {
        try {
          await setStatus.mutateAsync({ id, status: suspended ? "ACTIVE" : "INACTIVE" });
          toast({ title: t("staffAdmin.account.statusChanged"), tone: "ok" });
        } catch (e) {
          toast({ title: adminErrorText(e, t), tone: "danger" });
        }
      }}
    >
      {suspended ? t("staffAdmin.account.restore") : t("staffAdmin.account.suspend")}
    </Button>
  );
}

/**
 * Deletes a student or an instructor after the name is typed. Master Admin only.
 * The button opens `DeleteAccountDialog`; `onDeleted` runs after a successful delete.
 */
export function DeleteAccountButton({
  kind,
  id,
  name,
  onDeleted,
}: {
  kind: AccountKind;
  id: string;
  name: string;
  /** Called after the account is deleted, for a page that leaves the account's own page. */
  onDeleted?: () => void;
}) {
  const { t } = useLocale();
  const { isMasterAdmin } = useRole();
  const [open, setOpen] = useState(false);

  if (!isMasterAdmin) {
    return null;
  }

  return (
    <>
      <Button variant="danger" size="sm" onClick={() => setOpen(true)}>
        {t("staffAdmin.account.deleteAction")}
      </Button>
      <DeleteAccountDialog
        kind={kind}
        id={id}
        name={name}
        open={open}
        onOpenChange={setOpen}
        onDeleted={onDeleted}
      />
    </>
  );
}

/**
 * The delete dialog for one student or instructor. The API refuses when the account
 * has paid orders (student) or marked work (instructor); the refusal is shown with a
 * way to suspend the account instead. A list that opens it from a row menu uses this
 * directly. Master Admin only: for anyone else it renders nothing.
 */
export function DeleteAccountDialog({
  kind,
  id,
  name,
  open,
  onOpenChange,
  onDeleted,
}: {
  kind: AccountKind;
  id: string;
  name: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onDeleted?: () => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const { isMasterAdmin } = useRole();
  const remove = useDeleteAccount(kind);
  const setStatus = useSetAccountStatus(kind);
  const [typed, setTyped] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [blocked, setBlocked] = useState(false);

  if (!isMasterAdmin) {
    return null;
  }
  const matches = typed.trim() === name.trim();

  function close(next: boolean) {
    if (remove.isPending || setStatus.isPending) {
      return;
    }
    onOpenChange(next);
    setTyped("");
    setError(null);
    setBlocked(false);
  }

  async function confirmDelete() {
    setError(null);
    try {
      await remove.mutateAsync(id);
      toast({ title: t("staffAdmin.account.deleted"), tone: "ok" });
      close(false);
      onDeleted?.();
    } catch (e) {
      setError(adminErrorText(e, t));
      setBlocked(e instanceof ApiError && (e.code === "ACCOUNT_HAS_PAID_ORDERS" || e.code === "ACCOUNT_HAS_MARKED_WORK"));
    }
  }

  async function suspend() {
    try {
      await setStatus.mutateAsync({ id, status: "INACTIVE" });
      toast({ title: t("staffAdmin.account.statusChanged"), tone: "ok" });
      close(false);
    } catch (e) {
      setError(adminErrorText(e, t));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={close}
      title={t("staffAdmin.account.deleteTitle", { name })}
      description={t("staffAdmin.account.deleteBody")}
      footer={
        <>
          <Button variant="ghost" onClick={() => close(false)} disabled={remove.isPending}>
            {t("common.actions.cancel")}
          </Button>
          <Button variant="danger" loading={remove.isPending} disabled={!matches || blocked} onClick={confirmDelete}>
            {t("staffAdmin.account.deleteConfirm")}
          </Button>
        </>
      }
    >
      <div className="grid gap-3">
        <Field label={t("staffAdmin.account.deleteTypeName")}>
          <TextInput value={typed} autoComplete="off" onChange={(event) => setTyped(event.target.value)} />
        </Field>
        {error ? (
          <p role="alert" className="text-meta text-danger">
            {error}
          </p>
        ) : null}
        {blocked ? (
          <div className="grid gap-2">
            <p className="text-meta text-muted">{t("staffAdmin.account.deleteBlockedHint")}</p>
            <Button variant="secondary" size="sm" className="justify-self-start" loading={setStatus.isPending} onClick={suspend}>
              {t("staffAdmin.account.suspend")}
            </Button>
          </div>
        ) : null}
      </div>
    </Dialog>
  );
}
