import Image from "next/image";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { phones } from "@/content/site-info";
import { useLocale } from "@/i18n/locale-context";

/** The navy chevron from the client's banner: a flat left point, cut square on the right. */
const CHEVRON = "[clip-path:polygon(16%_0,100%_0,100%_100%,16%_100%,0_50%)]";

export function Hero() {
  const { t } = useLocale();
  return (
    <section aria-labelledby="landing-hero" className="px-4 pb-12 pt-10 md:pb-16 md:pt-14">
      <div className="mx-auto grid max-w-6xl items-center gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:gap-14">
        <div className="text-center lg:text-start">
          <p className="text-label font-bold text-text-accent">{t("landing.heroLabel")}</p>
          <h1 id="landing-hero" className="mt-3 text-3xl font-extrabold leading-tight md:text-5xl">
            {t("landing.heroTitle")}
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-body text-muted md:text-lg lg:mx-0">
            {t("landing.heroLead")}
          </p>

          <ul className="mt-6 flex flex-wrap justify-center gap-2 lg:justify-start">
            <li className="rounded-full bg-accent px-4 py-1.5 text-label font-bold text-on-accent">
              {t("landing.chipOnline")}
            </li>
            <li className="rounded-full border border-border bg-card px-4 py-1.5 text-label font-semibold">
              {t("landing.chipOffline")}
            </li>
          </ul>

          {/* The mockup's entrance: the two doors are the way into the product. */}
          <div className="mx-auto mt-7 grid max-w-md grid-cols-2 gap-3 lg:mx-0">
            <Link
              href="/mcq"
              className="block rounded-card border border-border bg-card p-5 text-center transition hover:-translate-y-0.5 hover:border-accent"
            >
              <span aria-hidden="true" className="block text-3xl">✅</span>
              <span className="mt-1 block text-title font-bold">{t("home.doorMcqTitle")}</span>
              <span className="block text-meta text-muted">{t("home.doorMcqSub")}</span>
            </Link>
            <Link
              href="/written"
              className="block rounded-card border border-border bg-card p-5 text-center transition hover:-translate-y-0.5 hover:border-accent"
            >
              <span aria-hidden="true" className="block text-3xl">✍️</span>
              <span className="mt-1 block text-title font-bold">{t("home.doorWrittenTitle")}</span>
              <span className="block text-meta text-muted">{t("home.doorWrittenSub")}</span>
            </Link>
          </div>

          <div className="mx-auto mt-4 grid max-w-md lg:mx-0">
            <Button href="/register" size="lg" fullWidth>
              {t("landing.ctaSignUp")}
            </Button>
          </div>
        </div>

        <div className="relative mx-auto w-full max-w-sm lg:max-w-none">
          <div aria-hidden="true" className={cn("absolute inset-0 translate-x-3 translate-y-3 bg-band", CHEVRON)} />
          <Image
            src="/brand/classroom.webp"
            alt={t("landing.photoAlt")}
            width={419}
            height={582}
            priority
            sizes="(min-width: 1024px) 40vw, 90vw"
            className={cn("relative h-auto w-full object-cover", CHEVRON)}
          />
          <div className="absolute bottom-4 end-4 grid gap-1 rounded-card border border-border bg-card p-3 text-body shadow-lg">
            <p className="font-bold">{t("landing.callNow")}</p>
            {phones.map((phone) => (
              <a key={phone.href} href={`tel:${phone.href}`} className="font-semibold text-text-accent underline">
                {phone.display}
              </a>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
