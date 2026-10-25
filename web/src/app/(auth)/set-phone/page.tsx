"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState, type FormEvent } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";
import { destinationFor, safeStudentNext, useSession } from "@/lib/auth/session-context";
import { loginRedirect } from "@/lib/auth/guards";
import { useSetPhone } from "@/lib/api/exam/account";
import { codeOf, FormError, focusFirst } from "@/app/(auth)/_components/auth-ui";
import { isPhone, normalizePhone } from "@/app/(auth)/_components/validate";

/** Shown once, for a Google sign-up with no phone. The phone can be saved only once. */
function SetPhoneForm() {
  const { t } = useLocale();
  const router = useRouter();
  const params = useSearchParams();
  const { session, refreshMe } = useSession();
  const next = params.get("next");
  const [phone, setPhone] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const mutation = useSetPhone();

  // Not for this page: signed out goes to login; a phone already on file goes on.
  useEffect(() => {
    if (session.status === "anonymous") {
      router.replace(loginRedirect("/set-phone", next ? `next=${encodeURIComponent(next)}` : "", false));
    } else if (session.status === "authenticated" && !session.user.phoneRequired) {
      router.replace(destinationFor(session.user, next));
    }
  }, [session, next, router]);

  if (session.status !== "authenticated" || !session.user.phoneRequired) {
    return (
      <Card padding="lg" className="grid gap-3" aria-busy="true">
        <Skeleton variant="text" count={2} />
      </Card>
    );
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    if (!phone.trim()) {
      setFieldError(t("auth.register.fieldRequired"));
      focusFirst(["set-phone-input"]);
      return;
    }
    if (!isPhone(phone)) {
      setFieldError(t("auth.setPhone.invalid"));
      focusFirst(["set-phone-input"]);
      return;
    }
    setFieldError(null);
    try {
      await mutation.mutateAsync({ phone: normalizePhone(phone) });
      await refreshMe();
      router.replace(safeStudentNext(next) ?? "/");
    } catch (err) {
      const code = codeOf(err);
      if (code === "PHONE_ALREADY_SET") {
        await refreshMe();
        router.replace(safeStudentNext(next) ?? "/");
        return;
      }
      if (code === "DUPLICATE_PHONE") {
        setFieldError(t("auth.setPhone.duplicate"));
        focusFirst(["set-phone-input"]);
      } else {
        setFormError(t("common.states.errorBody"));
      }
    }
  }

  return (
    <>
      <PageHeader
        title={t("auth.setPhone.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t("auth.setPhone.breadcrumb") }]}
      />
      <Card padding="lg" className="grid gap-5">
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <Field label={t("auth.setPhone.phone")} hint={t("auth.setPhone.help")} error={fieldError ?? undefined} required>
            <TextInput
              id="set-phone-input"
              name="phone"
              type="tel"
              inputMode="numeric"
              autoComplete="tel-national"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              invalid={Boolean(fieldError)}
            />
          </Field>
          {formError ? <FormError>{formError}</FormError> : null}
          <Button type="submit" fullWidth loading={mutation.isPending}>
            {t("auth.setPhone.submit")}
          </Button>
        </form>
      </Card>
    </>
  );
}

export default function SetPhonePage() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <SetPhoneForm />
    </Suspense>
  );
}
