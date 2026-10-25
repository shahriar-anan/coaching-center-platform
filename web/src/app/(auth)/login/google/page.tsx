"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState, type ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { destinationFor, useSession } from "@/lib/auth/session-context";
import { FormError } from "@/app/(auth)/_components/auth-ui";

const KNOWN_ERRORS = [
  "GOOGLE_ACCOUNT_INACTIVE",
  "GOOGLE_ROLE_NOT_ALLOWED",
  "GOOGLE_EMAIL_NOT_VERIFIED",
  "GOOGLE_EMAIL_MISSING",
  "GOOGLE_CANCELLED",
] as const;

/**
 * Where the API sends the browser after Google sign-in. Success takes the refresh
 * cookie, signs the student in and routes on; a missing phone goes to set-phone.
 * Errors come back as `?error=CODE`. Google stays off until it is configured (U10).
 */
function GoogleReturn() {
  const { t } = useLocale();
  const router = useRouter();
  const params = useSearchParams();
  const { acceptToken } = useSession();
  const next = params.get("next");
  const errorCode = params.get("error");
  const [failure, setFailure] = useState<ReactNode>(null);
  const started = useRef(false);

  useEffect(() => {
    if (errorCode || started.current) {
      return;
    }
    started.current = true;
    (async () => {
      try {
        const tokens = await api.refresh();
        const user = await acceptToken(tokens.accessToken, { redirect: false });
        if (user.phoneRequired) {
          router.replace(`/set-phone${next ? `?next=${encodeURIComponent(next)}` : ""}`);
          return;
        }
        router.replace(destinationFor(user, next));
      } catch {
        setFailure(t("auth.google.errors.generic"));
      }
    })();
  }, [acceptToken, errorCode, next, router, t]);

  const message = errorCode
    ? (KNOWN_ERRORS as readonly string[]).includes(errorCode)
      ? t(`auth.google.errors.${errorCode}`)
      : t("auth.google.errors.generic")
    : failure;

  return (
    <>
      <PageHeader
        title={t("auth.google.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t("auth.google.title") }]}
      />
      <Card padding="lg" className="grid gap-5">
        {message ? (
          <>
            <FormError>{message}</FormError>
            <Link href="/login" className="text-center font-semibold text-text-accent underline underline-offset-4">
              {t("auth.google.back")}
            </Link>
          </>
        ) : (
          <div aria-busy="true" className="grid gap-3">
            <p className="text-[15px] text-muted">{t("auth.google.working")}</p>
            <Skeleton variant="text" count={2} />
          </div>
        )}
      </Card>
    </>
  );
}

export default function GoogleReturnPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <GoogleReturn />
    </Suspense>
  );
}
