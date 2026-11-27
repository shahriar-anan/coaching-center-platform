"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Pill } from "@/components/ui/pill";
import { Sheet } from "@/components/ui/sheet";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useGroupPayees } from "@/lib/api/accounting/payees";
import { useGroups } from "@/lib/api/accounting/groups";
import type { Entry, EntryKind, Group, PayeeRow } from "@/lib/api/accounting/types";
import { pickName } from "../accounting-model";
import { EntryForm } from "./entry-form";

const KINDS: EntryKind[] = ["CHARGE", "PAYMENT", "SPEND", "INCOME"];

/** The groups a kind can go into (plan 4.4): charges and payments in Payees groups, spending in Log expense groups, income in Log income groups. */
export function groupsFor(kind: EntryKind, groups: Group[]): Group[] {
  return groups.filter((group) => {
    if (kind === "CHARGE" || kind === "PAYMENT") {
      return group.mode === "PAYEES";
    }
    if (kind === "SPEND") {
      return group.mode === "LOG" && group.direction === "EXPENSE";
    }
    return group.mode === "LOG" && group.direction === "INCOME";
  });
}

/**
 * "+ Add" (B14, plan 4.6): what do you want to add, then the group, then the person, then the form. A page that already knows the
 * kind, group or person passes it in and that step is skipped.
 */
export function QuickAddButton({ kind, group, payee, label }: { kind?: EntryKind; group?: Group; payee?: PayeeRow; label?: string }) {
  const { t } = useLocale();
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button onClick={() => setOpen(true)}>{label ?? `+ ${t("accounting.quick.add")}`}</Button>
      {open ? <QuickAddSheet kind={kind} group={group} payee={payee} onClose={() => setOpen(false)} /> : null}
    </>
  );
}

export function QuickAddSheet({
  kind: presetKind,
  group: presetGroup,
  payee: presetPayee,
  onClose,
  onSaved,
}: {
  kind?: EntryKind;
  group?: Group;
  payee?: PayeeRow;
  onClose: () => void;
  onSaved?: (entries: Entry[]) => void;
}) {
  const { t, locale } = useLocale();
  const groups = useGroups(false);
  const [kind, setKind] = useState<EntryKind | null>(presetKind ?? null);
  const [group, setGroup] = useState<Group | null>(presetGroup ?? null);
  const [payee, setPayee] = useState<PayeeRow | null>(presetPayee ?? null);

  const title = !kind
    ? t("accounting.quick.whatTitle")
    : !group
      ? t("accounting.quick.groupTitle")
      : !payee && group.mode === "PAYEES"
        ? t("accounting.quick.payeeTitle")
        : t(`accounting.quick.kind.${kind}`);

  function back() {
    if (payee && !presetPayee) {
      setPayee(null);
    } else if (group && !presetGroup) {
      setGroup(null);
    } else if (kind && !presetKind) {
      setKind(null);
    }
  }
  const canGoBack = Boolean((payee && !presetPayee) || (group && !presetGroup) || (kind && !presetKind));

  return (
    <Sheet open onOpenChange={(open) => !open && onClose()} title={title}>
      <div className="grid gap-4">
        {canGoBack ? (
          <Button variant="ghost" size="sm" className="justify-self-start" onClick={back}>
            {t("accounting.quick.back")}
          </Button>
        ) : null}

        {!kind ? (
          <div className="grid gap-2 sm:grid-cols-2">
            {KINDS.map((item) => (
              <Button key={item} variant="secondary" fullWidth onClick={() => setKind(item)}>
                {t(`accounting.quick.kind.${item}`)}
              </Button>
            ))}
          </div>
        ) : null}

        {kind && !group ? (
          <div className="grid gap-2">
            {groupsFor(kind, groups.data ?? []).map((item) => (
              <Button key={item.id} variant="secondary" fullWidth onClick={() => setGroup(item)}>
                {pickName(item, locale)}
              </Button>
            ))}
            {groupsFor(kind, groups.data ?? []).length === 0 && !groups.isPending ? (
              <p className="text-body text-muted">{t("accounting.quick.noGroups")}</p>
            ) : null}
          </div>
        ) : null}

        {kind && group && !payee && group.mode === "PAYEES" ? (
          <PayeePicker group={group} onPick={setPayee} />
        ) : null}

        {kind && group && (payee || group.mode === "LOG") ? (
          <EntryForm
            kind={kind}
            group={group}
            payee={payee}
            onSaved={(entries) => {
              onSaved?.(entries);
              onClose();
            }}
            onCancel={onClose}
          />
        ) : null}
      </div>
    </Sheet>
  );
}

/** The people of one Payees group, with a search. Picking one moves on to the form. */
function PayeePicker({ group, onPick }: { group: Group; onPick: (payee: PayeeRow) => void }) {
  const { t } = useLocale();
  const [query, setQuery] = useState("");
  const payees = useGroupPayees(group.id, { archived: false });
  const needle = query.trim().toLowerCase();
  const rows = (payees.data ?? []).filter((row) => !needle || row.name.toLowerCase().includes(needle) || (row.phone ?? "").includes(needle));
  return (
    <div className="grid gap-3">
      <TextInput value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t("accounting.payees.search")} aria-label={t("accounting.payees.search")} />
      <ul className="grid gap-2">
        {rows.map((row) => (
          <li key={row.id}>
            <Button variant="secondary" fullWidth onClick={() => onPick(row)} className="justify-between">
              <span>{row.name}</span>
              {row.balance !== "0.00" ? <Pill tone={Number(row.balance) > 0 ? "warn" : "accent"}>{t(Number(row.balance) > 0 ? "accounting.balance.owedShort" : "accounting.balance.advanceShort")}</Pill> : null}
            </Button>
          </li>
        ))}
      </ul>
      {rows.length === 0 && !payees.isPending ? <p className="text-body text-muted">{t("accounting.payees.none")}</p> : null}
    </div>
  );
}
