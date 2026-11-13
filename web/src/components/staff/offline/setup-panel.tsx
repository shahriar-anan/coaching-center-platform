"use client";

import { useEffect, useRef, useState } from "react";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { ApiError } from "@/lib/api/errors";
import {
  useBatches,
  useSaveSetup,
  type AdmissionDetail,
  type AdmissionSetup,
  type SetupBody,
} from "@/lib/api/exam/offline-admin";
import { defaultFirstDue, duePreview, toNumber } from "./offline-model";
import { ItemsPanel } from "./items-panel";
import { offlineErrorText } from "./offline-error";

type Draft = { batchId: string; monthlyFee: string; admittedOn: string; firstDueOn: string; courseEndsOn: string };

function draftOf(setup: AdmissionSetup): Draft {
  return {
    batchId: setup.batchId ?? "",
    monthlyFee: setup.monthlyFee === null ? "" : String(setup.monthlyFee),
    admittedOn: setup.admittedOn ?? "",
    firstDueOn: setup.firstDueOn ?? "",
    courseEndsOn: setup.courseEndsOn ?? "",
  };
}

function bodyOf(draft: Draft): SetupBody {
  return {
    batchId: draft.batchId || null,
    monthlyFee: draft.monthlyFee.trim() || null,
    admittedOn: draft.admittedOn || null,
    firstDueOn: draft.firstDueOn || null,
    courseEndsOn: draft.courseEndsOn || null,
  };
}

/** Field errors the API named, keyed by field. */
function fieldErrorsOf(error: unknown): Record<string, string> {
  if (error instanceof ApiError && error.fieldErrors) {
    return Object.fromEntries(error.fieldErrors.map((problem) => [problem.field, problem.message]));
  }
  return {};
}

/**
 * The setup (plan 6.3): batch, monthly fee, admitted on, first due, course end, the dues it will create, and the exams and packages.
 * A batch fills the fee and the end date; the first due date is a month after the admitted date and moves with it while it is still
 * that default. Changes save as they are made. The setup is locked while the admission is ready (plan C16).
 */
export function SetupPanel({ admission, editable, canEditItems }: { admission: AdmissionDetail; editable: boolean; canEditItems: boolean }) {
  const { t } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const { toast } = useToast();
  const batches = useBatches("ACTIVE");
  const save = useSaveSetup(admission.id);
  const saveSetup = save.mutate;
  const [draft, setDraft] = useState<Draft>(() => draftOf(admission.setup));
  const [dirty, setDirty] = useState(false);
  const [status, setStatus] = useState<"idle" | "saving" | "saved" | "error">("idle");
  const [errors, setErrors] = useState<Record<string, string>>({});

  // The server's copy comes in again whenever it changes, unless the person is typing. Compared during render, so no extra render pass.
  const [seenSetup, setSeenSetup] = useState(admission.setup);
  if (admission.setup !== seenSetup) {
    setSeenSetup(admission.setup);
    if (!dirty) {
      setDraft(draftOf(admission.setup));
    }
  }

  // The newest draft, so a save that finishes after another edit does not clear the edit that is still unsaved.
  const latestDraft = useRef(draft);
  useEffect(() => {
    latestDraft.current = draft;
  }, [draft]);

  // Save shortly after the last change. The save sends the whole setup. A failed save is not retried by itself: the next change tries again.
  useEffect(() => {
    if (!dirty || !editable) {
      return;
    }
    const timer = window.setTimeout(() => {
      const sent = bodyOf(draft);
      setStatus("saving");
      saveSetup(sent, {
        onSuccess: () => {
          setErrors({});
          setStatus("saved");
          if (JSON.stringify(bodyOf(latestDraft.current)) === JSON.stringify(sent)) {
            setDirty(false);
          }
        },
        onError: (error) => {
          setStatus("error");
          setErrors(fieldErrorsOf(error));
          toast({ title: offlineErrorText(error, t), tone: "danger" });
        },
      });
    }, 600);
    return () => window.clearTimeout(timer);
  }, [draft, dirty, editable, saveSetup, t, toast]);

  function edit(next: Partial<Draft>) {
    setDraft((current) => ({ ...current, ...next }));
    setDirty(true);
    setErrors({});
  }

  function chooseBatch(id: string) {
    const batch = batches.data?.find((candidate) => candidate.id === id);
    edit({
      batchId: id,
      monthlyFee: batch ? String(batch.defaultMonthlyFee) : draft.monthlyFee,
      courseEndsOn: batch ? batch.endsOn : draft.courseEndsOn,
    });
  }

  function changeAdmittedOn(value: string) {
    // The first due date moves with the admitted date only while it still holds the default (plan D12).
    const wasDefault = draft.firstDueOn === "" || (draft.admittedOn !== "" && draft.firstDueOn === defaultFirstDue(draft.admittedOn));
    edit({ admittedOn: value, firstDueOn: value && wasDefault ? defaultFirstDue(value) : draft.firstDueOn });
  }

  const batchOptions = batches.data ?? [];
  const currentBatchMissing = draft.batchId !== "" && !batchOptions.some((batch) => batch.id === draft.batchId);
  const preview = duePreview(draft.firstDueOn, draft.courseEndsOn, toNumber(draft.monthlyFee));
  const total = preview.reduce((sum, row) => sum + row.amount, 0);
  const shownRows = preview.slice(0, 12);

  const disabled = !editable;
  const statusText =
    status === "saving"
      ? t("staffOffline.setup.saving")
      : status === "saved"
        ? t("staffOffline.setup.saved")
        : status === "error"
          ? t("staffOffline.setup.notSaved")
          : "";

  return (
    <section aria-labelledby="setup-title" className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id="setup-title" className="text-card-title text-text">
          {t("staffOffline.setup.title")}
        </h3>
        <span role="status" aria-live="polite" className="text-meta text-muted">
          {statusText}
        </span>
      </div>

      {!editable ? (
        <p className="rounded-control bg-bg px-3 py-2 text-meta text-muted">
          {admission.state === "READY" ? t("staffOffline.setup.lockedReady") : t("staffOffline.setup.lockedForm")}
        </p>
      ) : null}

      <Field label={t("staffOffline.setup.batch")} hint={t("staffOffline.setup.batchHint")} error={errors.batchId} required>
        <Select value={draft.batchId} disabled={disabled} onChange={(event) => chooseBatch(event.target.value)} invalid={Boolean(errors.batchId)}>
          <option value="">{t("staffOffline.setup.batchNone")}</option>
          {currentBatchMissing ? (
            <option value={draft.batchId}>{admission.setup.batchNameEn ?? admission.setup.batchNameBn ?? ""}</option>
          ) : null}
          {batchOptions.map((batch) => (
            <option key={batch.id} value={batch.id}>
              {batch.nameEn} / {batch.nameBn}
            </option>
          ))}
        </Select>
      </Field>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field label={t("staffOffline.setup.monthlyFee")} error={errors.monthlyFee} required>
          <TextInput
            inputMode="decimal"
            value={draft.monthlyFee}
            disabled={disabled}
            onChange={(event) => edit({ monthlyFee: event.target.value })}
            invalid={Boolean(errors.monthlyFee)}
          />
        </Field>
        <Field label={t("staffOffline.setup.admittedOn")} error={errors.admittedOn} required>
          <TextInput
            type="date"
            value={draft.admittedOn}
            disabled={disabled}
            onChange={(event) => changeAdmittedOn(event.target.value)}
            invalid={Boolean(errors.admittedOn)}
          />
        </Field>
        <Field label={t("staffOffline.setup.firstDue")} hint={t("staffOffline.setup.firstDueHint")} error={errors.firstDueOn} required>
          <TextInput
            type="date"
            value={draft.firstDueOn}
            disabled={disabled}
            onChange={(event) => edit({ firstDueOn: event.target.value })}
            invalid={Boolean(errors.firstDueOn)}
          />
        </Field>
        <Field label={t("staffOffline.setup.courseEnds")} error={errors.courseEndsOn} required>
          <TextInput
            type="date"
            value={draft.courseEndsOn}
            disabled={disabled}
            onChange={(event) => edit({ courseEndsOn: event.target.value })}
            invalid={Boolean(errors.courseEndsOn)}
          />
        </Field>
      </div>

      <div className="grid gap-2 rounded-card border border-border-control bg-card p-3">
        <h4 className="text-meta font-semibold text-text">{t("staffOffline.setup.dues")}</h4>
        {preview.length === 0 ? (
          <p className="text-meta text-muted">{t("staffOffline.setup.duesEmpty")}</p>
        ) : (
          <>
            <ul className="grid gap-1 text-body">
              {shownRows.map((row) => (
                <li key={row.dueOn} className="flex justify-between gap-3">
                  <span>{formatDate(row.dueOn)}</span>
                  <span className="font-latin">{formatMoney(row.amount)}</span>
                </li>
              ))}
            </ul>
            <p className="text-meta text-muted">
              {t("staffOffline.setup.duesTotal", { count: preview.length, total: formatMoney(total) ?? "" })}
              {preview.length > shownRows.length ? ` · ${t("staffOffline.setup.duesMore", { more: preview.length - shownRows.length })}` : ""}
            </p>
          </>
        )}
      </div>

      {/* The exams and packages lock with the setup while the admission is ready (plan C16). */}
      <ItemsPanel admission={admission} canEdit={canEditItems && admission.state !== "READY"} />
    </section>
  );
}
