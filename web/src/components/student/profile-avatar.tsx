"use client";

import { Avatar } from "@/components/ui/file/avatar";
import { useAuthorizedObjectUrl } from "@/lib/hooks/use-authorized-object-url";

/**
 * The signed-in student's own picture, or their initials when there is none (or it does not load). The picture is fetched
 * with the bearer token, so it is shown as an object URL.
 */
export function ProfileAvatar({
  name,
  photoPath,
  size,
}: {
  name: string;
  photoPath: string | null | undefined;
  size: number;
}) {
  const src = useAuthorizedObjectUrl(photoPath ?? null);
  return <Avatar src={src} name={name} size={size} />;
}
