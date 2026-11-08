"use client";

import { Field } from "@/components/ui/field";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import {
  PAYMENT_METHODS,
  PAYMENT_TYPES,
  errorFor,
  type PaymentDraft,
  type PaymentField,
  type PaymentMethod,
  type PaymentPlan,
  type PaymentType,
  type Resolved,
} from "@/components/staff/enrol/payment-model";

/**
 * The payment part of a direct enrolment: the type, the total, what is paid today, the method, the reference, the date and
 * the note, and for installments the count, the amount per installment and the next date with a read-only schedule. It shows
 * what the rules in `payment-model.ts` worked out, and it writes only what the admin types. Errors show once the admin has
 * tried to save (`showErrors`).
 */
export function PaymentFields({
  draft,
  resolved,
  free,
  standardPaisa,
  today,
  showErrors,
  onChange,
}: {
  draft: PaymentDraft;
  resolved: Resolved;
  /** A free exam takes only No charge. */
  free: boolean;
  /** The standard price in paisa, or null when the target has none. */
  standardPaisa: number | null;
  today: string;
  showErrors: boolean;
  onChange: (patch: Partial<PaymentDraft>) => void;
}) {
  const { t } = useLocale();
  const { formatMoney } = useFormat();
  const check = resolved.check;
  const plan = check.ok ? check.payment : null;

  /** The message for a field's error, once the admin has tried to save. A missing reason has its own sentence. */
  function message(field: PaymentField): string | undefined {
    if (!showErrors) {
      return undefined;
    }
    const code = errorFor(check, field);
    if (!code) {
      return undefined;
    }
    if (field === "reason" && code === "required") {
      return t("staffAdmin.enrol.reasonRequired");
    }
    return t(`staffAdmin.enrol.payment.error.${code}`);
  }

  const money = (paisa: number) => formatMoney(paisa / 100) ?? "";
  const hasStandard = standardPaisa !== null;
  const difference = resolved.differencePaisa;
  const twoInstallments = draft.count.trim() === "2";
  const type = resolved.type;

  return (
    <div className="grid gap-4">
      {free ? (
        <p className="rounded-control border border-border bg-bg p-3 text-meta text-muted">{t("staffAdmin.enrol.payment.freeOnly")}</p>
      ) : (
        <div className="grid gap-2">
          <span className="text-label font-medium text-text">{t("staffAdmin.enrol.payment.typeLabel")}</span>
          <SegmentedControl
            ariaLabel={t("staffAdmin.enrol.payment.typeLabel")}
            value={draft.type}
            options={PAYMENT_TYPES.map((value) => ({ value, label: t(`staffAdmin.enrol.payment.types.${value}`) }))}
            onChange={(value) => onChange({ type: value as PaymentType })}
          />
        </div>
      )}

      {type === "NO_CHARGE" ? (
        <Field label={t("staffAdmin.enrol.reason")} hint={t("staffAdmin.enrol.payment.noChargeHint")} error={message("reason")} required>
          <TextInput
            value={draft.reason}
            maxLength={500}
            invalid={Boolean(message("reason"))}
            onChange={(event) => onChange({ reason: event.target.value })}
          />
        </Field>
      ) : (
        <>
          <div className="grid gap-1.5">
            <span className="text-label font-medium text-text">{t("staffAdmin.enrol.payment.standardPrice")}</span>
            <p className="rounded-control border border-border bg-bg px-3.5 py-2.5 text-body tabular-nums text-text">
              {standardPaisa !== null ? money(standardPaisa) : t("staffAdmin.enrol.payment.noPrice")}
            </p>
          </div>

          <Field
            label={t("staffAdmin.enrol.payment.total")}
            hint={hasStandard ? t("staffAdmin.enrol.payment.totalHint") : t("staffAdmin.enrol.payment.typeTotal")}
            error={message("total")}
            required
          >
            <TextInput
              inputMode="decimal"
              value={resolved.totalText}
              invalid={Boolean(message("total"))}
              onChange={(event) => onChange({ total: event.target.value })}
            />
          </Field>
          {hasStandard && difference !== null && difference !== 0 ? (
            <p className="text-meta font-medium text-warn">
              {difference > 0
                ? t("staffAdmin.enrol.payment.raised", { amount: money(difference) })
                : t("staffAdmin.enrol.payment.lowered", { amount: money(-difference) })}
            </p>
          ) : null}

          {type === "FULL" ? (
            <div className="grid gap-1.5">
              <span className="text-label font-medium text-text">{t("staffAdmin.enrol.payment.paidFull")}</span>
              <p className="rounded-control border border-border bg-bg px-3.5 py-2.5 text-body tabular-nums text-text">
                {plan ? money(plan.paidPaisa) : resolved.totalText || "—"}
              </p>
            </div>
          ) : (
            <Field
              label={t("staffAdmin.enrol.payment.paidToday")}
              hint={t("staffAdmin.enrol.payment.paidTodayHint")}
              error={message("paid")}
              required
            >
              <TextInput
                inputMode="decimal"
                value={draft.paid}
                invalid={Boolean(message("paid"))}
                onChange={(event) => onChange({ paid: event.target.value })}
              />
            </Field>
          )}

          <Field label={t("staffAdmin.enrol.payment.method")} required>
            <Select value={draft.method} onChange={(event) => onChange({ method: event.target.value as PaymentMethod })}>
              {PAYMENT_METHODS.map((value) => (
                <option key={value} value={value}>
                  {t(`staffAdmin.enrol.payment.methods.${value}`)}
                </option>
              ))}
            </Select>
          </Field>

          <Field
            label={t("staffAdmin.enrol.payment.reference")}
            hint={
              draft.method === "CASH" && draft.reference === null
                ? t("staffAdmin.enrol.payment.referenceHintCash")
                : t("staffAdmin.enrol.payment.referenceHint")
            }
            error={message("reference")}
            required
          >
            <TextInput
              value={resolved.referenceText}
              maxLength={100}
              invalid={Boolean(message("reference"))}
              onChange={(event) => onChange({ reference: event.target.value })}
            />
          </Field>

          <Field
            label={t("staffAdmin.enrol.payment.paidOn")}
            hint={t("staffAdmin.enrol.payment.paidOnHint")}
            error={message("paidOn")}
            required
          >
            <TextInput
              type="date"
              max={today}
              value={resolved.paidOnText}
              invalid={Boolean(message("paidOn"))}
              onChange={(event) => onChange({ paidOn: event.target.value })}
            />
          </Field>

          <Field label={t("staffAdmin.enrol.payment.note")} error={message("note")}>
            <TextInput value={draft.note} maxLength={500} invalid={Boolean(message("note"))} onChange={(event) => onChange({ note: event.target.value })} />
          </Field>

          {type === "INSTALLMENTS" ? (
            <>
              <Field label={t("staffAdmin.enrol.payment.count")} hint={t("staffAdmin.enrol.payment.countHint")} error={message("count")} required>
                <TextInput
                  inputMode="numeric"
                  value={draft.count}
                  invalid={Boolean(message("count"))}
                  onChange={(event) => onChange({ count: event.target.value })}
                />
              </Field>

              {twoInstallments ? (
                <p className="text-meta text-muted">{t("staffAdmin.enrol.payment.twoInstallments")}</p>
              ) : (
                <Field
                  label={t("staffAdmin.enrol.payment.perInstallment")}
                  hint={t("staffAdmin.enrol.payment.perInstallmentHint")}
                  error={message("perInstallment")}
                  required
                >
                  <TextInput
                    inputMode="decimal"
                    value={resolved.perText}
                    invalid={Boolean(message("perInstallment"))}
                    onChange={(event) => onChange({ perInstallment: event.target.value })}
                  />
                </Field>
              )}

              <Field label={t("staffAdmin.enrol.payment.nextOn")} hint={t("staffAdmin.enrol.payment.nextOnHint")} error={message("nextOn")} required>
                <TextInput
                  type="date"
                  min={today}
                  value={resolved.nextOnText}
                  invalid={Boolean(message("nextOn"))}
                  onChange={(event) => onChange({ nextOn: event.target.value })}
                />
              </Field>

              {plan ? <SchedulePreview plan={plan} /> : null}
            </>
          ) : null}
        </>
      )}
    </div>
  );
}

/** The rows as the receipt will show them, each with its date and amount. Shown only when the rules pass. */
function SchedulePreview({ plan }: { plan: PaymentPlan }) {
  const { t } = useLocale();
  const { formatNumber, formatMoney, formatDate } = useFormat();
  return (
    <div className="grid gap-2">
      <p className="text-label font-medium text-text">{t("staffAdmin.enrol.payment.schedule")}</p>
      <ol className="grid divide-y divide-border rounded-control border border-border">
        {plan.rows.map((row) => (
          <li key={row.seq} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2.5 text-meta">
            <span className="font-medium text-text">
              {row.seq === 1
                ? t("staffAdmin.enrol.payment.rowPaid")
                : t("staffAdmin.enrol.payment.rowNumber", { n: formatNumber(row.seq), count: formatNumber(plan.rows.length) })}
            </span>
            <span className="tabular-nums text-muted">{formatDate(row.dueOn)}</span>
            <span className="tabular-nums text-text">{formatMoney(row.paisa / 100)}</span>
          </li>
        ))}
      </ol>
      <p className="text-meta text-muted">
        {t("staffAdmin.enrol.payment.stillDue", { amount: formatMoney(plan.duePaisa / 100) ?? "" })}
      </p>
    </div>
  );
}
