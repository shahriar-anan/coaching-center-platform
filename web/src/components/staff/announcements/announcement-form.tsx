"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import {
  useAnnouncementPreview,
  useSendAnnouncement,
  type AnnouncementAudience,
  type AnnouncementPreviewParams,
  type AnnouncementRequest,
} from "@/lib/api/exam/staff-admin";
import { adminErrorText } from "@/components/staff/access/admin-error";
import { AudienceCards } from "@/components/staff/announcements/audience-cards";
import { StudentPicker, type FoundStudent } from "@/components/staff/student-picker";
import { TargetPicker, type PickedTarget } from "@/components/staff/target-picker";

const TITLE_MAX = 500;
const BODY_MAX = 5000;
/** The most students one "Selected students" send can reach. The API refuses more. */
const SELECTED_MAX = 500;

type Draft = {
  titleEn: string;
  titleBn: string;
  bodyEn: string;
  bodyBn: string;
  audience: AnnouncementAudience;
};

type Errors = { text?: string; target?: string; students?: string };

const EMPTY_DRAFT: Draft = { titleEn: "", titleBn: "", bodyEn: "", bodyBn: "", audience: "ALL" };

/**
 * Master Admin and System Admin write one announcement in Bangla and English, for all students, the students of one
 * package, the students of one exam, or a list of chosen students. Each text field may be blank; at least one must be
 * filled. A sent announcement cannot be changed, so the send asks first, and the question says how many students it reaches.
 */
export function AnnouncementForm() {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const { toast } = useToast();
  const send = useSendAnnouncement();
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT);
  const [target, setTarget] = useState<PickedTarget | null>(null);
  const [students, setStudents] = useState<FoundStudent[]>([]);
  const [errors, setErrors] = useState<Errors>({});
  const [asking, setAsking] = useState(false);

  const audience = draft.audience;
  // The count is asked for only while the dialog is open. The chosen students are counted by their list, not a preview.
  const preview = useAnnouncementPreview(previewParams(audience, target), asking && audience !== "SELECTED");

  function update<K extends keyof Draft>(key: K, value: Draft[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  // Switching audience drops the other audience's choice: a package or exam, or the list of students.
  function chooseAudience(next: AnnouncementAudience) {
    if (next === audience) {
      return;
    }
    update("audience", next);
    setTarget(null);
    setStudents([]);
    setErrors({});
  }

  function review(event: FormEvent) {
    event.preventDefault();
    const next: Errors = {};
    if (![draft.titleEn, draft.titleBn, draft.bodyEn, draft.bodyBn].some((value) => value.trim())) {
      next.text = t("staffAdmin.announce.needOne");
    }
    if (audience === "MODULE" && !target) {
      next.target = t("staffAdmin.announce.moduleRequired");
    }
    if (audience === "EXAM" && !target) {
      next.target = t("staffAdmin.announce.examRequired");
    }
    if (audience === "SELECTED" && students.length === 0) {
      next.students = t("staffAdmin.announce.studentsRequired");
    }
    setErrors(next);
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
      audience,
      ...(audience === "MODULE" && target ? { moduleId: target.id } : {}),
      ...(audience === "EXAM" && target ? { examId: target.id } : {}),
      ...(audience === "SELECTED" ? { studentIds: students.map((student) => student.id) } : {}),
    };
    try {
      const result = await send.mutateAsync(body);
      // The exact count the server reports; the dialog's count was a preview, and students may have joined since.
      toast({
        title: t(result.recipients === 1 ? "staffAdmin.announce.sentOne" : "staffAdmin.announce.sent", {
          count: result.recipients,
        }),
        tone: "ok",
      });
      setDraft(EMPTY_DRAFT);
      setTarget(null);
      setStudents([]);
      setErrors({});
    } catch (e) {
      throw new Error(adminErrorText(e, t));
    }
  }

  /** The dialog's sentences, one after another: who it goes to, how many, the note for later joiners, and that it is final. */
  function confirmBody(): string {
    const lines: string[] = [];
    if (audience === "ALL") {
      lines.push(t("staffAdmin.announce.confirmToAll"));
    }
    if ((audience === "MODULE" || audience === "EXAM") && target) {
      lines.push(t("staffAdmin.announce.confirmToTarget", { title: target.title }));
    }
    if (audience === "SELECTED") {
      lines.push(
        t(students.length === 1 ? "staffAdmin.announce.confirmToSelectedOne" : "staffAdmin.announce.confirmToSelected", {
          count: formatNumber(students.length),
        }),
      );
    }
    if (audience !== "SELECTED") {
      // A count from an earlier opening is not shown while the preview is asked for again.
      if (preview.isPending || preview.isFetching) {
        lines.push(t("staffAdmin.announce.confirmCountLoading"));
      } else if (preview.isError) {
        lines.push(t("staffAdmin.announce.confirmCountUnknown"));
      } else {
        const count = preview.data.recipients;
        lines.push(t(count === 1 ? "staffAdmin.announce.confirmCountOne" : "staffAdmin.announce.confirmCount", { count: formatNumber(count) }));
      }
    }
    if (audience === "MODULE" || audience === "EXAM") {
      lines.push(t("staffAdmin.announce.confirmJoinLater"));
      if (preview.data?.recipients === 0) {
        lines.push(t("staffAdmin.announce.confirmNobody"));
      }
    }
    lines.push(t("staffAdmin.announce.confirmNoChange"));
    return lines.join(" ");
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

        <AudienceCards value={audience} onChange={chooseAudience} />

        {audience === "MODULE" || audience === "EXAM" ? (
          <TargetPicker
            key={audience}
            kind={audience === "MODULE" ? "package" : "exam"}
            hide={["DRAFT"]}
            label={t(audience === "MODULE" ? "staffAdmin.announce.pickPackage" : "staffAdmin.announce.pickExam")}
            value={target}
            onChange={(next) => {
              setTarget(next);
              setErrors((current) => ({ ...current, target: undefined }));
            }}
            invalid={Boolean(errors.target)}
            errorText={errors.target}
          />
        ) : null}

        {audience === "SELECTED" ? (
          <div className="grid gap-3">
            <StudentPicker
              value={students}
              max={SELECTED_MAX}
              onChange={(next) => {
                setStudents(next);
                setErrors((current) => ({ ...current, students: undefined }));
              }}
            />
            {errors.students ? (
              <p role="alert" className="text-meta text-danger">
                {errors.students}
              </p>
            ) : null}
          </div>
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
        body={confirmBody()}
        confirmLabel={t("staffAdmin.announce.send")}
        onConfirm={sendNow}
      />
    </>
  );
}

/** The preview request for the audience. `SELECTED` has no preview, so it asks for the nearest one and is never sent. */
function previewParams(audience: AnnouncementAudience, target: PickedTarget | null): AnnouncementPreviewParams {
  if (audience === "MODULE") {
    return { audience, moduleId: target?.id };
  }
  if (audience === "EXAM") {
    return { audience, examId: target?.id };
  }
  return { audience: "ALL" };
}
