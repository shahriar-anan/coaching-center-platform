"use client";

import { useState, type FormEvent } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import { useChangePhone } from "@/lib/api/exam/account";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { PasswordInput } from "@/components/ui/password-input";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { codeOf, FormError, focusFirst } from "@/app/(auth)/_components/auth-ui";
import { isPhone, normalizePhone } from "@/app/(auth)/_components/validate";

/** Changes the own phone with the current password. A wrong password stays on the form. */
export function PhoneDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useLocale();
  const { refreshMe } = useSession();
  const { toast } = useToast();
  const mutation = useChangePhone();
  const [current, setCurrent] = useState("");
  const [phone, setPhone] = useState("");
  const [errors, setErrors] = useState<{ current?: string; phone?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);

  function reset() {
    setCurrent("");
    setPhone("");
    setErrors({});
    setFormError(null);
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: { current?: string; phone?: string } = {};
    if (!current) next.current = t("auth.register.fieldRequired");
    if (!phone.trim()) next.phone = t("auth.register.fieldRequired");
    else if (!isPhone(phone)) next.phone = t("auth.register.invalidPhone");
    setErrors(next);
    setFormError(null);
    if (next.current || next.phone) {
      focusFirst([next.current ? "phone-current" : "phone-new"]);
      return;
    }
    try {
      await mutation.mutateAsync({ currentPassword: current, phone: normalizePhone(phone) });
      await refreshMe();
      toast({ title: t("account.phoneDialog.saved"), tone: "ok" });
      reset();
      onOpenChange(false);
    } catch (err) {
      const code = codeOf(err);
      if (code === "CURRENT_PASSWORD_INVALID") {
        setErrors({ current: t("account.phoneDialog.wrongCurrent") });
        focusFirst(["phone-current"]);
      } else if (code === "DUPLICATE_PHONE") {
        setErrors({ phone: t("account.phoneDialog.duplicate") });
        focusFirst(["phone-new"]);
      } else if (code === "VALIDATION_FAILED") {
        setErrors({ phone: t("auth.register.invalidPhone") });
        focusFirst(["phone-new"]);
      } else {
        setFormError(t("common.states.errorBody"));
      }
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) reset();
        onOpenChange(next);
      }}
      title={t("account.phoneDialog.title")}
    >
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <Field label={t("account.phoneDialog.current")} error={errors.current} required>
          <PasswordInput
            id="phone-current"
            name="currentPassword"
            autoComplete="current-password"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
            invalid={Boolean(errors.current)}
          />
        </Field>
        <Field label={t("account.phoneDialog.phone")} hint={t("account.phoneDialog.help")} error={errors.phone} required>
          <TextInput
            id="phone-new"
            name="phone"
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            invalid={Boolean(errors.phone)}
          />
        </Field>
        {formError ? <FormError>{formError}</FormError> : null}
        <Button type="submit" fullWidth loading={mutation.isPending}>
          {t("account.phoneDialog.submit")}
        </Button>
      </form>
    </Dialog>
  );
}
