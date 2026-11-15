"use client";

import { useState, type CSSProperties, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { PrintArea } from "@/components/ui/print-area";
import { useLocale, type Locale } from "@/i18n/locale-context";
import { ADMISSION_PHOTO_PATH, type AdmissionForm, type AdmissionFormField, type AdmissionView } from "@/lib/api/exam/offline-admission";
import { formatDate, toBengaliDigits } from "@/lib/format";
import { useAuthorizedObjectUrl } from "@/lib/hooks/use-authorized-object-url";
import { cn } from "@/components/ui/cn";
import { ADDRESS_FIELDS, PERSONAL_FIELDS } from "./fields";

/* The A4 sheet follows the SYMAM LAW registration form on paper: a charcoal header band with a teal sweep and the school
 * name, the "Registration Form" title with the photo box, the personal and address blocks as label-and-box rows, the date,
 * the two signature lines and a footer band. The fields are the ones the form asks for; nothing is added to or taken from them.
 * The page has no margin so the bands run to the edge; the sheet is exactly A4 when printed. */
const SHEET_CSS = "@page { size: A4; margin: 0; }";

/** The colours of the paper form. */
const INK = "#1f2229";
const TEAL = "#3a8c7c";
const PURPLE = "#8f5cf0";
const RULE = "#a8a29e";

/** Printers drop background colours unless asked to keep them; the bands must print. */
const KEEP_COLOURS: CSSProperties = { WebkitPrintColorAdjust: "exact", printColorAdjust: "exact" };

/** The date of birth as the paper prints it: day/month/year, in the locale's digits. */
function dateOfBirthText(value: string | null | undefined, locale: Locale): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? "");
  if (!match) {
    return value ?? "";
  }
  const text = `${match[3]}/${match[2]}/${match[1]}`;
  return locale === "bn" ? toBengaliDigits(text) : text;
}

/** What the sheet prints: the form, its date and whether a photo exists. Staff print the same sheet for any admission. */
export type PrintableAdmission = Pick<AdmissionView, "form" | "hasPhoto" | "submittedAt">;

/**
 * The printable registration form (plan 6.2). Bangla or English labels follow the page's language. The student prints their own form
 * (the defaults); staff pass the admission's photo path and the page to go back to.
 */
export function PrintSheet({
  admission,
  photoPath = ADMISSION_PHOTO_PATH,
  backHref = "/admission/form",
}: {
  admission: PrintableAdmission;
  photoPath?: string;
  backHref?: string;
}) {
  const { t, locale } = useLocale();
  const photo = useAuthorizedObjectUrl(admission.hasPhoto ? photoPath : null);
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

  /** One field: the label on the left, the value in a box on the right. Address fields take the full width and more height. */
  function row(key: AdmissionFormField, wide = false): ReactNode {
    const tall = ADDRESS_FIELDS.includes(key);
    return (
      <div key={key} className={cn("grid grid-cols-[34mm_minmax(0,1fr)] items-start gap-2", wide && "col-span-2")}>
        <dt className="pt-1.5 font-serif text-[12px] font-bold leading-tight text-black">{t(`admission.field.${key}`)}</dt>
        <dd
          className={cn(
            "min-w-0 whitespace-pre-line border px-2 py-1 text-[12px] leading-snug text-black",
            tall ? "min-h-[16mm]" : "min-h-[8mm]",
          )}
          style={{ borderColor: RULE }}
        >
          {valueOf(key) || " "}
        </dd>
      </div>
    );
  }

  return (
    <div className="grid gap-4">
      <style>{SHEET_CSS}</style>
      <div className="flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Button variant="secondary" href={backHref}>
          {t("admission.print.back")}
        </Button>
        <Button onClick={() => window.print()}>{t("admission.print.print")}</Button>
      </div>
      <div className="overflow-x-auto">
        <PrintArea>
          <article
            className="mx-auto flex h-[297mm] w-[210mm] max-w-none flex-col overflow-hidden bg-white text-black print:w-[210mm]"
            style={KEEP_COLOURS}
          >
            {/* The header band: a charcoal band with a teal sweep to the right, the school name on the left. */}
            <header className="relative h-[36mm] shrink-0 overflow-hidden" style={{ background: INK, color: "#ffffff" }}>
              <div
                aria-hidden="true"
                className="absolute inset-y-0 end-0 w-[62%]"
                style={{ background: TEAL, clipPath: "polygon(18% 0, 100% 0, 100% 100%, 0 100%)" }}
              />
              <div className="relative flex h-full flex-col justify-center gap-1.5 px-[10mm]">
                <p className="font-serif text-[22px] font-bold tracking-wide">{t("admission.print.brand")}</p>
                <p className="ps-[9mm] text-[12px] font-semibold tracking-wide" style={{ color: PURPLE }}>
                  {t("admission.print.centre")}
                </p>
              </div>
            </header>

            <div className="grid min-h-0 flex-1 content-start gap-5 px-[10mm] pt-6">
              {/* The title, with the photo box on the right, as the paper has it. */}
              <div className="flex items-start justify-between gap-4">
                <h1 className="font-serif text-[26px] font-bold leading-tight" style={{ color: INK }}>
                  {t("admission.print.formTitle")}
                </h1>
                <div
                  className="flex h-[40mm] w-[32mm] shrink-0 items-center justify-center overflow-hidden border-[2.5px]"
                  style={{ borderColor: RULE }}
                >
                  {photo ? (
                    <img src={photo} alt={t("admission.section.photo")} className="size-full object-cover" />
                  ) : (
                    <span className="font-serif text-[12px] font-bold text-neutral-600">{t("admission.print.picture")}</span>
                  )}
                </div>
              </div>

              <section aria-labelledby="print-personal" className="grid gap-3">
                <div className="flex items-end justify-between gap-4">
                  <div className="grid gap-1.5">
                    <h2 id="print-personal" className="text-[14px] font-bold uppercase tracking-wide" style={{ color: INK }}>
                      {t("admission.print.personal")}
                    </h2>
                    <span className="h-[2px] w-12" style={{ background: TEAL }} />
                  </div>
                  <p className="text-[12px] font-bold uppercase tracking-wide">
                    {t("admission.print.date")}: {formatDate(admission.submittedAt ?? printedOn, locale)}
                  </p>
                </div>
                <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
                  {PERSONAL_FIELDS.map((key) =>
                    key === "gender" ? (
                      <div key={key} className="col-span-2 grid grid-cols-[34mm_minmax(0,1fr)] items-start gap-2">
                        <dt className="pt-1.5 font-serif text-[12px] font-bold leading-tight">{t("admission.field.gender")}</dt>
                        <dd className="min-h-[8mm] border px-2 py-1.5 text-[12px]" style={{ borderColor: RULE }}>
                          {genderMarks(form, t)}
                        </dd>
                      </div>
                    ) : (
                      row(key, key === "fullName")
                    ),
                  )}
                </dl>
              </section>

              <section aria-labelledby="print-address" className="grid gap-3">
                <div className="grid gap-1.5">
                  <h2 id="print-address" className="text-[14px] font-bold uppercase tracking-wide" style={{ color: INK }}>
                    {t("admission.print.address")}
                  </h2>
                  <span className="h-[2px] w-12" style={{ background: TEAL }} />
                </div>
                <dl className="grid grid-cols-1 gap-y-3">{ADDRESS_FIELDS.map((key) => row(key, true))}</dl>
              </section>
            </div>

            {/* The footer: the contacts on the left, the two signature lines on the right, then the colour bands. */}
            <footer className="mt-auto grid shrink-0 gap-6 px-[10mm] pb-5">
              <div className="grid grid-cols-2 items-end gap-10">
                <div className="grid gap-1 text-[11px] leading-snug">
                  <p className="font-serif font-bold">{t("admission.print.centreAddress")}</p>
                  <p className="font-serif font-bold">{t("admission.print.contact")}</p>
                </div>
                <div className="grid grid-cols-2 gap-6">
                  <div className="grid gap-1.5 text-center">
                    <span className="h-px w-full" style={{ background: RULE }} />
                    <p className="font-serif text-[11px] font-bold">{t("admission.print.signature")}</p>
                  </div>
                  <div className="grid gap-1.5 text-center">
                    <span className="h-px w-full" style={{ background: RULE }} />
                    <p className="font-serif text-[11px] font-bold">{t("admission.print.officer")}</p>
                  </div>
                </div>
              </div>
            </footer>
            <div className="h-[3.5mm] shrink-0" style={{ background: TEAL }} />
            <div className="h-[9mm] shrink-0" style={{ background: INK }} />
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
