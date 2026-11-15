"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import {
  useBatchClasses,
  useBatchRates,
  useInstructors,
  useRemoveRate,
  useSaveInstructorPhone,
  useSetRate,
  type InstructorRow,
} from "@/lib/api/exam/offline-batch-pay";
import { useFormat } from "@/lib/format/use-format";
import { offlineErrorText } from "./offline-error";
import { amountOf } from "./batch-pay-format";

/** One instructor of the batch: from its classes and its rate. */
type InstructorLine = { id: string; name: string; classes: number; rate: number | null };

/**
 * The default rate of each instructor in this batch (plan F7, 8.1). Instructors with classes but no rate come first, with "Set a rate".
 * A rate saves on Enter or when the field is left; "Saved" stays until the rate changes again.
 */
export function BatchRates({ batchId, archived }: { batchId: string; archived: boolean }) {
  const { t } = useLocale();
  const rates = useBatchRates(batchId);
  const classes = useBatchClasses(batchId);
  const { toast } = useToast();
  const setRate = useSetRate(batchId);
  const removeRate = useRemoveRate(batchId);
  const instructors = useInstructors();
  const savePhone = useSaveInstructorPhone();
  const [saved, setSaved] = useState<Record<string, true>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});

  if (rates.isPending || classes.isPending) {
    return <Skeleton variant="rows" count={4} />;
  }
  if (rates.isError || classes.isError) {
    return <ErrorState onRetry={() => void Promise.all([rates.refetch(), classes.refetch()])} error={rates.error ?? classes.error} />;
  }

  const lines = instructorLines(rates.data, classes.data);
  const byId = new Map((instructors.data ?? []).map((instructor) => [instructor.id, instructor]));

  async function savePhoneFor(instructor: InstructorRow, value: string) {
    const phone = value.trim() === "" ? null : value.trim();
    setErrors((current) => {
      const next = { ...current };
      delete next[instructor.id];
      return next;
    });
    try {
      await savePhone.mutateAsync({ id: instructor.id, version: instructor.version, phone });
      setSaved((current) => ({ ...current, [`phone:${instructor.id}`]: true }));
    } catch (failure) {
      setErrors((current) => ({ ...current, [instructor.id]: offlineErrorText(failure, t) }));
    }
  }

  async function save(line: InstructorLine, value: string) {
    const amount = Number(value);
    if (value.trim() === "" || !Number.isFinite(amount) || amount < 0) {
      setErrors((current) => ({ ...current, [line.id]: t("batchPay.rates.invalid") }));
      return;
    }
    setErrors((current) => {
      const next = { ...current };
      delete next[line.id];
      return next;
    });
    setSaved((current) => {
      const next = { ...current };
      delete next[line.id];
      return next;
    });
    try {
      await setRate.mutateAsync({ instructorId: line.id, rate: value.trim() });
      setSaved((current) => ({ ...current, [line.id]: true }));
    } catch (failure) {
      setErrors((current) => ({ ...current, [line.id]: offlineErrorText(failure, t) }));
    }
  }

  async function remove(line: InstructorLine) {
    try {
      await removeRate.mutateAsync(line.id);
      toast({ title: t("batchPay.rates.removed"), tone: "ok" });
    } catch (failure) {
      setErrors((current) => ({ ...current, [line.id]: offlineErrorText(failure, t) }));
    }
  }

  const columns: Column<InstructorLine>[] = [
    {
      key: "name",
      header: t("batchPay.rates.instructor"),
      cell: (line) => <span className="font-medium text-text">{line.name}</span>,
    },
    {
      key: "phone",
      header: t("batchPay.rates.phone"),
      cell: (line) => {
        const instructor = byId.get(line.id);
        return instructor ? (
          <PhoneInput
            key={`${line.id}:${instructor.phone ?? ""}:${instructor.version}`}
            instructor={instructor}
            disabled={archived}
            saved={Boolean(saved[`phone:${line.id}`])}
            onSave={(value) => void savePhoneFor(instructor, value)}
          />
        ) : null;
      },
    },
    {
      key: "classes",
      header: t("batchPay.rates.classes"),
      nowrap: true,
      cell: (line) => <ClassCount count={line.classes} />,
    },
    {
      key: "rate",
      header: t("batchPay.rates.rate"),
      cell: (line) => (
        <RateInput
          key={`${line.id}:${line.rate ?? ""}`}
          line={line}
          disabled={archived}
          onSave={(value) => void save(line, value)}
        />
      ),
    },
    {
      key: "status",
      header: t("batchPay.rates.status"),
      cell: (line) => (
        <span className="grid gap-1">
          {saved[line.id] ? <span className="text-meta text-ok-text">{t("batchPay.rates.saved")}</span> : null}
          {line.rate === null ? <Pill tone="warn">{t("batchPay.rates.setRate")}</Pill> : null}
          {errors[line.id] ? <span role="alert" className="text-meta text-danger">{errors[line.id]}</span> : null}
        </span>
      ),
    },
    {
      key: "remove",
      header: "",
      cell: (line) =>
        line.rate !== null && !archived ? (
          <Button size="sm" variant="ghost" onClick={() => void remove(line)} loading={removeRate.isPending}>
            {t("batchPay.rates.remove")}
          </Button>
        ) : null,
    },
  ];

  return (
    <div className="grid gap-3">
      <p className="text-meta text-muted">{t("batchPay.rates.note")}</p>
      {archived ? <p className="text-meta text-muted">{t("batchPay.rates.readOnly")}</p> : null}
      <Table
        columns={columns}
        rows={lines}
        rowKey={(line) => line.id}
        empty={<EmptyState title={t("batchPay.rates.empty")} body={t("batchPay.rates.emptyBody")} />}
      />
    </div>
  );
}

/**
 * Instructors from the classes of the batch and from its rates, one line each. Those with classes and no rate come first, then by name.
 */
export function instructorLines(
  rates: { instructorId: string; name: string; rate: number | string }[],
  classes: { instructorId: string | null; instructorName: string | null; status: string }[],
): InstructorLine[] {
  const lines = new Map<string, InstructorLine>();
  const ensure = (id: string, name: string) => {
    const existing = lines.get(id);
    if (existing) {
      if (!existing.name && name) {
        existing.name = name;
      }
      return existing;
    }
    const created: InstructorLine = { id, name, classes: 0, rate: null };
    lines.set(id, created);
    return created;
  };
  for (const item of classes) {
    if (item.instructorId && item.status !== "CANCELLED") {
      ensure(item.instructorId, item.instructorName ?? "").classes += 1;
    }
  }
  for (const rate of rates) {
    ensure(rate.instructorId, rate.name).rate = amountOf(rate.rate);
  }
  return [...lines.values()].sort((a, b) => {
    const aMissing = a.rate === null && a.classes > 0 ? 0 : 1;
    const bMissing = b.rate === null && b.classes > 0 ? 0 : 1;
    if (aMissing !== bMissing) {
      return aMissing - bMissing;
    }
    return a.name.localeCompare(b.name);
  });
}

/**
 * The instructor's phone number, edited in place. It saves on Enter or when the field is left, only when the value changed. The phone
 * belongs to the instructor, so it shows and saves the same on every batch.
 */
function PhoneInput({
  instructor,
  disabled,
  saved,
  onSave,
}: {
  instructor: InstructorRow;
  disabled: boolean;
  saved: boolean;
  onSave: (value: string) => void;
}) {
  const { t } = useLocale();
  const initial = instructor.phone ?? "";
  const [draft, setDraft] = useState(initial);

  function commit() {
    if (draft.trim() !== initial.trim()) {
      onSave(draft);
    }
  }

  return (
    <span className="grid gap-1">
      <TextInput
        aria-label={`${t("batchPay.rates.phone")}: ${instructor.name}`}
        inputMode="tel"
        placeholder={t("batchPay.rates.phonePlaceholder")}
        className="h-9 max-w-44 py-1 text-meta"
        disabled={disabled}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === "Enter") {
            event.preventDefault();
            commit();
          }
        }}
      />
      {saved ? <span className="text-meta text-ok-text">{t("batchPay.rates.phoneSaved")}</span> : null}
    </span>
  );
}

function ClassCount({ count }: { count: number }) {
  const { formatNumber } = useFormat();
  return <span className="text-body text-text">{formatNumber(count)}</span>;
}

/** The rate field. It saves on Enter or when it loses focus, and only when the value changed. */
function RateInput({ line, disabled, onSave }: { line: InstructorLine; disabled: boolean; onSave: (value: string) => void }) {
  const { t } = useLocale();
  const initial = line.rate === null ? "" : String(line.rate);
  const [draft, setDraft] = useState(initial);

  function commit() {
    if (draft.trim() !== initial) {
      onSave(draft);
    }
  }

  return (
    <TextInput
      aria-label={`${t("batchPay.rates.rate")}: ${line.name}`}
      inputMode="decimal"
      placeholder={t("batchPay.rates.setRate")}
      className="h-9 max-w-40 py-1 text-meta"
      disabled={disabled}
      value={draft}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={commit}
      onKeyDown={(event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          commit();
        }
      }}
    />
  );
}
