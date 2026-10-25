"use client";

import { useState, type FormEvent } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useChangePassword } from "@/lib/api/exam/account";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { PasswordInput } from "@/components/ui/password-input";
import { useToast } from "@/components/ui/toast";
import { codeOf, FormError, focusFirst } from "@/app/(auth)/_components/auth-ui";
import { isPasswordLength } from "@/app/(auth)/_components/validate";

/** Current and new password. A wrong current password stays inline; the values stay in place. */
export function PasswordForm({ onDone }: { onDone: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const mutation = useChangePassword();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [errors, setErrors] = useState<{ current?: string; next?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found: { current?: string; next?: string } = {};
    if (!current) found.current = t("auth.register.fieldRequired");
    if (!next) found.next = t("auth.register.fieldRequired");
    else if (!isPasswordLength(next)) found.next = t("account.password.lengthError");
    setErrors(found);
    setFormError(null);
    if (found.current || found.next) {
      focusFirst([found.current ? "password-current" : "password-next"]);
      return;
    }
    try {
      await mutation.mutateAsync({ currentPassword: current, newPassword: next });
      toast({ title: t("account.password.saved"), tone: "ok" });
      setCurrent("");
      setNext("");
      onDone();
    } catch (err) {
      const code = codeOf(err);
      if (code === "CURRENT_PASSWORD_INVALID") {
        setErrors({ current: t("account.password.wrongCurrent") });
        focusFirst(["password-current"]);
      } else if (code === "PASSWORD_NOT_ACCEPTED" || code === "VALIDATION_FAILED") {
        setErrors({ next: t("account.password.lengthError") });
        focusFirst(["password-next"]);
      } else {
        setFormError(t("common.states.errorBody"));
      }
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      <Field label={t("account.password.current")} error={errors.current} required>
        <PasswordInput
          id="password-current"
          name="currentPassword"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          invalid={Boolean(errors.current)}
        />
      </Field>
      <Field label={t("account.password.next")} hint={t("account.password.nextHelp")} error={errors.next} required>
        <PasswordInput
          id="password-next"
          name="newPassword"
          autoComplete="new-password"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          invalid={Boolean(errors.next)}
        />
      </Field>
      {formError ? <FormError>{formError}</FormError> : null}
      <Button type="submit" fullWidth loading={mutation.isPending}>
        {t("account.password.submit")}
      </Button>
    </form>
  );
}
