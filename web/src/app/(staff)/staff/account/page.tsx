"use client";

import { Suspense, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { EmailCard, PasswordForm, PhoneCard } from "@/components/staff/ops/account-forms";
import { ALL_STAFF, RequireStaff, useRole } from "@/lib/auth/guards";
import { useSession } from "@/lib/auth/session-context";
import { useLocale } from "@/i18n/locale-context";

export default function StaffAccountPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={3} />}>
      <RequireStaff roles={ALL_STAFF}>
        <Account />
      </RequireStaff>
    </Suspense>
  );
}

function Account() {
  const { t } = useLocale();
  const { session, refreshMe, logout } = useSession();
  const [confirmOut, setConfirmOut] = useState(false);
  const { role } = useRole();

  if (session.status !== "authenticated") {
    return null;
  }
  const user = session.user;
  const roleLabel = t(`common.roles.${role}`);

  return (
    <div className="grid gap-5">
      <PageHeader title={t("staffOps.account.title")} breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffOps.account.title") }]} />

      <Card>
        <dl className="grid gap-4 sm:grid-cols-2">
          <div className="grid gap-1">
            <dt className="text-sm text-muted">{t("staffOps.account.name")}</dt>
            <dd className="text-text">{user.fullName}</dd>
          </div>
          <div className="grid gap-1">
            <dt className="text-sm text-muted">{t("staffOps.account.role")}</dt>
            <dd className="text-text">{roleLabel}</dd>
          </div>
        </dl>
        <p className="mt-3 text-sm text-muted">{t("staffOps.account.readOnlyHint")}</p>
      </Card>

      {user.email ? (
        <EmailCard email={user.email} verifiedAt={user.emailVerifiedAt} onVerified={refreshMe} />
      ) : null}
      <PhoneCard phone={user.phone} onSaved={refreshMe} />
      <PasswordForm />

      <Card>
        <Button variant="danger" onClick={() => setConfirmOut(true)}>
          {t("staffOps.account.logout")}
        </Button>
        <ConfirmDialog
          open={confirmOut}
          onOpenChange={setConfirmOut}
          title={t("staffOps.account.logoutTitle")}
          body={t("staffOps.account.logoutBody")}
          confirmLabel={t("staffOps.account.logoutConfirm")}
          onConfirm={async () => {
            await logout();
          }}
        />
      </Card>
    </div>
  );
}
