"use client";

import { Button } from "@/components/ui/button";
import { CopyField } from "@/components/ui/copy-field";
import { Dialog } from "@/components/ui/dialog";
import { useLocale } from "@/i18n/locale-context";

/** Shows an activation code once. It cannot be shown again, so the dialog says so. */
export function OneTimeCodeDialog({
  open,
  code,
  personName,
  onClose,
}: {
  open: boolean;
  code: string;
  personName: string;
  onClose: () => void;
}) {
  const { t } = useLocale();
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          onClose();
        }
      }}
      title={t("common.oneTimeCode.title")}
      description={t("common.oneTimeCode.body", { name: personName })}
      footer={<Button onClick={onClose}>{t("common.oneTimeCode.done")}</Button>}
    >
      <CopyField value={code} label={t("common.oneTimeCode.label")} />
      <p className="rounded-control bg-warn/10 px-3.5 py-2.5 text-sm text-warn">
        {t("common.oneTimeCode.once")}
      </p>
    </Dialog>
  );
}
