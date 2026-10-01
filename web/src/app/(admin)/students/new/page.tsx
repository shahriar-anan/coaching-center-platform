"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { ActivationCodePanel } from "@/components/activation-code-panel";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";

export default function NewStudentPage() {
  const { t } = useLocale();
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [activationCode, setActivationCode] = useState<string | null>(null);
  const [studentCode, setStudentCode] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const created = await api.createStudent({ fullName, phone, email });
      setActivationCode(created.activationCode);
      setStudentCode(created.studentCode);
      setFullName("");
      setPhone("");
      setEmail("");
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="max-w-lg">
      <h1 className="text-2xl font-semibold">{t("students.createTitle")}</h1>
      {activationCode ? <ActivationCodePanel code={activationCode} /> : null}
      {studentCode ? (
        <p className="mt-4 text-sm text-zinc-600">
          {t("students.code")}: <span className="font-mono">{studentCode}</span>
        </p>
      ) : null}
      <form className="mt-6 space-y-4" onSubmit={onSubmit}>
        <div>
          <label className="block text-sm font-medium" htmlFor="fullName">
            {t("students.fullName")}
          </label>
          <input
            id="fullName"
            required
            className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
          />
        </div>
        <div>
          <label className="block text-sm font-medium" htmlFor="phone">
            {t("students.phone")}
          </label>
          <input
            id="phone"
            required
            className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
          />
        </div>
        <div>
          <label className="block text-sm font-medium" htmlFor="email">
            {t("students.email")}
          </label>
          <input
            id="email"
            type="email"
            required
            className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        {error ? <FormError error={error} /> : null}
        <div className="flex gap-3">
          <button
            type="submit"
            disabled={submitting}
            className="rounded-md bg-zinc-900 px-4 py-2 text-white hover:bg-zinc-800 disabled:opacity-60"
          >
            {submitting ? t("common.loading") : t("common.create")}
          </button>
          <Link
            href="/students"
            className="rounded-md border border-zinc-300 px-4 py-2 hover:bg-zinc-50"
          >
            {t("common.cancel")}
          </Link>
        </div>
      </form>
    </div>
  );
}
