"use client";

import { useEffect, useRef, useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useRemovePhoto, useUploadPhoto } from "@/lib/api/exam/account";
import { useAuthorizedObjectUrl } from "@/lib/hooks/use-authorized-object-url";
import { Avatar } from "@/components/ui/file/avatar";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { FormError } from "@/app/(auth)/_components/auth-ui";

/** The accepted types and the upload limit. The browser checks them first; the server checks them again. */
export const PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];
export const PHOTO_MAX_BYTES = 8 * 1024 * 1024;

/**
 * The student's profile picture row on the account page (80 px). Choose a file, check the preview, then save. Remove asks
 * first. Only the student sees it; the picture is fetched with the bearer token.
 */
export function ProfilePhoto({ name, photoPath }: { name: string; photoPath: string | null | undefined }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const input = useRef<HTMLInputElement>(null);
  const upload = useUploadPhoto();
  const remove = useRemovePhoto();
  const saved = useAuthorizedObjectUrl(photoPath ?? null);
  const [chosen, setChosen] = useState<{ file: File; url: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removeOpen, setRemoveOpen] = useState(false);
  const busy = upload.isPending || remove.isPending;
  const hasPhoto = Boolean(photoPath);

  // The preview is an object URL of the chosen file. It is released when the choice changes, is saved or cancelled, or leaves the page.
  useEffect(() => {
    if (!chosen) return;
    return () => URL.revokeObjectURL(chosen.url);
  }, [chosen]);

  function onChoose(file: File | undefined) {
    if (!file) return;
    setError(null);
    if (!PHOTO_TYPES.includes(file.type)) {
      setError(t("account.photo.wrongType"));
      return;
    }
    if (file.size > PHOTO_MAX_BYTES) {
      setError(t("account.photo.tooLarge"));
      return;
    }
    setChosen({ file, url: URL.createObjectURL(file) });
  }

  function cancel() {
    setError(null);
    setChosen(null);
  }

  async function save() {
    if (!chosen) return;
    setError(null);
    try {
      await upload.mutateAsync(chosen.file);
      setChosen(null);
      toast({ title: t("account.photo.saved"), tone: "ok" });
    } catch (err) {
      setError(mapError(err));
    }
  }

  async function removePhoto() {
    setError(null);
    try {
      await remove.mutateAsync();
      toast({ title: t("account.photo.removed"), tone: "ok" });
    } catch {
      throw new Error(t("account.photo.failed"));
    }
  }

  function mapError(err: unknown): string {
    const code = err instanceof ApiError ? err.code : null;
    if (code === "FILE_TOO_LARGE") return t("account.photo.tooLarge");
    if (code === "VALIDATION_FAILED") return t("account.photo.wrongType");
    return t("account.photo.failed");
  }

  return (
    <div className="grid gap-4 py-3 sm:grid-cols-[auto_minmax(0,1fr)] sm:items-center">
      <Avatar
        src={chosen?.url ?? saved}
        name={name}
        size={80}
        alt={chosen ? t("account.photo.previewAlt") : undefined}
      />
      <div className="grid min-w-0 gap-3">
        <span className="text-meta text-muted">{t("account.photo.label")}</span>
        {chosen ? (
          <div className="grid gap-2 sm:max-w-sm sm:grid-cols-2">
            <Button onClick={() => void save()} loading={upload.isPending} disabled={remove.isPending}>
              {t("account.photo.save")}
            </Button>
            <Button variant="secondary" onClick={cancel} disabled={upload.isPending}>
              {t("account.photo.cancel")}
            </Button>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            <input
              ref={input}
              type="file"
              accept={PHOTO_TYPES.join(",")}
              className="hidden"
              disabled={busy}
              onChange={(event) => {
                onChoose(event.target.files?.[0]);
                // Cleared so the same file can be chosen again after a cancel.
                event.target.value = "";
              }}
            />
            <Button variant="secondary" disabled={busy} onClick={() => input.current?.click()}>
              {hasPhoto ? t("account.photo.change") : t("account.photo.upload")}
            </Button>
            {hasPhoto ? (
              <Button variant="ghost" disabled={busy} onClick={() => setRemoveOpen(true)}>
                {t("account.photo.remove")}
              </Button>
            ) : null}
          </div>
        )}
        <p className="text-meta text-muted">{t("account.photo.help")}</p>
        {error ? <FormError>{error}</FormError> : null}
      </div>

      <ConfirmDialog
        open={removeOpen}
        onOpenChange={setRemoveOpen}
        title={t("account.photo.removeTitle")}
        body={t("account.photo.removeBody")}
        confirmLabel={t("account.photo.removeConfirm")}
        tone="danger"
        onConfirm={removePhoto}
      />
    </div>
  );
}
