import type { useLocale } from "@/i18n/locale-context";

type Translate = ReturnType<typeof useLocale>["t"];

/**
 * The paper language as shown on an exam: BN and EN by name, BOTH as "Both". No language (null) or an
 * unknown value gives null, and then nothing is shown: no row, no badge, no placeholder.
 */
export function paperLanguageLabel(value: string | null | undefined, t: Translate): string | null {
  if (value === "BN") {
    return t("catalog.exam.langBn");
  }
  if (value === "EN") {
    return t("catalog.exam.langEn");
  }
  if (value === "BOTH") {
    return t("common.paperLanguage.both");
  }
  return null;
}
