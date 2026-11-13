"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import type { CreatedAccount, NewPerson } from "@/lib/api/exam/staff-people";
import { ApiError } from "@/lib/api/errors";

/** Bangladeshi mobile: 01 and nine digits. The server checks the same rule. */
const PHONE = /^01\d{9}$/;

/**
 * Creates a person from name, phone and email. There is never a password field:
 * the account is pending and the one-time code comes back in the response.
 */
export function NewPersonDialog({
  open,
  onOpenChange,
  title,
  submit,
  onCreated,
  offlineOption = false,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  submit: (body: NewPerson) => Promise<CreatedAccount>;
  /** Told whether the account was made as an offline admission (the desk form), so the code is read out with its steps. */
  onCreated: (account: CreatedAccount, offline: boolean) => void;
  /** Shows the "Offline admission" checkbox, on by default (plan 6.3). Only the students page offers it. */
  offlineOption?: boolean;
}) {
  const { t } = useLocale();
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [offline, setOffline] = useState(true);
  const [errors, setErrors] = useState<Partial<Record<"name" | "phone" | "email" | "form", string>>>({});
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const next: typeof errors = {};
    if (!name.trim()) {
      next.name = t("staffPeople.form.required");
    }
    if (!PHONE.test(phone.trim())) {
      next.phone = t("staffPeople.form.phoneHint");
    }
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      next.email = t("staffPeople.form.emailHint");
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return;
    }
    setBusy(true);
    try {
      const asOffline = offlineOption && offline;
      const created = await submit({
        fullName: name.trim(),
        phone: phone.trim(),
        email: email.trim(),
        ...(offlineOption ? { offlineAdmission: asOffline } : {}),
      });
      setName("");
      setPhone("");
      setEmail("");
      onOpenChange(false);
      onCreated(created, asOffline);
    } catch (error) {
      const code = error instanceof ApiError ? error.code : null;
      if (code === "DUPLICATE_PHONE") {
        setErrors({ phone: t("staffPeople.form.phoneInUse") });
      } else if (code === "DUPLICATE_EMAIL") {
        setErrors({ email: t("staffPeople.form.emailInUse") });
      } else {
        setErrors({ form: t("common.error.body") });
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={title} description={t("staffPeople.form.noPassword")}>
      <form className="grid gap-4" onSubmit={onSubmit} noValidate>
        <Field label={t("staffPeople.form.name")} error={errors.name} required>
          <TextInput autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} invalid={Boolean(errors.name)} />
        </Field>
        <Field label={t("staffPeople.form.phone")} hint={t("staffPeople.form.phoneFormat")} error={errors.phone} required>
          <TextInput
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            invalid={Boolean(errors.phone)}
          />
        </Field>
        <Field label={t("staffPeople.form.email")} error={errors.email} required>
          <TextInput
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            invalid={Boolean(errors.email)}
          />
        </Field>
        {offlineOption ? (
          <label className="flex min-h-11 cursor-pointer items-start gap-3 rounded-control border border-border-control bg-card px-3.5 py-2.5">
            <input
              type="checkbox"
              className="mt-1 size-5 shrink-0 accent-accent"
              checked={offline}
              onChange={(event) => setOffline(event.target.checked)}
            />
            <span className="grid gap-0.5">
              <span className="text-body text-text">{t("staffOffline.desk.option")}</span>
              <span className="text-meta text-muted">{t("staffOffline.desk.optionHint")}</span>
            </span>
          </label>
        ) : null}
        {errors.form ? <p className="text-meta text-danger">{errors.form}</p> : null}
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button type="submit" loading={busy}>
            {t("staffPeople.form.submit")}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
