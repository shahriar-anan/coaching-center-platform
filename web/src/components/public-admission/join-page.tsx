"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { CODE_DEAD, FormError, codeOf, focusFirst } from "@/app/(auth)/_components/auth-ui";
import { readCodeFromHash, scrubHash } from "@/app/(auth)/_components/code-hash";
import { useJoinAdmission } from "@/lib/api/exam/my-admission";

/**
 * `/admission/join` (plan B, C): an account that was already there joins the batch with the join code from the email. A link in the
 * email carries the code in its address fragment, which fills the box, as on the activation page. On success the student goes to
 * My admission.
 */
export function JoinPage() {
  const { t } = useLocale();
  const router = useRouter();
  const join = useJoinAdmission();
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | null>(null);

  useEffect(() => {
    const fromLink = readCodeFromHash();
    if (fromLink) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- the address fragment is only readable in the browser, after load
      setCode(fromLink);
      scrubHash();
    }
  }, []);

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setFieldError(null);
    const value = code.trim();
    if (!value) {
      setFieldError(t("auth.register.fieldRequired"));
      focusFirst(["join-code"]);
      return;
    }
    join.mutateAsync(value.toUpperCase()).then(
      () => router.replace("/offline-activities"),
      (err: unknown) => {
        const cd = codeOf(err);
        if (cd && CODE_DEAD.has(cd)) {
          setError(t("publicAdmission.join.invalid"));
          focusFirst(["join-code"]);
        } else {
          setError(t("publicAdmission.join.failed"));
        }
      },
    );
  }

  return (
    <div className="grid gap-6">
      <PageHeader
        title={t("publicAdmission.join.title")}
        breadcrumbs={[{ label: t("catalog.home"), href: "/" }, { label: t("publicAdmission.join.title") }]}
      />
      <Card padding="lg" className="grid gap-5">
        <p className="text-body text-muted">{t("publicAdmission.join.lead")}</p>
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <Field label={t("publicAdmission.join.code")} hint={t("publicAdmission.join.codeHelp")} error={fieldError ?? undefined} required>
            <TextInput
              id="join-code"
              name="code"
              autoCapitalize="characters"
              spellCheck={false}
              autoComplete="one-time-code"
              value={code}
              onChange={(event) => {
                setCode(event.target.value.toUpperCase());
                setFieldError(null);
              }}
              invalid={Boolean(fieldError)}
            />
          </Field>
          {error ? <FormError>{error}</FormError> : null}
          <Button type="submit" fullWidth loading={join.isPending}>
            {t("publicAdmission.join.submit")}
          </Button>
        </form>
      </Card>
    </div>
  );
}
