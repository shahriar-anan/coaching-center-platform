"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useLinkableUsers, useSavePayee, type PayeeBody } from "@/lib/api/accounting/payees";
import type { Group, Payee } from "@/lib/api/accounting/types";
import { writeErrorText } from "../accounting-model";

/** Adds a person to a Payees group, or changes one (accounting-plan 4.3). Other spellings are for the Offline instructors group only. */
export function PayeeForm({ group, payee, onClose, onSaved }: { group: Group; payee: Payee | null; onClose: () => void; onSaved?: (payee: Payee) => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const save = useSavePayee();
  const isInstructors = group.systemKey === "OFFLINE_INSTRUCTORS";
  const [name, setName] = useState(payee?.name ?? "");
  const [phone, setPhone] = useState(payee?.phone ?? "");
  const [note, setNote] = useState(payee?.note ?? "");
  const [aliases, setAliases] = useState((payee?.aliases ?? []).join("\n"));
  const [userId, setUserId] = useState<string | null>(payee?.userId ?? null);
  const [userName, setUserName] = useState<string | null>(payee?.userName ?? null);
  const [userSearch, setUserSearch] = useState("");
  const users = useLinkableUsers(userSearch, userSearch.trim().length > 0);
  const [errors, setErrors] = useState<{ name?: string; phone?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problems: { name?: string; phone?: string } = {};
    if (!name.trim() || name.trim().length > 200) {
      problems.name = t("accounting.payees.form.name");
    }
    if (phone.trim().length > 20) {
      problems.phone = t("accounting.payees.form.phoneTooLong");
    }
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    setFormError(null);
    const body: PayeeBody = {
      name: name.trim(),
      phone: phone.trim() || null,
      note: note.trim() || null,
      aliases: isInstructors
        ? Array.from(new Set(aliases.split("\n").map((line) => line.trim()).filter(Boolean)))
        : [],
      userId,
      ...(payee ? { version: payee.version } : {}),
    };
    try {
      const saved = await save.mutateAsync({ groupId: group.id, id: payee?.id, body });
      toast({ title: t("accounting.payees.saved"), tone: "ok" });
      onSaved?.(saved);
      onClose();
    } catch (error) {
      setFormError(writeErrorText(error, t));
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={payee ? t("accounting.payees.editTitle") : t("accounting.payees.addTitle")}>
      <form className="grid gap-4" onSubmit={(event) => void submit(event)} noValidate>
        <Field label={t("accounting.payees.form.name")} required error={errors.name}>
          <TextInput value={name} onChange={(event) => setName(event.target.value)} maxLength={200} invalid={Boolean(errors.name)} />
        </Field>
        <Field label={t("accounting.payees.form.phone")} error={errors.phone}>
          <TextInput inputMode="tel" value={phone} onChange={(event) => setPhone(event.target.value)} maxLength={20} invalid={Boolean(errors.phone)} />
        </Field>
        {isInstructors ? (
          <Field label={t("accounting.payees.form.aliases")} hint={t("accounting.payees.form.aliasesHint")}>
            <Textarea value={aliases} onChange={(event) => setAliases(event.target.value)} rows={3} />
          </Field>
        ) : null}
        <Field label={t("accounting.payees.form.note")} hint={t("accounting.payees.form.noteHint")}>
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} rows={2} />
        </Field>

        <div className="grid gap-2 rounded-card border border-border p-3">
          <p className="text-label font-semibold text-text">{t("accounting.payees.form.linked")}</p>
          <p className="text-meta text-muted">{t("accounting.payees.form.linkedHint")}</p>
          {userId ? (
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="text-body text-text">{userName ?? userId}</span>
              <Button variant="ghost" size="sm" onClick={() => { setUserId(null); setUserName(null); }}>
                {t("accounting.payees.form.unlink")}
              </Button>
            </div>
          ) : (
            <>
              <TextInput value={userSearch} onChange={(event) => setUserSearch(event.target.value)} placeholder={t("accounting.payees.form.searchUsers")} aria-label={t("accounting.payees.form.searchUsers")} />
              <ul className="grid gap-2">
                {(users.data ?? []).map((user) => (
                  <li key={user.id}>
                    <Button variant="secondary" size="sm" fullWidth onClick={() => { setUserId(user.id); setUserName(user.name); setUserSearch(""); }}>
                      {user.name}
                    </Button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>

        {formError ? <p role="alert" className="text-meta text-danger">{formError}</p> : null}
        <div className="sticky bottom-0 flex justify-end gap-2 bg-card pt-2">
          <Button variant="ghost" onClick={onClose}>
            {t("accounting.common.cancel")}
          </Button>
          <Button type="submit" loading={save.isPending}>
            {save.isPending ? t("accounting.common.saving") : t("accounting.common.save")}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
