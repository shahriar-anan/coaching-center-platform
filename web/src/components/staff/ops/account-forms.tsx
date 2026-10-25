"use client";

import { useState, type FormEvent } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { PasswordInput } from "@/components/ui/password-input";
import { Pill } from "@/components/ui/pill";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/errors";
import {
  useChangePassword,
  useChangePhone,
  useResendEmailVerification,
  useVerifyEmail,
} from "@/lib/api/exam/staff-ops";

/** The error code of a failed call, or null when it was not an API error. */
function codeOf(error: unknown): string | null {
  return error instanceof ApiError ? error.code : null;
}

/** Change password: the current password, then a new one of 8 to 72 characters. */
export function PasswordForm() {
  const { t } = useLocale();
  const { toast } = useToast();
  const change = useChangePassword();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [currentError, setCurrentError] = useState<string | undefined>();
  const [nextError, setNextError] = useState<string | undefined>();

  async function submit(event: FormEvent) {
    event.preventDefault();
    setCurrentError(undefined);
    setNextError(undefined);
    if (next.length < 8 || next.length > 72) {
      setNextError(t("staffOps.account.newPasswordHint"));
      return;
    }
    try {
      await change.mutateAsync({ currentPassword: current, newPassword: next });
      setCurrent("");
      setNext("");
      toast({ title: t("staffOps.account.passwordChanged"), tone: "ok" });
    } catch (error) {
      if (codeOf(error) === "CURRENT_PASSWORD_INVALID") {
        setCurrentError(t("staffOps.account.currentPasswordWrong"));
        return;
      }
      setNextError(t("common.error.body"));
    }
  }

  return (
    <Card>
      <h2 className="text-lg font-semibold text-text">{t("staffOps.account.passwordTitle")}</h2>
      <form className="mt-4 grid gap-4" onSubmit={submit} noValidate>
        <Field label={t("staffOps.account.currentPassword")} error={currentError} required>
          <PasswordInput
            autoComplete="current-password"
            value={current}
            onChange={(event) => setCurrent(event.target.value)}
            invalid={Boolean(currentError)}
          />
        </Field>
        <Field label={t("staffOps.account.newPassword")} hint={t("staffOps.account.newPasswordHint")} error={nextError} required>
          <PasswordInput
            autoComplete="new-password"
            value={next}
            onChange={(event) => setNext(event.target.value)}
            invalid={Boolean(nextError)}
          />
        </Field>
        <div>
          <Button type="submit" loading={change.isPending}>
            {t("staffOps.account.changePassword")}
          </Button>
        </div>
      </form>
    </Card>
  );
}

/** Email: shows whether it is verified, and offers the code and a resend. Name and role are shown elsewhere. */
export function EmailCard({
  email,
  verifiedAt,
  onVerified,
}: {
  email: string;
  verifiedAt: string | null;
  onVerified: () => Promise<void>;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const verify = useVerifyEmail();
  const resend = useResendEmailVerification();
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | undefined>();
  const verified = verifiedAt !== null;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(undefined);
    try {
      await verify.mutateAsync({ code: code.trim() });
      setCode("");
      await onVerified();
      toast({ title: t("staffOps.account.verifyDone"), tone: "ok" });
    } catch (caught) {
      setError(
        codeOf(caught) === "INVALID_CODE" || codeOf(caught) === "CODE_EXPIRED"
          ? t("errors.invalidCode")
          : t("common.error.body"),
      );
    }
  }

  async function sendAgain() {
    try {
      await resend.mutateAsync({ email });
      toast({ title: t("staffOps.account.resendSent"), tone: "info" });
    } catch {
      toast({ title: t("common.error.body"), tone: "danger" });
    }
  }

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm text-muted">{t("staffOps.account.email")}</p>
          <p className="font-latin text-text">{email}</p>
        </div>
        <Pill tone={verified ? "ok" : "warn"}>
          {verified ? t("staffOps.account.emailVerified") : t("staffOps.account.emailNotVerified")}
        </Pill>
      </div>
      {!verified ? (
        <div className="mt-4 grid gap-4">
          <p className="text-sm text-muted">{t("staffOps.account.verifyBody")}</p>
          <form className="grid gap-4" onSubmit={submit} noValidate>
            <Field label={t("staffOps.account.verifyCode")} error={error} required>
              <TextInput
                inputMode="numeric"
                autoComplete="one-time-code"
                value={code}
                onChange={(event) => setCode(event.target.value)}
                invalid={Boolean(error)}
              />
            </Field>
            <div className="flex flex-wrap gap-2">
              <Button type="submit" loading={verify.isPending}>
                {t("staffOps.account.verifyAction")}
              </Button>
              <Button type="button" variant="secondary" loading={resend.isPending} onClick={sendAgain}>
                {t("staffOps.account.resendAction")}
              </Button>
            </div>
          </form>
        </div>
      ) : null}
    </Card>
  );
}

/**
 * Phone: shown as it is. Adding a number when none is set, or changing it, both
 * ask for the current password. The phone is never the password field.
 */
export function PhoneCard({
  phone,
  onSaved,
}: {
  phone: string | null;
  onSaved: () => Promise<void>;
}) {
  const { t } = useLocale();
  const [open, setOpen] = useState(false);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm text-muted">{t("staffOps.account.phone")}</p>
          <p className="font-latin text-text">{phone ?? "—"}</p>
          {!phone ? <p className="mt-1 text-sm text-muted">{t("staffOps.account.phoneAddOnce")}</p> : null}
        </div>
        <Button variant="secondary" onClick={() => setOpen(true)}>
          {phone ? t("staffOps.account.phoneChange") : t("staffOps.account.phoneTitle")}
        </Button>
      </div>
      <PhoneDialog open={open} onOpenChange={setOpen} onSaved={onSaved} />
    </Card>
  );
}

function PhoneDialog({
  open,
  onOpenChange,
  onSaved,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved: () => Promise<void>;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const change = useChangePhone();
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<{ field: "phone" | "password"; message: string } | undefined>();

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(undefined);
    try {
      await change.mutateAsync({ currentPassword: password, phone: phone.trim() });
      setPassword("");
      setPhone("");
      await onSaved();
      toast({ title: t("staffOps.account.phoneSaved"), tone: "ok" });
      onOpenChange(false);
    } catch (caught) {
      const code = codeOf(caught);
      if (code === "CURRENT_PASSWORD_INVALID") {
        setError({ field: "password", message: t("staffOps.account.currentPasswordWrong") });
      } else if (code === "DUPLICATE_PHONE") {
        setError({ field: "phone", message: t("staffOps.account.phoneInUse") });
      } else if (code === "VALIDATION_FAILED") {
        setError({ field: "phone", message: t("staffOps.account.phoneHint") });
      } else {
        setError({ field: "phone", message: t("common.error.body") });
      }
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffOps.account.phoneDialogTitle")}
      description={t("staffOps.account.phoneDialogBody")}
    >
      <form className="grid gap-4" onSubmit={submit} noValidate>
        <Field label={t("staffOps.account.phone")} hint={t("staffOps.account.phoneHint")} error={error?.field === "phone" ? error.message : undefined} required>
          <TextInput
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            invalid={error?.field === "phone"}
          />
        </Field>
        <Field label={t("staffOps.account.currentPassword")} error={error?.field === "password" ? error.message : undefined} required>
          <PasswordInput
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            invalid={error?.field === "password"}
          />
        </Field>
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button type="submit" loading={change.isPending}>
            {t("common.actions.save")}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
