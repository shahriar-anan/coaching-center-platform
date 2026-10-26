"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useModules } from "@/lib/api/exam/staff-catalog";
import {
  useSendAnnouncement,
  type AnnouncementAudience,
  type AnnouncementRequest,
} from "@/lib/api/exam/staff-admin";
import { adminErrorText } from "@/components/staff/access/admin-error";

const TITLE_MAX = 500;
const BODY_MAX = 5000;

type Draft = {
  titleEn: string;
  titleBn: string;
  bodyEn: string;
  bodyBn: string;
  audience: AnnouncementAudience;
  moduleId: string;
};

const EMPTY_DRAFT: Draft = { titleEn: "", titleBn: "", bodyEn: "", bodyBn: "", audience: "ALL", moduleId: "" };

/**
 * Master Admin writes one announcement in Bangla and English, to all students or
 * to the students of one module. Each field may be blank; at least one must be
 * filled. A sent announcement cannot be changed, so the send asks first.
 */
export function AnnouncementForm() {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const { toast } = useToast();
  const send = useSendAnnouncement();
  const modules = useModules();
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT);
  const [errors, setErrors] = useState<{ text?: string; module?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [asking, setAsking] = useState(false);

  const moduleOptions = (modules.data?.content ?? []).filter((row) => row.status !== "ARCHIVED");
  const chosenModule = moduleOptions.find((row) => row.id === draft.moduleId);

  function update<K extends keyof Draft>(key: K, value: Draft[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  function review(event: FormEvent) {
    event.preventDefault();
    const next: { text?: string; module?: string } = {};
    if (![draft.titleEn, draft.titleBn, draft.bodyEn, draft.bodyBn].some((value) => value.trim())) {
      next.text = t("staffAdmin.announce.needOne");
    }
    if (draft.audience === "MODULE" && !draft.moduleId) {
      next.module = t("staffAdmin.announce.moduleRequired");
    }
    setErrors(next);
    setFormError(null);
    if (Object.keys(next).length === 0) {
      setAsking(true);
    }
  }

  async function sendNow() {
    const body: AnnouncementRequest = {
      titleEn: draft.titleEn.trim(),
      titleBn: draft.titleBn.trim(),
      bodyEn: draft.bodyEn.trim(),
      bodyBn: draft.bodyBn.trim(),
      audience: draft.audience,
      ...(draft.audience === "MODULE" ? { moduleId: draft.moduleId } : {}),
    };
    try {
      const result = await send.mutateAsync(body);
      toast({ title: t("staffAdmin.announce.sent", { count: result.recipients }), tone: "ok" });
      setDraft(EMPTY_DRAFT);
      setErrors({});
    } catch (e) {
      throw new Error(adminErrorText(e, t));
    }
  }

  return (
    <>
      <form className="grid gap-5 max-w-3xl" onSubmit={review} noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("staffAdmin.announce.titleEn")}>
            <TextInput
              value={draft.titleEn}
              maxLength={TITLE_MAX}
              invalid={Boolean(errors.text)}
              onChange={(event) => update("titleEn", event.target.value)}
            />
          </Field>
          <Field label={t("staffAdmin.announce.titleBn")}>
            <TextInput
              value={draft.titleBn}
              maxLength={TITLE_MAX}
              invalid={Boolean(errors.text)}
              onChange={(event) => update("titleBn", event.target.value)}
            />
          </Field>
          <Field label={t("staffAdmin.announce.bodyEn")}>
            <Textarea
              value={draft.bodyEn}
              maxLength={BODY_MAX}
              rows={5}
              invalid={Boolean(errors.text)}
              onChange={(event) => update("bodyEn", event.target.value)}
            />
          </Field>
          <Field label={t("staffAdmin.announce.bodyBn")}>
            <Textarea
              value={draft.bodyBn}
              maxLength={BODY_MAX}
              rows={5}
              invalid={Boolean(errors.text)}
              onChange={(event) => update("bodyBn", event.target.value)}
            />
          </Field>
        </div>
        <p className="text-meta text-muted">{t("staffAdmin.announce.fieldHint")}</p>
        {errors.text ? (
          <p role="alert" className="text-meta text-danger">
            {errors.text}
          </p>
        ) : null}

        <Field label={t("staffAdmin.announce.audience")}>
          <Select
            value={draft.audience}
            onChange={(event) => {
              update("audience", event.target.value as AnnouncementAudience);
              setErrors({});
            }}
          >
            <option value="ALL">{t("staffAdmin.announce.audienceAll")}</option>
            <option value="MODULE">{t("staffAdmin.announce.audienceModule")}</option>
          </Select>
        </Field>

        {draft.audience === "MODULE" ? (
          <Field label={t("staffAdmin.announce.module")} error={errors.module} required>
            <Select
              value={draft.moduleId}
              invalid={Boolean(errors.module)}
              onChange={(event) => update("moduleId", event.target.value)}
            >
              <option value="">{t("staffAdmin.grant.pickModule")}</option>
              {moduleOptions.map((row) => (
                <option key={row.id} value={row.id}>
                  {pickTitle(row)}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}

        {formError ? (
          <p role="alert" className="text-meta text-danger">
            {formError}
          </p>
        ) : null}

        <div className="grid gap-2">
          <Button type="submit" className="justify-self-start">
            {t("staffAdmin.announce.send")}
          </Button>
          <p className="text-meta text-muted">{t("staffAdmin.announce.sendNote")}</p>
        </div>
      </form>

      <ConfirmDialog
        open={asking}
        onOpenChange={setAsking}
        title={t("staffAdmin.announce.confirmTitle")}
        body={
          draft.audience === "MODULE"
            ? t("staffAdmin.announce.confirmModule", { module: chosenModule ? pickTitle(chosenModule) : "" })
            : t("staffAdmin.announce.confirmAll")
        }
        confirmLabel={t("staffAdmin.announce.send")}
        onConfirm={sendNow}
      />
    </>
  );
}
