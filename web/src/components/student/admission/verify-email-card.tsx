"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useResendEmailVerification, useVerifyEmail } from "@/lib/api/exam/account";
import { useSession } from "@/lib/auth/session-context";

/**
 * Lets a desk student confirm the email from their form, while the admission is still under review. Their home page and
 * account page are closed to them until they are admitted, so this is the only place they can do it. Hidden once verified.
 */
export function VerifyEmailCard({ email }: { email: string | null }) {
  const { t } = useLocale();
  const { session } = useSession();
  const resend = useResendEmailVerification();
  const verify = useVerifyEmail();
  const [code, setCode] = useState("");
  const [sent, setSent] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const verified = session.status === "authenticated" && Boolean(session.user.emailVerifiedAt);
  if (verified || !email) {
    return null;
  }

  async function sendCode() {
    setError(null);
    try {
      await resend.mutateAsync({ email: email ?? "" });
      setSent(true);
    } catch {
      setError(t("admission.verifyEmail.sendFailed"));
    }
  }

  async function confirm(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await verify.mutateAsync({ code: code.trim() });
      setDone(true);
    } catch {
      setError(t("admission.verifyEmail.wrongCode"));
    }
  }

  return (
    <Card padding="md" className="grid gap-3">
      <h2 className="text-section font-semibold text-text">{t("admission.verifyEmail.title")}</h2>
      {done ? (
        <p className="text-body text-ok">{t("admission.verifyEmail.done")}</p>
      ) : (
        <>
          <p className="text-body text-muted">{t("admission.verifyEmail.body", { email })}</p>
          {!sent ? (
            <div>
              <Button type="button" variant="secondary" loading={resend.isPending} onClick={() => void sendCode()}>
                {t("admission.verifyEmail.send")}
              </Button>
            </div>
          ) : (
            <form className="grid gap-3" onSubmit={(event) => void confirm(event)}>
              <label className="grid gap-1.5 text-body text-text">
                {t("admission.verifyEmail.code")}
                <TextInput
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                />
              </label>
              <div>
                <Button type="submit" loading={verify.isPending} disabled={code.trim().length === 0}>
                  {t("admission.verifyEmail.verify")}
                </Button>
              </div>
            </form>
          )}
        </>
      )}
      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}
    </Card>
  );
}
