import type { ReactNode } from "react";
import { Phone, MessageCircle, Mail, Clock, MapPin } from "lucide-react";
import { address, email, mapUrl, officeHours, phones, socials, whatsappNumber } from "@/content/site-info";
import { useLocale } from "@/i18n/locale-context";
import { Section } from "@/components/student/landing/section";

const link = "font-semibold text-text-accent underline underline-offset-4";

function InfoCard({ icon: Icon, title, children }: { icon: typeof Phone; title: string; children: ReactNode }) {
  return (
    <div className="grid gap-3 rounded-card border border-border bg-card p-5">
      <div className="flex items-center gap-2">
        <Icon aria-hidden="true" className="size-5 text-accent" />
        <h3 className="font-bold">{title}</h3>
      </div>
      <div className="grid gap-1.5 text-body">{children}</div>
    </div>
  );
}

export function Contact() {
  const { t, locale } = useLocale();
  return (
    <Section id="landing-contact" title={t("landing.contactTitle")}>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <InfoCard icon={Phone} title={t("landing.contactCall")}>
          {phones.map((phone) => (
            <a key={phone.href} href={`tel:${phone.href}`} className={link}>
              {phone.display}
            </a>
          ))}
        </InfoCard>

        <InfoCard icon={MessageCircle} title={t("landing.contactWhatsapp")}>
          <a href={`https://wa.me/${whatsappNumber}`} target="_blank" rel="noopener noreferrer" className={link}>
            {phones[0].display}
          </a>
        </InfoCard>

        <InfoCard icon={Mail} title={t("landing.contactEmail")}>
          <a href={`mailto:${email}`} className={link}>
            {email}
          </a>
        </InfoCard>

        <InfoCard icon={Clock} title={t("landing.contactHours")}>
          <p>{officeHours}</p>
        </InfoCard>

        <InfoCard icon={MapPin} title={t("landing.contactAddress")}>
          <p>{locale === "bn" ? address.bn : address.en}</p>
          <a href={mapUrl} target="_blank" rel="noopener noreferrer" className={link}>
            {t("landing.contactMap")}
          </a>
        </InfoCard>

        <InfoCard icon={MessageCircle} title={t("landing.contactFollow")}>
          <a href={socials.facebook} target="_blank" rel="noopener noreferrer" className={link}>
            Facebook
          </a>
          <a href={socials.youtube} target="_blank" rel="noopener noreferrer" className={link}>
            YouTube
          </a>
          <a href={socials.linkedin} target="_blank" rel="noopener noreferrer" className={link}>
            LinkedIn
          </a>
        </InfoCard>
      </div>
    </Section>
  );
}
