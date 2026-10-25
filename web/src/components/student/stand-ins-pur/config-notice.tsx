// Stand-in for PUR-FND's ConfigNotice. Delete at the switch to @/components/ui/purchase.
import { useLocale } from "@/i18n/locale-context";

export function ConfigNotice({ show }: { show: boolean }) {
  const { t } = useLocale();
  if (!show) return null;
  return (
    <p role="status" className="rounded-control border border-warn bg-card p-3 text-body text-text">
      {t("purchase.shared.checkoutOff")}
    </p>
  );
}
