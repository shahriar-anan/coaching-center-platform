"use client";

import Link from "next/link";
import { Clock, Mail, MapPin, Phone } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { Logo } from "@/components/shell/logo";
import { Pill } from "@/components/ui/pill";
import { cn } from "@/components/ui/cn";

/** Which footer a page shows. `none` renders nothing; the route decides it (see `footerVariant`). */
export type FooterVariant = "full" | "minimal" | "none";

/** The old site's footer links, as copied. The Google Play address is the one the old site uses. */
const APP_STORE_URL = "https://apps.apple.com/us/app/apple-store/id375380948";
const GOOGLE_PLAY_URL = "https://expo.dev/accounts/abtahi-tajwar/projects/symamlaw-mobile/builds/a0368e73-baf4-4021-8bc6-e59fc962afaa";
const SOCIAL_LINKS = [
  { key: "facebook", href: "https://www.facebook.com/profile.php?id=100065554386812" },
  { key: "whatsapp", href: "https://api.whatsapp.com/send?phone=8801601561225" },
  { key: "linkedin", href: "https://www.linkedin.com/company/symam-law/?viewAsMember=true" },
  { key: "youtube", href: "https://youtu.be/ZmrfDj6T5R4?si=aKH4z7RErGn-v7Lr" },
] as const;

const LINK =
  "inline-flex min-h-11 items-center rounded-control text-meta text-muted hover:text-text hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";
const HEADING = "text-label font-semibold text-text";
const ICON = "mt-0.5 size-4 shrink-0 text-text-accent";
const APP_BUTTON =
  "inline-flex min-h-11 items-center rounded-control border border-border bg-card px-3 text-meta font-medium text-text hover:bg-bg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";
const APP_BUTTON_OFF =
  "inline-flex min-h-11 items-center rounded-control border border-dashed border-border px-3 text-meta font-medium text-muted";
/** The store links stay off until the app is published. Set to true on the day the store pages are live. */
const APP_AVAILABLE = false;
const DEVELOPER_URL = "https://www.linkedin.com/in/shahriar-anan-a358562a3/";

/**
 * The site footer. `full` shows the old site's content (contact, company links, app links, social links,
 * copyright). `minimal` shows only the copyright and the legal links (sign-in and sign-up pages).
 * The caller decides the variant from the route; `width` is the content column of the page.
 */
export function SiteFooter({ variant, width }: { variant: Exclude<FooterVariant, "none">; width: string }) {
  const { t } = useLocale();
  const legalLinks = [
    { href: "/terms", label: t("common.footer.terms") },
    { href: "/refund", label: t("common.footer.refund") },
    { href: "/privacy", label: t("common.footer.privacy") },
  ];
  const companyLinks = [...legalLinks, { href: "/about", label: t("common.footer.about") }];

  if (variant === "minimal") {
    return (
      <footer className="border-t border-border bg-card">
        <nav
          aria-label={t("common.footer.label")}
          className={cn("mx-auto flex w-full flex-wrap items-center justify-between gap-x-5 gap-y-1 px-4 py-2", width)}
        >
          <ul className="flex flex-wrap gap-x-5">
            {legalLinks.map((link) => (
              <li key={link.href}>
                <Link href={link.href} prefetch={false} className={LINK}>
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
          <p className="text-meta text-muted">{t("common.footer.copyright")}</p>
        </nav>
      </footer>
    );
  }

  return (
    <footer className="border-t border-border bg-card">
      <nav
        aria-label={t("common.footer.label")}
        className={cn("mx-auto grid w-full gap-x-10 gap-y-10 px-4 py-10 sm:grid-cols-2 xl:grid-cols-4", width)}
      >
        <div className="grid content-start gap-3">
          <Logo />
        </div>

        <section aria-labelledby="footer-contact" className="grid content-start gap-3">
          <h2 id="footer-contact" className={HEADING}>
            {t("common.footer.contactHeading")}
          </h2>
          <ul className="grid gap-2 text-meta text-muted">
            <li className="flex items-start gap-2">
              <Phone aria-hidden="true" className={ICON} />
              <span>{t("common.footer.phone")}</span>
            </li>
            <li className="flex items-start gap-2">
              <Mail aria-hidden="true" className={ICON} />
              <a href={`mailto:${t("common.footer.email")}`} className="break-all hover:text-text hover:underline">
                {t("common.footer.email")}
              </a>
            </li>
            <li className="flex items-start gap-2">
              <Clock aria-hidden="true" className={ICON} />
              <span>{t("common.footer.hours")}</span>
            </li>
            <li className="flex items-start gap-2">
              <MapPin aria-hidden="true" className={ICON} />
              <span>{t("common.footer.location")}</span>
            </li>
          </ul>
        </section>

        <section aria-labelledby="footer-company" className="grid content-start gap-3">
          <h2 id="footer-company" className={HEADING}>
            {t("common.footer.companyHeading")}
          </h2>
          <ul className="grid gap-1 text-meta text-muted">
            {companyLinks.map((link) => (
              <li key={link.href}>
                <Link href={link.href} prefetch={false} className={LINK}>
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="footer-app" className="grid content-start gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id="footer-app" className={HEADING}>
              {t("common.footer.appHeading")}
            </h2>
            {APP_AVAILABLE ? null : <Pill tone="accent">{t("common.footer.comingSoon")}</Pill>}
          </div>
          <p className="text-meta text-muted">{t("common.footer.appText")}</p>
          <div className="flex flex-wrap gap-2">
            {APP_AVAILABLE ? (
              <>
                <a href={APP_STORE_URL} target="_blank" rel="noopener noreferrer" className={APP_BUTTON}>
                  {t("common.footer.appStore")}
                </a>
                <a href={GOOGLE_PLAY_URL} target="_blank" rel="noopener noreferrer" className={APP_BUTTON}>
                  {t("common.footer.googlePlay")}
                </a>
              </>
            ) : (
              <>
                <span aria-disabled="true" className={APP_BUTTON_OFF}>
                  {t("common.footer.appStore")}
                </span>
                <span aria-disabled="true" className={APP_BUTTON_OFF}>
                  {t("common.footer.googlePlay")}
                </span>
              </>
            )}
          </div>
        </section>
      </nav>

      <div className={cn("mx-auto flex w-full flex-col gap-3 border-t border-border px-4 py-4 sm:flex-row sm:items-center sm:justify-between", width)}>
        <nav aria-label={t("common.footer.socialLabel")}>
          <ul className="flex flex-wrap gap-x-4">
            {SOCIAL_LINKS.map((link) => (
              <li key={link.key}>
                <a href={link.href} target="_blank" rel="noopener noreferrer" className={LINK}>
                  {t(`common.footer.${link.key}`)}
                </a>
              </li>
            ))}
          </ul>
        </nav>
        <div className="grid gap-1 text-meta text-muted sm:justify-items-end">
          <p>{t("common.footer.copyright")}</p>
          <p>
            {t("common.footer.developedBy")}{" "}
            <a href={DEVELOPER_URL} target="_blank" rel="noopener noreferrer" className={LINK}>
              {t("common.footer.developerName")}
            </a>
          </p>
        </div>
      </div>
    </footer>
  );
}
