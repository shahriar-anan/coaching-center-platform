"use client";

import { useState, type FormEvent } from "react";
import { ArrowDown, ArrowUp } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { IconButton } from "@/components/ui/icon-button";
import { ListCard } from "@/components/ui/list-card";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useGroups } from "@/lib/api/accounting/groups";
import { useGroupTypes, useReorderTypes, useSaveType, useTypeAction, type TypeBody } from "@/lib/api/accounting/account-types";
import type { AccType, Group, TypeKind } from "@/lib/api/accounting/types";
import { moveId, pickName, writeErrorText } from "../accounting-model";

/** Settings, Types tab (accounting-plan 4.2): the types of one group. Kind is fixed once an entry uses the type. */
export function TypesSettings() {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const liveGroups = useGroups(false);
  const groupList = liveGroups.data ?? [];
  const [chosen, setChosen] = useState<string | null>(null);
  const groupId = chosen ?? groupList[0]?.id ?? "";
  const group = groupList.find((item) => item.id === groupId) ?? null;

  const [showArchived, setShowArchived] = useState(false);
  const types = useGroupTypes(groupId, showArchived);
  const liveTypes = useGroupTypes(groupId, false);
  const reorder = useReorderTypes();
  const action = useTypeAction();
  const [editing, setEditing] = useState<AccType | "new" | null>(null);
  const [archiving, setArchiving] = useState<AccType | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const liveIds = (liveTypes.data ?? []).map((type) => type.id);

  async function move(type: AccType, step: -1 | 1) {
    const next = moveId(liveIds, type.id, step);
    if (!next) {
      return;
    }
    setActionError(null);
    try {
      await reorder.mutateAsync({ groupId, ids: next });
    } catch (error) {
      setActionError(writeErrorText(error, t));
    }
  }

  async function run(type: AccType, verb: "archive" | "restore") {
    setActionError(null);
    try {
      await action.mutateAsync({ id: type.id, action: verb });
      setArchiving(null);
      toast({ title: t(verb === "archive" ? "accounting.common.archived" : "accounting.settings.types.saved"), tone: "ok" });
    } catch (error) {
      setArchiving(null);
      setActionError(writeErrorText(error, t));
    }
  }

  if (liveGroups.isError) {
    return <ErrorState onRetry={() => void liveGroups.refetch()} error={liveGroups.error} />;
  }

  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
        <Field label={t("accounting.settings.types.group")}>
          <Select value={groupId} onChange={(event) => setChosen(event.target.value)}>
            {groupList.map((item) => (
              <option key={item.id} value={item.id}>
                {pickName(item, locale)}
              </option>
            ))}
          </Select>
        </Field>
        <Button onClick={() => setEditing("new")} disabled={!group}>
          {t("accounting.settings.types.add")}
        </Button>
      </div>
      <Switch checked={showArchived} onCheckedChange={setShowArchived} label={t("accounting.common.showArchived")} />
      {actionError ? <p role="alert" className="text-meta text-danger">{actionError}</p> : null}

      {types.isError ? (
        <ErrorState onRetry={() => void types.refetch()} error={types.error} />
      ) : (types.data ?? []).length === 0 && !types.isPending ? (
        <EmptyState title={t("accounting.settings.types.empty")} />
      ) : (
        <ListCard>
          {(types.data ?? []).map((type) => {
            const position = liveIds.indexOf(type.id);
            return (
              <li key={type.id} className="grid gap-3 p-4 sm:grid-cols-[1fr_auto] sm:items-center">
                <div className="grid gap-1">
                  <p className="font-medium text-text">{pickName(type, locale)}</p>
                  <p className="text-meta text-muted">
                    {t(`accounting.typeKind.${type.kind}`)}
                    {type.kind === "COUNTED" && (type.unitEn || type.unitBn) ? ` · ${pickName({ nameEn: type.unitEn ?? "", nameBn: type.unitBn }, locale)}` : ""}
                  </p>
                  <div className="flex flex-wrap gap-1">
                    {type.needsPeriod ? <Pill tone="accent">{t("accounting.settings.types.needsPeriod")}</Pill> : null}
                    {type.needsDescription ? <Pill tone="accent">{t("accounting.settings.types.needsDescription")}</Pill> : null}
                    {type.used ? <Pill tone="muted">{t("accounting.settings.types.used")}</Pill> : null}
                    {type.archived ? <Pill tone="warn">{t("accounting.common.archived")}</Pill> : null}
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {type.archived ? (
                    <Button variant="secondary" size="sm" onClick={() => void run(type, "restore")}>
                      {t("accounting.common.restore")}
                    </Button>
                  ) : (
                    <>
                      <IconButton label={t("accounting.common.moveUp")} icon={<ArrowUp aria-hidden className="size-4" />} disabled={position <= 0} onClick={() => void move(type, -1)} />
                      <IconButton label={t("accounting.common.moveDown")} icon={<ArrowDown aria-hidden className="size-4" />} disabled={position >= liveIds.length - 1} onClick={() => void move(type, 1)} />
                      <Button variant="secondary" size="sm" onClick={() => setEditing(type)}>
                        {t("accounting.common.edit")}
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setArchiving(type)}>
                        {t("accounting.common.archive")}
                      </Button>
                    </>
                  )}
                </div>
              </li>
            );
          })}
        </ListCard>
      )}

      {editing && group ? (
        <TypeForm
          key={editing === "new" ? `new-${groupId}` : editing.id}
          group={group}
          type={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}

      <ConfirmDialog
        open={archiving !== null}
        onOpenChange={(open) => !open && setArchiving(null)}
        title={t("accounting.settings.types.archiveTitle", { name: archiving ? pickName(archiving, locale) : "" })}
        body={t("accounting.settings.types.archiveBody")}
        confirmLabel={t("accounting.common.archive")}
        tone="danger"
        onConfirm={async () => { if (archiving) await run(archiving, "archive"); }}
      />
    </div>
  );
}

const KINDS: TypeKind[] = ["COUNTED", "AMOUNT", "DEDUCTION"];

/** Adds a type to a group, or changes one. A deduction lowers a balance, so only a Payees group can have one (the API refuses otherwise). */
function TypeForm({ group, type, onClose }: { group: Group; type: AccType | null; onClose: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const save = useSaveType();
  const [nameEn, setNameEn] = useState(type?.nameEn ?? "");
  const [nameBn, setNameBn] = useState(type?.nameBn ?? "");
  const [kind, setKind] = useState<TypeKind>(type?.kind ?? "AMOUNT");
  const [unitEn, setUnitEn] = useState(type?.unitEn ?? "");
  const [unitBn, setUnitBn] = useState(type?.unitBn ?? "");
  const [needsPeriod, setNeedsPeriod] = useState(type?.needsPeriod ?? false);
  const [needsDescription, setNeedsDescription] = useState(type?.needsDescription ?? false);
  const [errors, setErrors] = useState<{ nameEn?: string; unitEn?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);

  const kindLocked = Boolean(type?.used);
  const counted = kind === "COUNTED";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problems: { nameEn?: string; unitEn?: string } = {};
    if (!nameEn.trim() || nameEn.trim().length > 100) {
      problems.nameEn = t("accounting.settings.types.nameEn");
    }
    if (counted && (!unitEn.trim() || unitEn.trim().length > 30)) {
      problems.unitEn = t("accounting.settings.types.unitEn");
    }
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    setFormError(null);
    const body: TypeBody = {
      nameEn: nameEn.trim(),
      nameBn: nameBn.trim() || null,
      kind,
      unitEn: counted ? unitEn.trim() : null,
      unitBn: counted ? unitBn.trim() || null : null,
      needsPeriod,
      needsDescription,
      ...(type ? { version: type.version } : {}),
    };
    try {
      await save.mutateAsync({ groupId: group.id, id: type?.id, body });
      toast({ title: t("accounting.settings.types.saved"), tone: "ok" });
      onClose();
    } catch (error) {
      setFormError(writeErrorText(error, t));
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={type ? t("accounting.settings.types.editTitle") : t("accounting.settings.types.addTitle")}>
      <form className="grid gap-4" onSubmit={(event) => void submit(event)} noValidate>
        <Field label={t("accounting.settings.types.nameEn")} required error={errors.nameEn}>
          <TextInput value={nameEn} onChange={(event) => setNameEn(event.target.value)} maxLength={100} invalid={Boolean(errors.nameEn)} />
        </Field>
        <Field label={t("accounting.settings.types.nameBn")}>
          <TextInput value={nameBn} onChange={(event) => setNameBn(event.target.value)} maxLength={100} />
        </Field>
        <Field label={t("accounting.settings.types.kind")} hint={kindLocked ? t("accounting.settings.types.kindLocked") : undefined}>
          <Select value={kind} disabled={kindLocked} onChange={(event) => setKind(event.target.value as TypeKind)}>
            {KINDS.filter((item) => item !== "DEDUCTION" || group.mode === "PAYEES").map((item) => (
              <option key={item} value={item}>
                {t(`accounting.typeKind.${item}`)}
              </option>
            ))}
          </Select>
        </Field>
        {counted ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("accounting.settings.types.unitEn")} required hint={t("accounting.settings.types.unitHint")} error={errors.unitEn}>
              <TextInput value={unitEn} onChange={(event) => setUnitEn(event.target.value)} maxLength={30} invalid={Boolean(errors.unitEn)} />
            </Field>
            <Field label={t("accounting.settings.types.unitBn")}>
              <TextInput value={unitBn} onChange={(event) => setUnitBn(event.target.value)} maxLength={30} />
            </Field>
          </div>
        ) : null}
        <div className="grid gap-1">
          <Switch checked={needsPeriod} onCheckedChange={setNeedsPeriod} label={t("accounting.settings.types.needsPeriod")} />
          <p className="text-meta text-muted">{t("accounting.settings.types.needsPeriodHint")}</p>
        </div>
        <div className="grid gap-1">
          <Switch checked={needsDescription} onCheckedChange={setNeedsDescription} label={t("accounting.settings.types.needsDescription")} />
          <p className="text-meta text-muted">{t("accounting.settings.types.needsDescriptionHint")}</p>
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
