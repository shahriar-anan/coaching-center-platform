"use client";

import { useCallback, useEffect, useState } from "react";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { useUser } from "@/lib/auth/session-context";
import { api } from "@/lib/api/client";
import type { PaymentView } from "@/lib/api/types";

type PayableType = "COURSE_ENROLLMENT" | "BOOK_ORDER";
type PaymentStatus = "PENDING" | "SUCCESS" | "FAILED";

const TYPES: PayableType[] = ["COURSE_ENROLLMENT", "BOOK_ORDER"];
const STATUSES: PaymentStatus[] = ["PENDING", "SUCCESS", "FAILED"];

export default function PaymentsPage() {
  const { t } = useLocale();
  const user = useUser();
  const isMaster = user?.role === "MASTER_ADMIN";
  const [payableType, setPayableType] = useState<PayableType>("COURSE_ENROLLMENT");
  const [status, setStatus] = useState<PaymentStatus>("PENDING");
  const [rows, setRows] = useState<PaymentView[]>([]);
  const [selected, setSelected] = useState<PaymentView | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.listPayments(status, payableType, 0);
      setRows(data.content);
    } catch (err) {
      setError(err);
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [payableType, status]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- client-side list fetch
    void load();
  }, [load]);

  async function verify(id: string, result: "SUCCESS" | "FAILED") {
    setConfirming(true);
    setError(null);
    try {
      await api.verifyPayment(id, result);
      setSelected(null);
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setConfirming(false);
    }
  }

  function statusLabel(value: string) {
    if (value === "SUCCESS") {
      return t("payments.statusPaid");
    }
    if (value === "FAILED") {
      return t("payments.statusFailed");
    }
    return t("payments.statusPending");
  }

  function methodLabel(value: string) {
    if (value === "BKASH") {
      return t("payments.methodBkash");
    }
    if (value === "NAGAD") {
      return t("payments.methodNagad");
    }
    return value;
  }

  function typeLabel(value: PayableType) {
    return value === "COURSE_ENROLLMENT" ? t("payments.tabCourses") : t("payments.tabBooks");
  }

  function filterLabel(value: PaymentStatus) {
    if (value === "SUCCESS") {
      return t("payments.filterPaid");
    }
    if (value === "FAILED") {
      return t("payments.filterFailed");
    }
    return t("payments.filterPending");
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold">{t("payments.title")}</h1>
      <div role="tablist" aria-label={t("payments.groups")} className="mt-6 flex gap-2">
        {TYPES.map((type) => {
          const selectedType = payableType === type;
          return (
            <button
              key={type}
              type="button"
              role="tab"
              aria-selected={selectedType}
              className={
                selectedType
                  ? "rounded-md bg-zinc-900 px-3 py-1.5 text-sm text-white"
                  : "rounded-md border border-zinc-300 px-3 py-1.5 text-sm"
              }
              onClick={() => {
                setPayableType(type);
                setSelected(null);
              }}
            >
              {typeLabel(type)}
            </button>
          );
        })}
      </div>
      <div role="group" aria-label={t("payments.filters")} className="mt-4 flex gap-2">
        {STATUSES.map((value) => {
          const pressed = status === value;
          return (
            <button
              key={value}
              type="button"
              aria-pressed={pressed}
              className={
                pressed
                  ? "rounded-full bg-zinc-200 px-3 py-1 text-sm font-medium"
                  : "rounded-full border border-zinc-300 px-3 py-1 text-sm"
              }
              onClick={() => {
                setStatus(value);
                setSelected(null);
              }}
            >
              {filterLabel(value)}
            </button>
          );
        })}
      </div>
      <FormError error={error} />
      {loading ? (
        <p className="mt-8">{t("common.loading")}</p>
      ) : rows.length === 0 ? (
        <p className="mt-8 text-zinc-600">{t("payments.empty")}</p>
      ) : (
        <ul className="mt-6 grid gap-3 sm:grid-cols-2">
          {rows.map((row) => (
            <li key={row.id}>
              <button
                type="button"
                className="w-full rounded-lg border border-zinc-200 bg-white p-4 text-left hover:border-zinc-400"
                onClick={() => setSelected(row)}
              >
                <span className="flex items-start justify-between gap-3">
                  <span className="font-medium">{row.titleEn || t("payments.item")}</span>
                  <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs">{statusLabel(row.status)}</span>
                </span>
                {row.titleBn ? <span className="mt-1 block text-sm text-zinc-600">{row.titleBn}</span> : null}
                <span className="mt-3 block text-sm">
                  {row.studentName || row.studentId} · {row.amount}
                </span>
                <span className="mt-1 block font-mono text-xs text-zinc-600">{row.transactionRef}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {selected ? (
        <div
          className="fixed inset-0 z-20 flex items-center justify-center bg-black/40 p-4"
          onClick={() => setSelected(null)}
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="payment-detail-title"
            className="w-full max-w-md rounded-lg bg-white p-6 shadow-lg"
            onClick={(event) => event.stopPropagation()}
          >
            <h2 id="payment-detail-title" className="text-lg font-semibold">
              {selected.titleEn || t("payments.item")}
            </h2>
            {selected.titleBn ? <p className="text-sm text-zinc-600">{selected.titleBn}</p> : null}
            <dl className="mt-4 space-y-2 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">{t("payments.student")}</dt>
                <dd>{selected.studentName || selected.studentId}</dd>
              </div>
              {selected.studentPhone ? (
                <div className="flex justify-between gap-4">
                  <dt className="text-zinc-500">{t("payments.phone")}</dt>
                  <dd>{selected.studentPhone}</dd>
                </div>
              ) : null}
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">{t("payments.amount")}</dt>
                <dd>{selected.amount}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">{t("payments.method")}</dt>
                <dd>{methodLabel(selected.method)}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">{t("payments.transactionRef")}</dt>
                <dd className="font-mono text-xs">{selected.transactionRef}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-zinc-500">{t("payments.filters")}</dt>
                <dd>{statusLabel(selected.status)}</dd>
              </div>
              {selected.createdAt ? (
                <div className="flex justify-between gap-4">
                  <dt className="text-zinc-500">{t("payments.submitted")}</dt>
                  <dd>{new Date(selected.createdAt).toLocaleString()}</dd>
                </div>
              ) : null}
            </dl>
            <div className="mt-6 flex flex-wrap justify-end gap-2">
              {isMaster && selected.status === "PENDING" ? (
                <>
                  <button
                    type="button"
                    className="rounded-md bg-zinc-900 px-3 py-1.5 text-sm text-white disabled:opacity-50"
                    disabled={confirming}
                    onClick={() => verify(selected.id, "SUCCESS")}
                  >
                    {t("payments.verifySuccess")}
                  </button>
                  <button
                    type="button"
                    className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm disabled:opacity-50"
                    disabled={confirming}
                    onClick={() => verify(selected.id, "FAILED")}
                  >
                    {t("payments.verifyFailed")}
                  </button>
                </>
              ) : null}
              <button
                type="button"
                className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm"
                onClick={() => setSelected(null)}
              >
                {t("payments.close")}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
