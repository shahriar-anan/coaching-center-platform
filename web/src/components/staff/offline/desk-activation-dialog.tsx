"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { useLocale, type Locale } from "@/i18n/locale-context";
import { toBengaliDigits } from "@/lib/format";
import { useActivationCode, useRenewActivationCode, type ActivationCodeView } from "@/lib/api/exam/offline-admin";
import { useServerNow } from "@/lib/time/clock";

/** A code the dialog already holds: the one a desk start or a new code made, with the time it stops working. */
export type IssuedCode = { code: string; expiresAt: string };

/** What the code dialog is opened with: the admission, its phone, and the code when the desk start just made it. */
export type ShownCode = { admissionId: string; phone: string; initial?: IssuedCode };

type View =
  | { kind: "loading" }
  | { kind: "error" }
  | { kind: "activated" }
  | { kind: "expired" }
  | { kind: "live"; code: string; expiresAt: string };

/** The view from what the dialog holds, then what the server said. A code the dialog made always wins. */
function viewOf(issued: IssuedCode | null, fetched: ActivationCodeView | undefined, failed: boolean): View {
  if (issued) {
    return { kind: "live", ...issued };
  }
  if (failed) {
    return { kind: "error" };
  }
  if (!fetched) {
    return { kind: "loading" };
  }
  if (fetched.activated) {
    return { kind: "activated" };
  }
  if (fetched.code && fetched.expiresAt) {
    return { kind: "live", code: fetched.code, expiresAt: fetched.expiresAt };
  }
  return { kind: "expired" };
}

/** Minutes and seconds left, rounded up so the last second still shows: "9:42". Bangla digits in the Bangla page. */
function clockText(ms: number, locale: Locale): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const text = `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
  return locale === "bn" ? toBengaliDigits(text) : text;
}

/**
 * The activation code of a desk admission (plan 6.3): the code in large type, the phone, a live countdown to the moment it stops
 * working, and the steps to read out. Once it runs out, the dialog offers a new code. Without `initial` it reads the live code from the
 * server, so "Show code" on the queue always shows the code that works now.
 */
export function DeskActivationDialog({
  admissionId,
  phone,
  initial,
  onClose,
}: {
  admissionId: string;
  phone: string;
  initial?: IssuedCode;
  onClose: () => void;
}) {
  const { t, locale } = useLocale();
  const [issued, setIssued] = useState<IssuedCode | null>(initial ?? null);
  const [renewFailed, setRenewFailed] = useState(false);
  const fetched = useActivationCode(admissionId, !initial);
  const renew = useRenewActivationCode(admissionId);
  const now = useServerNow(1000);

  const view = viewOf(issued, fetched.data, fetched.isError);
  const left = view.kind === "live" ? Date.parse(view.expiresAt) - now : 0;
  const ended = view.kind === "expired" || (view.kind === "live" && left <= 0);

  function newCode() {
    setRenewFailed(false);
    renew.mutate(undefined, {
      onSuccess: (next) => setIssued({ code: next.code, expiresAt: next.expiresAt }),
      onError: () => setRenewFailed(true),
    });
  }

  return (
    <Dialog
      open
      onOpenChange={(next) => {
        if (!next) {
          onClose();
        }
      }}
      title={t("staffOffline.desk.codeTitle")}
      description={view.kind === "live" && !ended ? t("staffOffline.desk.codeBody") : undefined}
      footer={<Button onClick={onClose}>{t("common.actions.close")}</Button>}
    >
      <div className="grid gap-5">
        <p className="grid gap-1 text-body text-text">
          <span className="text-meta text-muted">{t("staffOffline.desk.phone")}</span>
          <span className="font-latin">{phone}</span>
        </p>

        {view.kind === "loading" ? <p className="text-body text-muted">{t("staffOffline.desk.loading")}</p> : null}

        {view.kind === "error" ? (
          <div className="grid gap-2">
            <p role="alert" className="text-body text-danger">
              {t("staffOffline.desk.loadFailed")}
            </p>
            <Button variant="secondary" onClick={() => void fetched.refetch()}>
              {t("common.actions.retry")}
            </Button>
          </div>
        ) : null}

        {view.kind === "activated" ? <p className="text-body text-text">{t("staffOffline.desk.activated")}</p> : null}

        {view.kind === "live" && !ended ? (
          <>
            <p
              aria-label={t("staffOffline.desk.codeLabel")}
              className="break-all rounded-card border border-border-control bg-bg py-5 text-center font-latin text-[clamp(2.25rem,12vw,3.5rem)] font-bold tracking-[0.12em] text-text"
            >
              {view.code}
            </p>
            <p className="text-center text-body tabular-nums text-text">
              {t("staffOffline.desk.expiresIn", { time: clockText(left, locale) })}
            </p>
            <div className="grid gap-2">
              <h3 className="text-card-title text-text">{t("staffOffline.desk.stepsTitle")}</h3>
              <ol className="grid list-decimal gap-1.5 ps-5 text-body text-text">
                <li>{t("staffOffline.desk.step1")}</li>
                <li>{t("staffOffline.desk.step2")}</li>
                <li>{t("staffOffline.desk.step3")}</li>
                <li>{t("staffOffline.desk.step4")}</li>
              </ol>
            </div>
          </>
        ) : null}

        {view.kind === "expired" || (view.kind === "live" && ended) ? (
          <div className="grid gap-3 rounded-card border border-warn/40 bg-warn/10 p-4">
            <p role="alert" className="text-body font-semibold text-warn">
              {t("staffOffline.desk.expired")}
            </p>
            <p className="text-meta text-text">{t("staffOffline.desk.expiredBody")}</p>
            {renewFailed ? (
              <p role="alert" className="text-meta text-danger">
                {t("staffOffline.desk.newCodeFailed")}
              </p>
            ) : null}
            <Button onClick={newCode} loading={renew.isPending} className="justify-self-start">
              {t("staffOffline.desk.newCode")}
            </Button>
          </div>
        ) : null}
      </div>
    </Dialog>
  );
}
