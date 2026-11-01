import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { Clock, Mail, MapPin, Phone } from "lucide-react";
import { address, email, mapUrl, officeHours, phones, socials, whatsappNumber } from "@/content/site-info";
import { useLocale } from "@/i18n/locale-context";
import { Section } from "@/components/student/landing/section";
import { FacebookIcon, LinkedInIcon, WhatsAppIcon, YouTubeIcon } from "@/components/shell/social-icons";
import { cn } from "@/components/ui/cn";

const link = "inline-flex min-h-9 items-center font-semibold text-text-accent underline underline-offset-4";
/** The call and WhatsApp buttons: 44 px tall, full width in their column, with a visible focus ring. */
const CALL_BUTTON =
  "inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-control px-4 text-body font-bold transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";
const SOCIAL_LINK =
  "inline-flex size-11 items-center justify-center rounded-control border border-border-control text-text hover:bg-bg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";

/** The social links, with the same URLs as the site's facts in site-info.ts. The label is the footer's name for each. */
const SOCIAL_LINKS = [
  { key: "facebook", href: socials.facebook, Icon: FacebookIcon },
  { key: "youtube", href: socials.youtube, Icon: YouTubeIcon },
  { key: "linkedin", href: socials.linkedin, Icon: LinkedInIcon },
] as const;

/** One block of the panel: a small heading with its icon, then the details under it. */
function InfoRow({ icon: Icon, title, children }: { icon: LucideIcon; title: string; children: ReactNode }) {
  return (
    <div className="grid gap-1">
      <h3 className="flex items-center gap-2 text-body font-bold">
        <Icon aria-hidden="true" className="size-5 shrink-0 text-text-accent" />
        {title}
      </h3>
      <div className="grid gap-1 ps-7 text-body text-muted">{children}</div>
    </div>
  );
}

/**
 * The contact details in one compact panel of three columns (call, visit, connect) from large width; it stacks on phones and
 * the columns are separated by thin dividers.
 */
export function Contact() {
  const { t, locale } = useLocale();
  return (
    <Section id="landing-contact" title={t("landing.contactTitle")} center>
      <div className="mx-auto grid max-w-5xl overflow-hidden rounded-card border border-border bg-card shadow-sm md:grid-cols-2 lg:grid-cols-3">
        <div className="grid content-start gap-3 p-5">
          <h3 className="flex items-center gap-2 text-body font-bold">
            <Phone aria-hidden="true" className="size-5 shrink-0 text-text-accent" />
            {t("landing.contactCall")}
          </h3>
          <div className="grid gap-2">
            {phones.map((phone) => (
              <a
                key={phone.href}
                href={`tel:${phone.href}`}
                className={cn(CALL_BUTTON, "border border-border-control text-text hover:bg-bg")}
              >
                {phone.display}
              </a>
            ))}
            <a
              href={`https://wa.me/${whatsappNumber}`}
              target="_blank"
              rel="noopener noreferrer"
              className={cn(CALL_BUTTON, "bg-action text-on-action hover:opacity-90")}
            >
              <WhatsAppIcon className="size-5" />
              {t("landing.contactWhatsapp")}
            </a>
          </div>
        </div>

        <div className="grid content-start gap-4 border-t border-border p-5 md:border-s md:border-t-0">
          <InfoRow icon={MapPin} title={t("landing.contactAddress")}>
            <p>{locale === "bn" ? address.bn : address.en}</p>
            <a href={mapUrl} target="_blank" rel="noopener noreferrer" className={link}>
              {t("landing.contactMap")}
            </a>
          </InfoRow>
          <InfoRow icon={Clock} title={t("landing.contactHours")}>
            <p>{officeHours}</p>
          </InfoRow>
        </div>

        <div className="grid content-start gap-4 border-t border-border p-5 md:col-span-2 lg:col-span-1 lg:border-s lg:border-t-0">
          <InfoRow icon={Mail} title={t("landing.contactEmail")}>
            <a href={`mailto:${email}`} className={cn(link, "break-all")}>
              {email}
            </a>
          </InfoRow>
          <div className="grid gap-2">
            <h3 className="text-body font-bold">{t("landing.contactFollow")}</h3>
            <ul className="flex flex-wrap gap-2">
              {SOCIAL_LINKS.map(({ key, href, Icon }) => {
                const label = t(`common.footer.${key}`);
                return (
                  <li key={key}>
                    <a href={href} target="_blank" rel="noopener noreferrer" aria-label={label} title={label} className={SOCIAL_LINK}>
                      <Icon className="size-5" />
                    </a>
                  </li>
                );
              })}
            </ul>
          </div>
        </div>
      </div>
    </Section>
  );
}
