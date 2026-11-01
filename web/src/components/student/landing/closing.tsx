import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";

/**
 * Both buttons on the navy band. The primary is solid gold: the page's action colour is navy in the light theme, which would
 * vanish on this band. The secondary is an outline in the band's text colour.
 */
const PRIMARY =
  "inline-flex min-h-11 w-full items-center justify-center rounded-control bg-accent px-6 text-label font-bold text-on-accent transition hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:w-auto";
const SECONDARY =
  "inline-flex min-h-11 w-full items-center justify-center rounded-control border border-on-band/40 px-6 text-label font-bold text-on-band transition hover:bg-on-band/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:w-auto";

export function Closing() {
  const { t } = useLocale();
  return (
    <section aria-labelledby="landing-closing" className="relative overflow-hidden bg-band px-4 py-8 text-center text-on-band md:py-10 md:text-start">
      {/* Gold circles in the corners, as on the exam category covers. Decorative only. */}
      <span aria-hidden="true" className="pointer-events-none absolute -start-16 -top-20 size-40 rounded-full bg-accent/15 md:size-48" />
      <span aria-hidden="true" className="pointer-events-none absolute -bottom-24 -end-12 size-44 rounded-full border-2 border-accent/30 md:size-52" />
      {/* One row from md: the title, lead and sign-in link on the left, the two buttons on the right. Stacked and centred on phones. */}
      <div className="relative mx-auto grid max-w-5xl justify-items-center gap-4 md:grid-cols-[1fr_auto] md:items-center md:justify-items-start md:gap-x-10">
        <div className="grid gap-1.5">
          <h2 id="landing-closing" className="text-xl font-extrabold md:text-2xl">
            {t("landing.closingTitle")}
          </h2>
          <p className="text-body text-on-band/80">{t("landing.closingLead")}</p>
          <Link href="/login" className="justify-self-center text-meta text-on-band/80 underline underline-offset-4 hover:text-on-band md:justify-self-start">
            {t("landing.closingLogin")}
          </Link>
        </div>
        <div className="flex w-full max-w-sm flex-col gap-3 sm:w-auto sm:max-w-none sm:flex-row">
          <Link href="/register" className={PRIMARY}>
            {t("landing.ctaSignUp")}
          </Link>
          <Link href="/packages" className={SECONDARY}>
            {t("landing.closingPackages")}
          </Link>
        </div>
      </div>
    </section>
  );
}
