"use client";

import { useEffect, useRef, useState } from "react";
import { ArrowDown, ArrowUp, FileText, Trash2 } from "lucide-react";
import {
  NOTICE_PDF_MAX,
  NOTICE_PHOTO_TYPES,
  NOTICE_PHOTO_UPLOAD_MAX,
} from "@/components/notice-board/notice-text";
import { NoticePhotoThumb } from "@/components/staff/notice-board/notice-photo-thumb";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { IconButton } from "@/components/ui/icon-button";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import {
  NOTICE_PHOTO_MAX,
  useAddNoticePhoto,
  useRemoveNoticePdf,
  useRemoveNoticePhoto,
  useReorderNoticePhotos,
  usePutNoticePdf,
  type NoticePhoto,
  type StaffNotice,
} from "@/lib/api/exam/site-notices";
import { useFormat } from "@/lib/format/use-format";

const KB = 1024;
const MB = 1024 * 1024;

export type NoticeAttachmentsProps = {
  /** The saved notice. Null for a draft that has no id yet, so nothing can be attached. */
  notice: StaffNotice | null;
  /** Whether this user may change the files. A System Admin sees a published notice's files read-only. */
  editable: boolean;
  /** True while the texts are being saved or the notice is being published or unpublished. */
  busy: boolean;
  /** The notice as the server returned it after a file change. */
  onUpdated: (notice: StaffNotice) => void;
  /** The notice's title in the reader's language. It is used in the photo alt text. */
  title: string;
};

/**
 * The photos and the PDF of a notice, shown under the texts in the editor. Each change is saved at once, like the cover of a
 * package; it does not wait for the texts. Photos are re-ordered with buttons, never by drag.
 */
export function NoticeAttachments({ notice, editable, busy, onUpdated, title }: NoticeAttachmentsProps) {
  const { t } = useLocale();
  return (
    <section aria-labelledby="notice-attachments-heading" className="grid gap-4 border-t border-border pt-4">
      <div className="grid gap-1">
        <h3 id="notice-attachments-heading" className="text-label font-semibold text-text">
          {t("notices.board.staff.attachments.heading")}
        </h3>
        <p className="text-meta text-muted">{t("notices.board.staff.attachments.hint")}</p>
      </div>
      {notice ? (
        <>
          {editable ? null : <p className="text-body text-muted">{t("notices.board.staff.attachments.readOnly")}</p>}
          <PhotoList notice={notice} title={title} editable={editable} busy={busy} onUpdated={onUpdated} />
          <PdfPart notice={notice} editable={editable} busy={busy} onUpdated={onUpdated} />
        </>
      ) : (
        <p className="text-body text-muted">{t("notices.board.staff.attachments.saveFirst")}</p>
      )}
    </section>
  );
}

type PartProps = {
  notice: StaffNotice;
  editable: boolean;
  busy: boolean;
  onUpdated: (notice: StaffNotice) => void;
};

function PhotoList({ notice, title, editable, busy, onUpdated }: PartProps & { title: string }) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const { toast } = useToast();
  const input = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLOListElement>(null);
  // The control to focus once a move has been saved, so the keyboard user stays on the photo they moved.
  const focusAfterMove = useRef<string | null>(null);
  const add = useAddNoticePhoto();
  const remove = useRemoveNoticePhoto();
  const reorder = useReorderNoticePhotos();
  const [error, setError] = useState<string | null>(null);
  const [removing, setRemoving] = useState<NoticePhoto | null>(null);

  const photos = notice.photos ?? [];
  const full = photos.length >= NOTICE_PHOTO_MAX;
  const locked = busy || add.isPending || remove.isPending || reorder.isPending || !editable;
  const counter = t("notices.board.staff.attachments.count", {
    count: formatNumber(photos.length),
    max: formatNumber(NOTICE_PHOTO_MAX),
  });

  useEffect(() => {
    const key = focusAfterMove.current;
    if (!key) {
      return;
    }
    focusAfterMove.current = null;
    const [id, direction] = key.split(":");
    const other = direction === "up" ? "down" : "up";
    const control =
      listRef.current?.querySelector<HTMLButtonElement>(`[data-move="${id}:${direction}"]:not(:disabled)`) ??
      listRef.current?.querySelector<HTMLButtonElement>(`[data-move="${id}:${other}"]:not(:disabled)`);
    control?.focus();
  }, [notice.photos]);

  async function onFile(file: File | undefined) {
    if (!file) {
      return;
    }
    setError(null);
    if (!NOTICE_PHOTO_TYPES.includes(file.type)) {
      setError(t("notices.board.staff.attachments.errors.photoType"));
      return;
    }
    if (file.size > NOTICE_PHOTO_UPLOAD_MAX) {
      setError(t("notices.board.staff.attachments.errors.tooLarge"));
      return;
    }
    try {
      onUpdated(await add.mutateAsync({ id: notice.id, file }));
      toast({ title: t("notices.board.staff.attachments.added"), tone: "ok" });
    } catch (err) {
      setError(attachmentError(err, t, t("notices.board.staff.attachments.errors.photoType")));
    } finally {
      if (input.current) {
        input.current.value = "";
      }
    }
  }

  async function move(index: number, direction: "up" | "down") {
    const target = direction === "up" ? index - 1 : index + 1;
    if (target < 0 || target >= photos.length) {
      return;
    }
    const ids = photos.map((photo) => photo.id);
    ids[index] = photos[target].id;
    ids[target] = photos[index].id;
    setError(null);
    focusAfterMove.current = `${photos[index].id}:${direction}`;
    try {
      onUpdated(await reorder.mutateAsync({ id: notice.id, ids }));
    } catch (err) {
      focusAfterMove.current = null;
      setError(attachmentError(err, t, t("notices.board.staff.attachments.errors.reorder")));
    }
  }

  async function removeChosen() {
    if (!removing) {
      return;
    }
    try {
      onUpdated(await remove.mutateAsync({ id: notice.id, fileId: removing.id }));
      toast({ title: t("notices.board.staff.attachments.removed"), tone: "ok" });
    } catch (err) {
      throw new Error(attachmentError(err, t, t("notices.board.staff.errors.generic")));
    }
  }

  return (
    <div className="grid gap-3">
      <p className="text-label font-medium text-text">{t("notices.board.staff.attachments.photos")}</p>
      {photos.length === 0 ? (
        <p className="text-body text-muted">{t("notices.board.staff.attachments.none")}</p>
      ) : (
        <ol ref={listRef} className="grid gap-2">
          {photos.map((photo, index) => {
            const position = formatNumber(index + 1);
            return (
              <li key={photo.id} className="flex items-center gap-3 rounded-control border border-border bg-card p-2">
                <span className="flex size-14 shrink-0 overflow-hidden rounded-control bg-bg">
                  <NoticePhotoThumb
                    path={photo.url}
                    alt={t("notices.board.photos.alt", { title, n: index + 1, m: photos.length })}
                  />
                </span>
                <span className="min-w-0 flex-1 text-label text-text">
                  {t("notices.board.staff.attachments.position", { n: position })}
                </span>
                {editable ? (
                  <div className="flex shrink-0 items-center">
                    <IconButton
                      label={t("notices.board.staff.attachments.moveUp", { n: position })}
                      icon={<ArrowUp aria-hidden="true" className="size-5" />}
                      data-move={`${photo.id}:up`}
                      disabled={locked || index === 0}
                      onClick={() => void move(index, "up")}
                    />
                    <IconButton
                      label={t("notices.board.staff.attachments.moveDown", { n: position })}
                      icon={<ArrowDown aria-hidden="true" className="size-5" />}
                      data-move={`${photo.id}:down`}
                      disabled={locked || index === photos.length - 1}
                      onClick={() => void move(index, "down")}
                    />
                    <IconButton
                      label={t("notices.board.staff.attachments.remove", { n: position })}
                      icon={<Trash2 aria-hidden="true" className="size-5" />}
                      disabled={locked}
                      onClick={() => setRemoving(photo)}
                    />
                  </div>
                ) : null}
              </li>
            );
          })}
        </ol>
      )}

      {editable ? (
        full ? (
          <p className="text-label font-medium text-text">{counter}</p>
        ) : (
          <div className="flex flex-wrap items-center gap-3">
            <input
              ref={input}
              type="file"
              accept={NOTICE_PHOTO_TYPES.join(",")}
              className="sr-only"
              aria-label={t("notices.board.staff.attachments.add")}
              onChange={(event) => void onFile(event.target.files?.[0])}
              disabled={locked}
            />
            <Button variant="secondary" loading={add.isPending} disabled={locked} onClick={() => input.current?.click()}>
              {t("notices.board.staff.attachments.add")}
            </Button>
            <span className="text-meta text-muted">{counter}</span>
          </div>
        )
      ) : (
        <p className="text-meta text-muted">{counter}</p>
      )}

      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => {
          if (!open) setRemoving(null);
        }}
        title={t("notices.board.staff.attachments.removeTitle")}
        body={t("notices.board.staff.attachments.removeBody")}
        confirmLabel={t("notices.board.staff.attachments.removeConfirm")}
        tone="danger"
        onConfirm={removeChosen}
      />
    </div>
  );
}

function PdfPart({ notice, editable, busy, onUpdated }: PartProps) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const { toast } = useToast();
  const input = useRef<HTMLInputElement>(null);
  const put = usePutNoticePdf();
  const remove = useRemoveNoticePdf();
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  const pdf = notice.pdf ?? null;
  const locked = busy || put.isPending || remove.isPending || !editable;

  async function onFile(file: File | undefined) {
    if (!file) {
      return;
    }
    setError(null);
    if (file.type !== "application/pdf" && !/\.pdf$/i.test(file.name)) {
      setError(t("common.file.notPdf"));
      return;
    }
    if (file.size > NOTICE_PDF_MAX) {
      setError(t("notices.board.staff.attachments.errors.tooLarge"));
      return;
    }
    try {
      onUpdated(await put.mutateAsync({ id: notice.id, file }));
      toast({
        title: t(pdf ? "notices.board.staff.attachments.pdfReplaced" : "notices.board.staff.attachments.pdfAttached"),
        tone: "ok",
      });
    } catch (err) {
      setError(attachmentError(err, t, t("common.file.notPdf")));
    } finally {
      if (input.current) {
        input.current.value = "";
      }
    }
  }

  async function removeChosen() {
    try {
      onUpdated(await remove.mutateAsync(notice.id));
      toast({ title: t("notices.board.staff.attachments.pdfRemoved"), tone: "ok" });
    } catch (err) {
      throw new Error(attachmentError(err, t, t("notices.board.staff.errors.generic")));
    }
  }

  return (
    <div className="grid gap-3">
      <p className="text-label font-medium text-text">{t("notices.board.staff.attachments.pdfLabel")}</p>
      {editable ? (
        <input
          ref={input}
          type="file"
          accept="application/pdf"
          className="sr-only"
          aria-label={pdf ? t("notices.board.staff.attachments.replacePdf") : t("notices.board.staff.attachments.attachPdf")}
          onChange={(event) => void onFile(event.target.files?.[0])}
          disabled={locked}
        />
      ) : null}

      {pdf ? (
        <div className="flex flex-wrap items-center gap-3 rounded-control border border-border bg-card p-3">
          <span
            aria-hidden="true"
            className="flex size-11 shrink-0 items-center justify-center rounded-control bg-accent/15 text-text-accent"
          >
            <FileText className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="break-words text-body font-medium text-text">{pdf.fileName}</p>
            <p className="text-meta text-muted">{fileSizeLabel(pdf.byteSize, t, formatNumber)}</p>
          </div>
          {editable ? (
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" loading={put.isPending} disabled={locked} onClick={() => input.current?.click()}>
                {t("notices.board.staff.attachments.replacePdf")}
              </Button>
              <Button variant="ghost" disabled={locked} onClick={() => setConfirming(true)}>
                {t("notices.board.staff.attachments.removePdf")}
              </Button>
            </div>
          ) : null}
        </div>
      ) : editable ? (
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="secondary" loading={put.isPending} disabled={locked} onClick={() => input.current?.click()}>
            {t("notices.board.staff.attachments.attachPdf")}
          </Button>
          <span className="text-meta text-muted">{t("notices.board.staff.attachments.noPdf")}</span>
        </div>
      ) : (
        <p className="text-meta text-muted">{t("notices.board.staff.attachments.noPdf")}</p>
      )}

      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}

      <ConfirmDialog
        open={confirming}
        onOpenChange={(open) => {
          if (!open) setConfirming(false);
        }}
        title={t("notices.board.staff.attachments.removePdfTitle")}
        body={t("notices.board.staff.attachments.removePdfBody")}
        confirmLabel={t("notices.board.staff.attachments.removePdfConfirm")}
        tone="danger"
        onConfirm={removeChosen}
      />
    </div>
  );
}

/** The file size as the common file strings show it: whole KB below 1 MB, one decimal place of MB from 1 MB. */
function fileSizeLabel(
  bytes: number,
  t: (key: string, params?: Record<string, string | number>) => string,
  formatNumber: (value: number, options?: { decimals?: number }) => string,
): string {
  if (bytes < MB) {
    return t("common.file.sizeKb", { size: formatNumber(Math.max(1, Math.round(bytes / KB))) });
  }
  return t("common.file.sizeMb", { size: formatNumber(bytes / MB, { decimals: 1 }) });
}

/** The sentence for a failed photo or PDF change. The API codes get their own text; `validation` is for a wrong type. */
function attachmentError(error: unknown, t: (key: string) => string, validation: string): string {
  if (error instanceof ApiError) {
    if (error.code === "FILE_TOO_LARGE") {
      return t("notices.board.staff.attachments.errors.tooLarge");
    }
    if (error.code === "ACCESS_DENIED") {
      return t("notices.board.staff.errors.denied");
    }
    if (error.code === "VALIDATION_FAILED") {
      return error.fieldErrors?.some((item) => item.field === "photos")
        ? t("notices.board.staff.attachments.errors.photoLimit")
        : validation;
    }
  }
  return t("notices.board.staff.errors.generic");
}
