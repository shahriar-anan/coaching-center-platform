import Link from "next/link";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";

export function Closing() {
  const { t } = useLocale();
  return (
    <section aria-labelledby="landing-closing" className="bg-band px-4 py-14 text-center text-on-band md:py-20">
      <div className="mx-auto grid max-w-2xl justify-items-center gap-5">
        <h2 id="landing-closing" className="text-2xl font-extrabold md:text-4xl">
          {t("landing.closingTitle")}
        </h2>
        <p className="text-body text-on-band/80">{t("landing.closingLead")}</p>
        <Button href="/register" size="lg">
          {t("landing.ctaSignUp")}
        </Button>
        <Link href="/login" className="text-body text-on-band/80 underline underline-offset-4 hover:text-on-band">
          {t("landing.closingLogin")}
        </Link>
      </div>
    </section>
  );
}
