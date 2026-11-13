"use client";

import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import { useAuthorizedObjectUrl } from "@/lib/hooks/use-authorized-object-url";

/** The API path of an admission's photo: the student's profile photo, through the staff route. */
export function admissionPhotoPath(admissionId: string): string {
  return `/api/v1/offline-admissions/${admissionId}/photo`;
}

/** A small photo of the student, or their initial while there is none (or while it loads). */
export function AdmissionPhoto({
  admissionId,
  hasPhoto,
  name,
  size = "sm",
}: {
  admissionId: string;
  hasPhoto: boolean;
  name: string;
  /** "lg" for the detail page, where the photo is large enough to check against the NID. */
  size?: "sm" | "lg";
}) {
  const { t } = useLocale();
  const url = useAuthorizedObjectUrl(hasPhoto ? admissionPhotoPath(admissionId) : null);
  return (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center overflow-hidden rounded-control border border-border-control bg-bg font-semibold text-muted",
        size === "lg" ? "h-44 w-36 text-section" : "size-12 text-meta",
      )}
    >
      {url ? (
        <img src={url} alt={t("staffOffline.photoOf", { name })} className="size-full object-cover" />
      ) : (
        <span aria-hidden="true">{name.trim().charAt(0) || "?"}</span>
      )}
    </span>
  );
}
