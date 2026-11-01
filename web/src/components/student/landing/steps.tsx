import Image from "next/image";
import type { LucideIcon } from "lucide-react";
import { CreditCard, Lock, Trophy, UserPlus } from "lucide-react";
import { Section } from "@/components/student/landing/section";
import { useLocale } from "@/i18n/locale-context";

/**
 * The gateway's payment logos, the same files the purchase sheet shows. Sizes are the files' own
 * proportions at the 32 px height the image is drawn at.
 */
const paymentLogos = [
  { name: "bKash", src: "/brand/payment/bkash.svg", width: 72, height: 32 },
  { name: "Nagad", src: "/brand/payment/nagad.svg", width: 73, height: 32 },
  { name: "Rocket", src: "/brand/payment/rocket.png", width: 48, height: 32 },
  { name: "Visa", src: "/brand/payment/visa.png", width: 48, height: 32 },
];

/** Three steps, then the payment logos the gateway accepts. */
export function Steps() {
  const { t } = useLocale();
  const steps: { icon: LucideIcon; title: string; body: string }[] = [
    { icon: UserPlus, title: t("landing.step1Title"), body: t("landing.step1Body") },
    { icon: CreditCard, title: t("landing.step2Title"), body: t("landing.step2Body") },
    { icon: Trophy, title: t("landing.step3Title"), body: t("landing.step3Body") },
  ];
  return (
    <Section id="landing-steps" title={t("landing.stepsTitle")}>
      <ol className="grid gap-6 md:grid-cols-3 md:gap-8">
        {steps.map((step, index) => {
          const Icon = step.icon;
          return (
            <li
              key={step.title}
              className="relative flex flex-col items-center gap-3 rounded-card border border-border bg-card p-6 text-center shadow-md"
            >
              {/* The joins between steps are decorative: a line to the next card on desktop, a line down to it on phones. */}
              {index < steps.length - 1 ? (
                <>
                  <span aria-hidden="true" className="absolute start-full top-8 hidden h-px w-8 bg-border-control md:block" />
                  <span
                    aria-hidden="true"
                    className="absolute start-1/2 top-full h-6 w-px -translate-x-1/2 bg-border-control md:hidden"
                  />
                </>
              ) : null}
              <span className="flex size-16 items-center justify-center rounded-full border-2 border-accent bg-accent/10 text-text-accent">
                <Icon aria-hidden="true" className="size-7" />
              </span>
              <span className="text-meta font-semibold text-muted">{t("landing.stepNumber", { n: index + 1 })}</span>
              <h3 className="text-lg font-bold">{step.title}</h3>
              <p className="text-body text-muted">{step.body}</p>
            </li>
          );
        })}
      </ol>
      <div className="mt-10 grid justify-items-center gap-4 text-center">
        <p className="flex items-center gap-2 text-body font-semibold">
          <Lock aria-hidden="true" className="size-4 shrink-0 text-text-accent" />
          {t("landing.paySecure")}
        </p>
        {/* White behind each logo, so the colours hold up on the dark theme too. */}
        <ul aria-label={t("landing.paymentsAlt")} className="flex flex-wrap justify-center gap-3">
          {paymentLogos.map((logo) => (
            <li key={logo.name} className="flex h-12 items-center justify-center rounded-control bg-white px-3">
              <Image src={logo.src} alt={logo.name} width={logo.width} height={logo.height} className="h-8 w-auto" />
            </li>
          ))}
        </ul>
      </div>
    </Section>
  );
}
