"use client";

import Image from "next/image";
import Link from "next/link";
import { Clock, Mail, MapPin, Phone } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import {
  AppleIcon,
  FacebookIcon,
  GooglePlayIcon,
  LinkedInIcon,
  WhatsAppIcon,
  YouTubeIcon,
} from "@/components/shell/social-icons";
import { cn } from "@/components/ui/cn";

/** Which footer a page shows. `none` renders nothing; the route decides it (see `footerVariant`). */
export type FooterVariant = "full" | "none";

/**
 * Store links from the old site's footer. They are placeholders and wrong: the Apple link is Apple's own app, and the
 * Google Play link is an Expo build. Replace both with the real store pages before APP_AVAILABLE is set to true.
 */
const APP_STORE_URL = "https://apps.apple.com/us/app/apple-store/id375380948";
const GOOGLE_PLAY_URL = "https://expo.dev/accounts/abtahi-tajwar/projects/symamlaw-mobile/builds/a0368e73-baf4-4021-8bc6-e59fc962afaa";
const SOCIAL_LINKS = [
  { key: "facebook", href: "https://www.facebook.com/profile.php?id=100065554386812" },
  { key: "whatsapp", href: "https://api.whatsapp.com/send?phone=8801601561225" },
  { key: "linkedin", href: "https://www.linkedin.com/company/symam-law/?viewAsMember=true" },
  { key: "youtube", href: "https://youtu.be/ZmrfDj6T5R4?si=aKH4z7RErGn-v7Lr" },
] as const;
/** The brand glyph for each social link. The link's accessible name comes from the same footer key as its label. */
const SOCIAL_ICONS = {
  facebook: FacebookIcon,
  whatsapp: WhatsAppIcon,
  linkedin: LinkedInIcon,
  youtube: YouTubeIcon,
} as const;

/** The full footer copies the old site's footer: its navy, its darker bottom bar, and its sizes. */
const OLD_NAVY = "#05325B";
const OLD_NAVY_DARK = "#0a2540";
const FOCUS = "rounded-control focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#eab308]";
/** One footer block: 320 px wide and padded on phones, its own width from 768 px. */
const BLOCK = "grid w-80 max-w-full content-start gap-4 px-8 md:w-auto md:px-0";
const HEADING = "text-2xl font-medium text-[#f3f4f6] lg:text-[1.75rem]";
const ICON = "size-5 shrink-0 text-[#eab308]";
/** The old site's outline pill buttons for the app stores. */
const APP_BUTTON = "inline-flex h-10 items-center gap-2 rounded-full border border-current px-4 text-sm";
/** The store links stay off until the app is published. Set to true on the day the store pages are live. */
const APP_AVAILABLE = false;
const DEVELOPER_URL = "https://www.linkedin.com/in/shahriar-anan-a358562a3/";

/**
 * The site footer. `full` is the old site's footer as it was: the logo, the contact details, the company links and the app
 * block on the navy band, the payment methods strip, then the social icons, copyright and credit on a darker bar. Every page
 * has it except the exam sitting page, as on the old site; the shell decides that from the route.
 */
export function SiteFooter() {
  const { t } = useLocale();
  const legalLinks = [
    { href: "/terms", label: t("common.footer.terms") },
    { href: "/refund", label: t("common.footer.refund") },
    { href: "/privacy", label: t("common.footer.privacy") },
  ];
  const companyLinks = [...legalLinks, { href: "/about", label: t("common.footer.about") }];

  return (
    <footer className="text-[#e5e7eb]">
      <div style={{ backgroundColor: OLD_NAVY }}>
        {/* Phones: one centred column, each block 320 px wide. 768–1023 px: three columns without the logo. From 1024 px: the
            logo and the three blocks in four columns, as on the old site. */}
        <nav
          aria-label={t("common.footer.label")}
          className="mx-auto grid max-w-[1920px] justify-items-center gap-y-12 px-5 py-10 md:grid-cols-3 md:items-start md:gap-x-6 lg:grid-cols-4"
        >
          <div className="flex w-full justify-center md:hidden lg:flex lg:w-auto">
            <Link href="/" aria-label={t("common.brand.home")} className={FOCUS}>
              <Image src="/brand/logo-dark.png" alt="" width={300} height={300} className="size-32 lg:size-40" />
            </Link>
          </div>

          <section aria-labelledby="footer-contact" className={BLOCK}>
            <h2 id="footer-contact" className={HEADING}>
              {t("common.footer.contactHeading")}
            </h2>
            <ul className="grid gap-3 text-[0.85rem] lg:text-sm">
              <li className="flex items-center gap-2.5">
                <Phone aria-hidden="true" className={ICON} fill="currentColor" strokeWidth={0} />
                <span>{t("common.footer.phone")}</span>
              </li>
              <li className="flex items-center gap-2.5">
                <Mail aria-hidden="true" className={ICON} strokeWidth={1.5} />
                <a href={`mailto:${t("common.footer.email")}`} className={cn("break-all hover:text-white", FOCUS)}>
                  {t("common.footer.email")}
                </a>
              </li>
              <li className="flex items-center gap-2.5">
                <Clock aria-hidden="true" className={ICON} />
                <span>{t("common.footer.hours")}</span>
              </li>
              <li className="flex items-center gap-2.5">
                <MapPin aria-hidden="true" className={ICON} />
                <span>{t("common.footer.location")}</span>
              </li>
            </ul>
          </section>

          <section aria-labelledby="footer-company" className={BLOCK}>
            <h2 id="footer-company" className={HEADING}>
              {t("common.footer.companyHeading")}
            </h2>
            <ul className="grid gap-2 text-[0.95rem]">
              {companyLinks.map((link) => (
                <li key={link.href}>
                  <Link href={link.href} prefetch={false} className={cn("hover:text-white", FOCUS)}>
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          <section aria-labelledby="footer-app" className={BLOCK}>
            <h2 id="footer-app" className={HEADING}>
              {t("common.footer.appHeading")}
            </h2>
            <p className="text-[0.95rem] leading-relaxed lg:text-sm">{t("common.footer.appText")}</p>
            <div className="flex flex-wrap gap-4">
              {APP_AVAILABLE ? (
                <>
                  <a href={APP_STORE_URL} target="_blank" rel="noopener noreferrer" className={cn(APP_BUTTON, "hover:bg-white hover:text-[#05325B]", FOCUS)}>
                    <AppleIcon className="size-4 shrink-0" />
                    {t("common.footer.appStore")}
                  </a>
                  <a href={GOOGLE_PLAY_URL} target="_blank" rel="noopener noreferrer" className={cn(APP_BUTTON, "hover:bg-white hover:text-[#05325B]", FOCUS)}>
                    <GooglePlayIcon className="size-4 shrink-0" />
                    {t("common.footer.googlePlay")}
                  </a>
                </>
              ) : (
                // The old site's buttons, not yet links: the store pages are not live. The title says so on hover.
                <>
                  <span aria-disabled="true" title={t("common.footer.comingSoon")} className={APP_BUTTON}>
                    <AppleIcon className="size-4 shrink-0" />
                    {t("common.footer.appStore")}
                  </span>
                  <span aria-disabled="true" title={t("common.footer.comingSoon")} className={APP_BUTTON}>
                    <GooglePlayIcon className="size-4 shrink-0" />
                    {t("common.footer.googlePlay")}
                  </span>
                </>
              )}
            </div>
          </section>
        </nav>
      </div>

      {/* The old site's payment strip, edge to edge on a white band. */}
      <div className="border-y border-white/20 bg-white">
        <Image
          src="/brand/payment-methods.png"
          alt={t("common.payments.alt")}
          width={9561}
          height={314}
          sizes="100vw"
          className="h-auto w-full"
        />
      </div>

      <div style={{ backgroundColor: OLD_NAVY_DARK }}>
        <div className="mx-auto grid max-w-[1920px] justify-items-center gap-2 px-10 py-4 md:grid-cols-3 md:items-center">
          <nav aria-label={t("common.footer.socialLabel")}>
            <ul className="flex items-center gap-3">
              {SOCIAL_LINKS.map((link) => {
                const Icon = SOCIAL_ICONS[link.key];
                const label = t(`common.footer.${link.key}`);
                return (
                  <li key={link.key}>
                    <a
                      href={link.href}
                      target="_blank"
                      rel="noopener noreferrer"
                      aria-label={label}
                      title={label}
                      className={cn("flex size-6 items-center justify-center hover:text-white", FOCUS)}
                    >
                      <Icon className="size-4 lg:size-[1.1rem]" />
                    </a>
                  </li>
                );
              })}
            </ul>
          </nav>
          <p className="text-center text-xs sm:text-sm">{t("common.footer.copyright")}</p>
          <p className="text-[0.7rem] sm:text-xs">
            {t("common.footer.developedBy")}{" "}
            <a href={DEVELOPER_URL} target="_blank" rel="noopener noreferrer" className={cn("hover:text-white hover:underline", FOCUS)}>
              {t("common.footer.developerName")}
            </a>
          </p>
        </div>
      </div>
    </footer>
  );
}
