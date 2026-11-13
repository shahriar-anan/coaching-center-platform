"use client";

import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { useLocale } from "@/i18n/locale-context";

/**
 * The activation code of a desk admission, in large type, with the steps to read out to the student (plan 6.3). The code is shown once.
 */
export function DeskActivationDialog({
  open,
  onClose,
  name,
  phone,
  code,
}: {
  open: boolean;
  onClose: () => void;
  name: string;
  phone: string;
  code: string;
}) {
  const { t } = useLocale();
  const steps = [t("staffOffline.desk.step1"), t("staffOffline.desk.step2"), t("staffOffline.desk.step3")];
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          onClose();
        }
      }}
      title={t("staffOffline.desk.codeTitle")}
      description={t("staffOffline.desk.codeBody", { name })}
      footer={<Button onClick={onClose}>{t("common.oneTimeCode.done")}</Button>}
    >
      <div className="grid gap-5">
        <p className="grid gap-1 text-body text-text">
          <span className="text-meta text-muted">{t("staffOffline.desk.phone")}</span>
          <span className="font-latin">{phone}</span>
        </p>
        <p
          aria-label={t("staffOffline.desk.codeLabel")}
          className="break-all rounded-card border border-border-control bg-bg py-5 text-center font-latin text-[clamp(2.25rem,12vw,3.5rem)] font-bold tracking-[0.12em] text-text"
        >
          {code}
        </p>
        <div className="grid gap-2">
          <h3 className="text-card-title text-text">{t("staffOffline.desk.stepsTitle")}</h3>
          <ol className="grid list-decimal gap-1.5 ps-5 text-body text-text">
            {steps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>
        </div>
        <p className="rounded-control bg-warn/10 px-3.5 py-2.5 text-meta text-warn">{t("common.oneTimeCode.once")}</p>
      </div>
    </Dialog>
  );
}
