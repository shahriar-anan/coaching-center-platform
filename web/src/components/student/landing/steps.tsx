import Image from "next/image";
import { Section } from "@/components/student/landing/section";
import { useLocale } from "@/i18n/locale-context";

/** Three steps, then the payment methods the gateway accepts. */
export function Steps() {
  const { t } = useLocale();
  const steps = [
    { title: t("landing.step1Title"), body: t("landing.step1Body") },
    { title: t("landing.step2Title"), body: t("landing.step2Body") },
    { title: t("landing.step3Title"), body: t("landing.step3Body") },
  ];
  return (
    <Section id="landing-steps" title={t("landing.stepsTitle")}>
      <ol className="grid gap-4 md:grid-cols-3">
        {steps.map((step, index) => (
          <li key={step.title} className="flex gap-4 rounded-card border border-border bg-card p-5">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-action font-bold text-on-action">
              {index + 1}
            </span>
            <span className="grid gap-1">
              <span className="text-lg font-bold">{step.title}</span>
              <span className="text-body text-muted">{step.body}</span>
            </span>
          </li>
        ))}
      </ol>
      <div className="mt-8 rounded-card border border-border bg-card p-4">
        <Image
          src="/brand/payment-methods.png"
          alt={t("landing.paymentsAlt")}
          width={1200}
          height={39}
          sizes="(min-width: 1024px) 960px, 100vw"
          className="h-auto w-full"
        />
      </div>
    </Section>
  );
}
