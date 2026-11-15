"use client";

import { MessageCircle } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";

/**
 * Opens a batch's or a package's WhatsApp group or channel in a new tab (15 Nov). The link comes from the API only for a student who is
 * onboarded to the batch, or who holds live access to the published package, so nobody else ever sees it.
 */
export function WhatsAppJoinLink({ url }: { url: string }) {
  const { t } = useLocale();
  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex min-h-10 w-fit items-center gap-2 rounded-control border border-border-control bg-card px-4 text-label text-text transition-colors hover:bg-bg"
    >
      <MessageCircle aria-hidden="true" className="size-4 text-[#25D366]" />
      {t("common.whatsapp.join")}
      <span className="sr-only">{t("common.whatsapp.newTab")}</span>
    </a>
  );
}
