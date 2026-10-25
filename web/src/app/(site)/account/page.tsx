"use client";

import { Skeleton } from "@/components/ui/skeleton";
import { Suspense, useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import { RequireStudent } from "@/lib/auth/guards";
import {
  isNotDeployed,
  useChangeName,
  useResendEmailVerification,
  useVerifyEmail,
} from "@/lib/api/exam/account";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { LocalePill, ThemePill } from "@/components/shell/pills";
import { codeOf, CODE_DEAD, FormError, focusFirst } from "@/app/(auth)/_components/auth-ui";
import { PasswordForm } from "@/app/(site)/account/_components/password-form";
import { PhoneDialog } from "@/app/(site)/account/_components/phone-dialog";

export default function AccountPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStudent>
        <AccountView />
      </RequireStudent>
    </Suspense>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 py-3">
      <span className="text-meta text-muted">{label}</span>
      <div className="flex min-w-0 flex-wrap items-center gap-2 text-body text-text">{children}</div>
    </div>
  );
}

function AccountView() {
  const { t } = useLocale();
  const { session, logout, refreshMe } = useSession();
  const { toast } = useToast();
  const resend = useResendEmailVerification();
  const verify = useVerifyEmail();
  const changeName = useChangeName();
  const [nameOpen, setNameOpen] = useState(false);
  const [name, setName] = useState("");
  const [nameError, setNameError] = useState<string | null>(null);
  const [codeSent, setCodeSent] = useState(false);
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState<string | null>(null);
  const [phoneOpen, setPhoneOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [logoutOpen, setLogoutOpen] = useState(false);

  if (session.status !== "authenticated") {
    return null;
  }
  const user = session.user;
  const verified = Boolean(user.emailVerifiedAt);

  async function sendCode() {
    setCodeError(null);
    try {
      await resend.mutateAsync({ email: user.email ?? "" });
      setCodeSent(true);
      toast({ title: t("account.profile.codeSent"), tone: "info" });
    } catch {
      setCodeError(t("common.states.errorBody"));
    }
  }

  function openName() {
    setName(user.fullName ?? "");
    setNameError(null);
    setNameOpen(true);
  }

  async function saveName() {
    const next = name.trim();
    if (next.length < 1 || next.length > 100) {
      setNameError(t("account.profile.nameInvalid"));
      focusFirst(["account-name"]);
      return;
    }
    setNameError(null);
    try {
      await changeName.mutateAsync({ fullName: next });
      await refreshMe();
      toast({ title: t("account.profile.nameSaved"), tone: "ok" });
      setNameOpen(false);
    } catch (err) {
      setNameError(isNotDeployed(err) ? t("account.profile.nameNotReady") : t("common.states.errorBody"));
      focusFirst(["account-name"]);
    }
  }

  async function verifyCode() {
    setCodeError(null);
    if (!code.trim()) {
      setCodeError(t("auth.register.fieldRequired"));
      focusFirst(["account-code"]);
      return;
    }
    try {
      await verify.mutateAsync({ code: code.trim() });
      await refreshMe();
      toast({ title: t("account.profile.verified"), tone: "ok" });
      setCodeSent(false);
      setCode("");
    } catch (err) {
      const cd = codeOf(err);
      setCodeError(cd && CODE_DEAD.has(cd) ? t("auth.reset.invalidCode") : t("common.states.errorBody"));
      focusFirst(["account-code"]);
    }
  }

  return (
    <>
      <PageHeader title={t("account.title")} />

      <Card padding="lg" className="divide-y divide-border">
        <Row label={t("account.profile.name")}>
          <span className="font-semibold">{user.fullName}</span>
          {nameOpen ? null : (
            <Button variant="ghost" onClick={openName}>
              {t("account.profile.editName")}
            </Button>
          )}
        </Row>
        {nameOpen ? (
          <div className="grid gap-3 pb-3">
            <Field label={t("account.profile.nameLabel")} hint={t("account.profile.nameHelp")} error={nameError ?? undefined}>
              <TextInput
                id="account-name"
                name="fullName"
                autoComplete="name"
                maxLength={100}
                value={name}
                onChange={(e) => setName(e.target.value)}
                invalid={Boolean(nameError)}
              />
            </Field>
            <div className="grid gap-2 sm:grid-cols-2">
              <Button onClick={saveName} loading={changeName.isPending}>
                {t("account.profile.nameSave")}
              </Button>
              <Button variant="secondary" onClick={() => setNameOpen(false)}>
                {t("account.profile.nameCancel")}
              </Button>
            </div>
          </div>
        ) : null}

        <Row label={t("account.profile.email")}>
          <span className="break-all">{user.email}</span>
          {verified ? (
            <Pill tone="ok">{t("account.profile.emailVerified")}</Pill>
          ) : (
            <Pill tone="warn">{t("account.profile.emailUnverified")}</Pill>
          )}
        </Row>
        {verified ? null : (
          <div className="grid gap-3 py-3">
            {codeSent ? (
              <div className="grid gap-3">
                <Field label={t("account.profile.codeLabel")} error={codeError ?? undefined}>
                  <TextInput
                    id="account-code"
                    name="verificationCode"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                    invalid={Boolean(codeError)}
                  />
                </Field>
                <Button onClick={verifyCode} loading={verify.isPending} fullWidth>
                  {t("account.profile.verifySubmit")}
                </Button>
              </div>
            ) : (
              <div className="grid gap-2">
                {codeError ? <FormError>{codeError}</FormError> : null}
                <Button variant="secondary" onClick={sendCode} loading={resend.isPending} fullWidth>
                  {t("account.profile.sendCode")}
                </Button>
              </div>
            )}
          </div>
        )}

        <Row label={t("account.profile.phone")}>
          <span className="tabular-nums">{user.phone ?? t("account.profile.noPhone")}</span>
          {user.phone ? (
            <Button variant="secondary" onClick={() => setPhoneOpen(true)} className="min-h-10 px-4">
              {t("account.profile.changePhone")}
            </Button>
          ) : null}
        </Row>

        <Row label={t("account.profile.studentCode")}>
          <span className="font-latin tabular-nums">{user.studentCode ?? "—"}</span>
        </Row>

        <Row label={t("account.profile.preferences")}>
          <LocalePill />
          <ThemePill />
        </Row>
      </Card>

      <section className="mt-6 grid gap-3" aria-labelledby="account-password-title">
        <h2 id="account-password-title" className="text-xl font-bold text-text">
          {t("account.password.title")}
        </h2>
        <Card padding="lg">
          {passwordOpen ? (
            <PasswordForm onDone={() => setPasswordOpen(false)} />
          ) : (
            <Button variant="secondary" fullWidth onClick={() => setPasswordOpen(true)}>
              {t("account.password.change")}
            </Button>
          )}
        </Card>
      </section>

      <div className="mt-6">
        <Card href="/account/payments">
          <span className="font-bold">{t("account.payments.link")}</span>
        </Card>
      </div>

      <div className="mt-8">
        <Button variant="secondary" fullWidth onClick={() => setLogoutOpen(true)}>
          {t("account.logout.button")}
        </Button>
      </div>

      <PhoneDialog open={phoneOpen} onOpenChange={setPhoneOpen} />

      <ConfirmDialog
        open={logoutOpen}
        onOpenChange={setLogoutOpen}
        title={t("account.logout.title")}
        body={t("account.logout.body")}
        confirmLabel={t("account.logout.confirm")}
        onConfirm={() => logout()}
      />
    </>
  );
}
