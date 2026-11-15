"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useModuleWhatsApp, useSaveModuleWhatsApp } from "@/lib/api/exam/staff-catalog";

/** Group invite (chat.whatsapp.com) or channel (whatsapp.com/channel) links only, as the API takes. */
const WHATSAPP_LINK = /^https:\/\/(chat\.whatsapp\.com\/[A-Za-z0-9]{8,}|(www\.)?whatsapp\.com\/channel\/[A-Za-z0-9]{8,}\/?)(\?\S*)?$/;

/**
 * A package's WhatsApp group or channel link (15 Nov). It is saved at once, apart from the package's own changes and publishing, and is
 * given to students who hold live access once the package is published. Only the admins who manage the cover can change it.
 */
export function WhatsAppLinkCard({ moduleId }: { moduleId: string }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const current = useModuleWhatsApp(moduleId);
  const save = useSaveModuleWhatsApp(moduleId);
  const [typed, setTyped] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const saved = current.data?.url ?? "";
  const value = typed ?? saved;
  const changed = value.trim() !== saved;

  async function submit(url: string | null) {
    setError(null);
    if (url && !WHATSAPP_LINK.test(url)) {
      setError(t("staffCatalog.whatsapp.invalid"));
      return;
    }
    try {
      await save.mutateAsync(url);
      setTyped(null);
      toast({ title: url ? t("staffCatalog.whatsapp.saved") : t("staffCatalog.whatsapp.removed"), tone: "ok" });
    } catch (caught) {
      const field = caught instanceof ApiError ? caught.fieldErrors?.find((problem) => problem.field === "url") : undefined;
      setError(field ? t("staffCatalog.whatsapp.invalid") : t("staffCatalog.whatsapp.failed"));
    }
  }

  return (
    <section aria-labelledby="whatsapp-heading" className="grid max-w-3xl gap-4 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-5">
      <h2 id="whatsapp-heading" className="text-card-title font-semibold">
        {t("staffCatalog.whatsapp.heading")}
      </h2>
      {current.isPending ? (
        <Skeleton variant="text" />
      ) : (
        <Field label={t("staffCatalog.whatsapp.label")} hint={t("staffCatalog.whatsapp.hint")} error={error ?? undefined}>
          <TextInput
            type="url"
            inputMode="url"
            placeholder="https://chat.whatsapp.com/..."
            value={value}
            onChange={(event) => setTyped(event.target.value)}
            invalid={Boolean(error)}
          />
        </Field>
      )}
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" loading={save.isPending} disabled={!changed || !value.trim()} onClick={() => void submit(value.trim())}>
          {t("staffCatalog.whatsapp.save")}
        </Button>
        {saved ? (
          <Button variant="ghost" disabled={save.isPending} onClick={() => void submit(null)}>
            {t("staffCatalog.whatsapp.remove")}
          </Button>
        ) : null}
      </div>
    </section>
  );
}
