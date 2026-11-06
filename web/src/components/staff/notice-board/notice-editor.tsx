"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import {
  missingNoticeFields,
  noticeFieldMax,
  noticeLength,
  noticeTextsForApi,
  overlongNoticeFields,
  NOTICE_FIELDS,
  type NoticeField,
} from "@/components/notice-board/notice-text";
import { NoticeAttachments } from "@/components/staff/notice-board/notice-attachments";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import {
  useDeleteNotice,
  usePublishNotice,
  useSaveNotice,
  useUnpublishNotice,
  type SiteNoticeStatus,
  type StaffNotice,
} from "@/lib/api/exam/site-notices";
import { useFormat } from "@/lib/format/use-format";

type Draft = Record<NoticeField, string>;

type Confirm = "publish" | "unpublish" | "delete" | null;

const TONES: Record<SiteNoticeStatus, PillTone> = {
  DRAFT: "muted",
  PUBLISHED: "ok",
  UNPUBLISHED: "warn",
};

function draftOf(notice: StaffNotice | null): Draft {
  return {
    titleEn: notice?.titleEn ?? "",
    titleBn: notice?.titleBn ?? "",
    bodyEn: notice?.bodyEn ?? "",
    bodyBn: notice?.bodyBn ?? "",
  };
}

/** The sentence for a failed save or publish. Known API codes get their own text; anything else is the generic one. */
function staffErrorText(error: unknown, t: (key: string) => string): string {
  if (error instanceof ApiError) {
    if (error.code === "ACCESS_DENIED") {
      return t("notices.board.staff.errors.denied");
    }
    if (error.code === "SITE_NOTICE_STATE") {
      return t("notices.board.staff.errors.state");
    }
  }
  return t("notices.board.staff.errors.generic");
}

/**
 * One notice or a new draft, in English and Bangla. A draft may be incomplete. A notice that has been published needs all
 * four texts, and only the Master Admin may change it, publish it, unpublish it or delete it. Publishing saves a changed
 * draft first, so the public sees the text on the screen.
 */
export function NoticeEditor({
  notice,
  isMasterAdmin,
  onClose,
}: {
  notice: StaffNotice | null;
  isMasterAdmin: boolean;
  onClose: () => void;
}) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const { toast } = useToast();
  const save = useSaveNotice();
  const publish = usePublishNotice();
  const unpublish = useUnpublishNotice();
  const remove = useDeleteNotice();

  const [current, setCurrent] = useState<StaffNotice | null>(notice);
  const [draft, setDraft] = useState<Draft>(() => draftOf(notice));
  const [formError, setFormError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<Confirm>(null);

  const status: SiteNoticeStatus = current?.status ?? "DRAFT";
  const published = status !== "DRAFT";
  const canEdit = !published || isMasterAdmin;
  const dirty = NOTICE_FIELDS.some((field) => draft[field] !== (current?.[field] ?? ""));
  const missing = missingNoticeFields(draft);
  const overlong = overlongNoticeFields(draft);
  const busy = save.isPending || publish.isPending || unpublish.isPending || remove.isPending;

  function update(field: NoticeField, value: string) {
    setDraft((previous) => ({ ...previous, [field]: value }));
    setFormError(null);
  }

  /** The reason a save or publish cannot go ahead, or null. Publishing needs all four texts; a draft need not have them. */
  function problem(needAll: boolean): string | null {
    if (overlong.length > 0) {
      return t("notices.board.staff.tooLong", {
        max: formatNumber(noticeFieldMax(overlong[0])),
      });
    }
    if (needAll && missing.length > 0) {
      return t("notices.board.staff.missing", { fields: missing.map((field) => t(`notices.board.staff.fields.${field}`)).join(", ") });
    }
    return null;
  }

  /** Saves the draft as it is on the screen: creates it, or saves the four texts. */
  async function persist(): Promise<StaffNotice> {
    const saved = await save.mutateAsync({ id: current?.id, texts: noticeTextsForApi(draft) });
    setCurrent(saved);
    setDraft(draftOf(saved));
    return saved;
  }

  async function saveForm(event: FormEvent) {
    event.preventDefault();
    const message = problem(published);
    if (message) {
      setFormError(message);
      return;
    }
    const wasNew = !current;
    try {
      await persist();
      toast({ title: t(wasNew ? "notices.board.staff.created" : "notices.board.staff.saved"), tone: "ok" });
    } catch (error) {
      setFormError(staffErrorText(error, t));
    }
  }

  async function publishNow() {
    const message = problem(true);
    if (message) {
      throw new Error(message);
    }
    try {
      const saved = dirty || !current ? await persist() : current;
      const result = await publish.mutateAsync(saved.id);
      setCurrent(result);
      toast({ title: t("notices.board.staff.published"), tone: "ok" });
    } catch (error) {
      throw new Error(staffErrorText(error, t));
    }
  }

  async function unpublishNow() {
    if (!current) {
      return;
    }
    try {
      const result = await unpublish.mutateAsync(current.id);
      setCurrent(result);
      toast({ title: t("notices.board.staff.unpublished"), tone: "ok" });
    } catch (error) {
      throw new Error(staffErrorText(error, t));
    }
  }

  async function deleteNow() {
    if (!current) {
      return;
    }
    try {
      await remove.mutateAsync(current.id);
      toast({ title: t("notices.board.staff.deleted"), tone: "ok" });
      onClose();
    } catch (error) {
      throw new Error(staffErrorText(error, t));
    }
  }

  const canDelete = Boolean(current) && (!published || isMasterAdmin);
  const canPublish = isMasterAdmin && status !== "PUBLISHED";
  const canUnpublish = isMasterAdmin && status === "PUBLISHED";
  const waitingForMaster = !isMasterAdmin && status === "DRAFT" && missing.length === 0;

  return (
    <div className="grid gap-4 rounded-card border border-border bg-card p-4 sm:p-5">
      <div className="flex flex-wrap items-center gap-2">
        <Pill tone={TONES[status]}>{t(`notices.board.staff.status.${status}`)}</Pill>
      </div>

      {!canEdit ? <p className="text-body text-muted">{t("notices.board.staff.readOnly")}</p> : null}
      {published && canEdit ? <p className="text-body text-muted">{t("notices.board.staff.publishedNote")}</p> : null}
      {waitingForMaster ? <p className="text-body text-muted">{t("notices.board.staff.waiting")}</p> : null}

      <form onSubmit={saveForm} className="grid gap-4" noValidate>
        <p className="text-meta text-muted">{t("notices.board.staff.plainText")}</p>
        {NOTICE_FIELDS.map((field) => {
          const count = noticeLength(draft[field]);
          const max = noticeFieldMax(field);
          const counter = t("notices.board.staff.counter", {
            count: formatNumber(count),
            max: formatNumber(max),
          });
          const label = t(`notices.board.staff.fields.${field}`);
          const tooLong = count > max;
          const shared = {
            value: draft[field],
            disabled: busy || !canEdit,
            onChange: (event: { target: { value: string } }) => update(field, event.target.value),
          };
          return (
            <Field
              key={field}
              label={label}
              hint={counter}
              error={tooLong ? t("notices.board.staff.tooLong", { max: formatNumber(max) }) : undefined}
            >
              {field.startsWith("title") ? (
                <TextInput {...shared} invalid={tooLong} />
              ) : (
                <Textarea {...shared} rows={6} invalid={tooLong} />
              )}
            </Field>
          );
        })}

        {formError ? (
          <p role="alert" className="text-meta text-danger">
            {formError}
          </p>
        ) : null}

        <div className="flex flex-wrap gap-2">
          {canEdit ? (
            <Button type="submit" variant="primary" loading={save.isPending} disabled={busy && !save.isPending}>
              {t(published ? "notices.board.staff.saveChanges" : "notices.board.staff.save")}
            </Button>
          ) : null}
          {canPublish ? (
            <Button type="button" variant="secondary" disabled={busy} onClick={() => setConfirm("publish")}>
              {t("notices.board.staff.publish")}
            </Button>
          ) : null}
          {canUnpublish ? (
            <Button type="button" variant="secondary" disabled={busy} onClick={() => setConfirm("unpublish")}>
              {t("notices.board.staff.unpublish")}
            </Button>
          ) : null}
          {canDelete ? (
            <Button type="button" variant="danger" disabled={busy} onClick={() => setConfirm("delete")}>
              {t("notices.board.staff.delete")}
            </Button>
          ) : null}
        </div>
      </form>

      <NoticeAttachments
        notice={current}
        editable={canEdit}
        busy={busy}
        onUpdated={setCurrent}
        title={current?.titleEn || current?.titleBn || t("notices.board.staff.untitled")}
      />

      <ConfirmDialog
        open={confirm === "publish"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title={t("notices.board.staff.publishTitle")}
        body={t("notices.board.staff.publishBody")}
        confirmLabel={t("notices.board.staff.publish")}
        onConfirm={publishNow}
      />
      <ConfirmDialog
        open={confirm === "unpublish"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title={t("notices.board.staff.unpublishTitle")}
        body={t("notices.board.staff.unpublishBody")}
        confirmLabel={t("notices.board.staff.unpublish")}
        onConfirm={unpublishNow}
      />
      <ConfirmDialog
        open={confirm === "delete"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title={t("notices.board.staff.deleteTitle")}
        body={t("notices.board.staff.deleteBody")}
        confirmLabel={t("notices.board.staff.delete")}
        tone="danger"
        onConfirm={deleteNow}
      />
    </div>
  );
}
