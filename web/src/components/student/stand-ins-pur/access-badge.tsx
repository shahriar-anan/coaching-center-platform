// Stand-in for PUR-FND's AccessBadge. Delete at the switch to @/components/ui/purchase.
import { Pill } from "@/components/ui/pill";
import { useLocale } from "@/i18n/locale-context";

const VIA_KEY = {
  MODULE: "purchase.shared.viaModule",
  EXAM: "purchase.shared.viaExam",
  ROSTER: "purchase.shared.viaRoster",
  MANUAL: "purchase.shared.viaManual",
} as const;

export function AccessBadge({ via }: { via: keyof typeof VIA_KEY }) {
  const { t } = useLocale();
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <Pill tone="ok">{t("purchase.shared.owned")}</Pill>
      <span className="text-meta text-muted">{t(VIA_KEY[via])}</span>
    </span>
  );
}
