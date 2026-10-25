"use client";

import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { doorPath } from "./catalog-helpers";

/** The two doors as buttons, for empty states that tell a student where to start. */
export function DoorActions() {
  const { t } = useLocale();
  return (
    <div className="flex flex-wrap justify-center gap-2">
      <Button variant="secondary" href={doorPath("mcq")}>{t("home.doorMcqTitle")}</Button>
      <Button variant="secondary" href={doorPath("written")}>{t("home.doorWrittenTitle")}</Button>
    </div>
  );
}
