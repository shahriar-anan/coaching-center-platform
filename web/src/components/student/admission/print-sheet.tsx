"use client";

import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { PrintArea } from "@/components/ui/print-area";
import { useLocale, type Locale } from "@/i18n/locale-context";
import { ADMISSION_PHOTO_PATH, type AdmissionForm, type AdmissionFormField, type AdmissionView } from "@/lib/api/exam/offline-admission";
import { formatDate, toBengaliDigits } from "@/lib/format";
import { useAuthorizedObjectUrl } from "@/lib/hooks/use-authorized-object-url";
import { cn } from "@/components/ui/cn";
import { ADDRESS_FIELDS, PERSONAL_FIELDS } from "./fields";

/* The A4 sheet follows the paper registration form: the centre, the photo box, the personal block, the address block, the
 * date and the two signature lines. The print rules in globals.css print only the marked area, so the site chrome stays
 * off the paper. */
const SHEET_CSS = "@page { size: A4; margin: 12mm; }";

/** The date of birth as the paper prints it: day/month/year, in the locale's digits. */
function dateOfBirthText(value: string | null | undefined, locale: Locale): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? "");
  if (!match) {
    return value ?? "";
  }
  const text = `${match[3]}/${match[2]}/${match[1]}`;
  return locale === "bn" ? toBengaliDigits(text) : text;
}

/** The printable registration form for a student (plan 6.2). Bangla or English labels follow the page's language. */
export function PrintSheet({ admission }: { admission: AdmissionView }) {
  const { t, locale } = useLocale();
  const photo = useAuthorizedObjectUrl(admission.hasPhoto ? ADMISSION_PHOTO_PATH : null);
  // The day the sheet is printed, when the form has not been submitted yet.
  const [printedOn] = useState(() => Date.now());
  const form = admission.form;

  function valueOf(key: AdmissionFormField): string {
    if (key === "dateOfBirth") {
      return dateOfBirthText(form.dateOfBirth, locale);
    }
    const value = form[key];
    return typeof value === "string" ? value : "";
  }

  function row(key: AdmissionFormField, wide = false): ReactNode {
    return (
      <div key={key} className={cn("grid gap-0.5 border-b border-neutral-800 pb-1", wide && "col-span-2")}>
        <dt className="text-[11px] leading-tight text-neutral-600">{t(`admission.field.${key}`)}</dt>
        <dd className="min-h-5 whitespace-pre-line text-[13px] font-medium leading-snug">{valueOf(key) || " "}</dd>
      </div>
    );
  }

  return (
    <div className="grid gap-4">
      <style>{SHEET_CSS}</style>
      <div className="flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Button variant="secondary" href="/admission">
          {t("admission.print.back")}
        </Button>
        <Button onClick={() => window.print()}>{t("admission.print.print")}</Button>
      </div>
      <div className="overflow-x-auto">
        <PrintArea>
          <article className="mx-auto grid w-[210mm] max-w-none gap-5 bg-white p-[10mm] text-[13px] text-black print:w-auto print:p-0">
            <header className="flex items-start justify-between gap-4 border-b-2 border-black pb-3">
              <div className="grid gap-1">
                <p className="text-[22px] font-bold tracking-wide">{t("admission.print.centre")}</p>
                <p className="text-[14px] font-semibold">{t("admission.print.formTitle")}</p>
              </div>
              <div className="flex shrink-0 flex-col items-end gap-3">
                <div className="flex h-[45mm] w-[35mm] items-center justify-center overflow-hidden border border-black">
                  {photo ? (
                    <img src={photo} alt={t("admission.section.photo")} className="size-full object-cover" />
                  ) : (
                    <span className="text-[11px] text-neutral-600">{t("admission.print.photo")}</span>
                  )}
                </div>
                <p className="text-[12px]">
                  <span className="font-semibold">{t("admission.print.date")}: </span>
                  {formatDate(admission.submittedAt ?? printedOn, locale)}
                </p>
              </div>
            </header>

            <section aria-labelledby="print-personal" className="grid gap-2">
              <h2 id="print-personal" className="text-[13px] font-bold uppercase tracking-wide">
                {t("admission.print.personal")}
              </h2>
              <dl className="grid grid-cols-2 gap-x-5 gap-y-3">
                {PERSONAL_FIELDS.map((key) =>
                  key === "gender" ? (
                    <div key={key} className="grid gap-0.5 border-b border-neutral-800 pb-1 col-span-2">
                      <dt className="text-[11px] leading-tight text-neutral-600">{t("admission.field.gender")}</dt>
                      <dd className="min-h-5 text-[13px] font-medium">{genderMarks(form, t)}</dd>
                    </div>
                  ) : (
                    row(key)
                  ),
                )}
              </dl>
            </section>

            <section aria-labelledby="print-address" className="grid gap-2">
              <h2 id="print-address" className="text-[13px] font-bold uppercase tracking-wide">
                {t("admission.print.address")}
              </h2>
              <dl className="grid grid-cols-1 gap-y-3">{ADDRESS_FIELDS.map((key) => row(key, true))}</dl>
            </section>

            <div className="mt-8 grid grid-cols-2 gap-10 pt-6">
              <div className="grid gap-1">
                <div className="border-t border-black" />
                <p className="text-[12px]">{t("admission.print.signature")}</p>
              </div>
              <div className="grid gap-1">
                <div className="border-t border-black" />
                <p className="text-[12px]">{t("admission.print.officer")}</p>
              </div>
            </div>

            <footer className="grid gap-0.5 border-t border-neutral-400 pt-2 text-center text-[10px] text-neutral-700">
              <p>{t("admission.print.centreAddress")}</p>
              <p>{t("admission.print.contact")}</p>
            </footer>
          </article>
        </PrintArea>
      </div>
    </div>
  );
}

/** Both gender options with the chosen one ticked, as the paper form has them. */
function genderMarks(form: AdmissionForm, t: (key: string) => string): string {
  const male = form.gender === "MALE";
  const female = form.gender === "FEMALE";
  return `${male ? "✓" : "☐"} ${t("admission.gender.male")}     ${female ? "✓" : "☐"} ${t("admission.gender.female")}`;
}
