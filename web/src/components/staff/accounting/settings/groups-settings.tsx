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
import { useGroupAction, useGroups, useReorderGroups, useSaveGroup } from "@/lib/api/accounting/groups";
import type { Direction, Group, GroupMode } from "@/lib/api/accounting/types";
import { moveId, pickName, writeErrorText } from "../accounting-model";

/** Settings, Groups tab (accounting-plan 4.1): add with mode and direction, rename, Count in Finance, order, archive and restore. */
export function GroupsSettings() {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const [showArchived, setShowArchived] = useState(false);
  const groups = useGroups(showArchived);
  const liveGroups = useGroups(false);
  const reorder = useReorderGroups();
  const action = useGroupAction();
  const [editing, setEditing] = useState<Group | "new" | null>(null);
  const [archiving, setArchiving] = useState<Group | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const liveIds = (liveGroups.data ?? []).map((group) => group.id);

  async function move(group: Group, step: -1 | 1) {
    const next = moveId(liveIds, group.id, step);
    if (!next) {
      return;
    }
    setActionError(null);
    try {
      await reorder.mutateAsync(next);
    } catch (error) {
      setActionError(writeErrorText(error, t));
    }
  }

  async function restore(group: Group) {
    setActionError(null);
    try {
      await action.mutateAsync({ id: group.id, action: "restore" });
      toast({ title: t("accounting.settings.groups.saved"), tone: "ok" });
    } catch (error) {
      setActionError(writeErrorText(error, t));
    }
  }

  async function archive(group: Group) {
    setActionError(null);
    try {
      await action.mutateAsync({ id: group.id, action: "archive" });
      setArchiving(null);
      toast({ title: t("accounting.common.archived"), tone: "ok" });
    } catch (error) {
      setArchiving(null);
      setActionError(writeErrorText(error, t));
    }
  }

  if (groups.isError) {
    return <ErrorState onRetry={() => void groups.refetch()} error={groups.error} />;
  }
  const rows = groups.data ?? [];

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Switch checked={showArchived} onCheckedChange={setShowArchived} label={t("accounting.common.showArchived")} />
        <Button onClick={() => setEditing("new")}>{t("accounting.settings.groups.add")}</Button>
      </div>
      {actionError ? <p role="alert" className="text-meta text-danger">{actionError}</p> : null}

      {rows.length === 0 && !groups.isPending ? (
        <EmptyState title={t("accounting.settings.groups.empty")} />
      ) : (
        <ListCard>
          {rows.map((group) => {
            const live = !group.archived;
            const position = liveIds.indexOf(group.id);
            return (
              <li key={group.id} className="grid gap-3 p-4 sm:grid-cols-[1fr_auto] sm:items-center">
                <div className="grid gap-1">
                  <p className="font-medium text-text">{pickName(group, locale)}</p>
                  <p className="text-meta text-muted">
                    {t(`accounting.groupMode.${group.mode}`)} · {t(`accounting.direction.${group.direction}`)}
                  </p>
                  <div className="flex flex-wrap gap-1">
                    {group.systemKey ? <Pill tone="muted">{t("accounting.settings.builtIn")}</Pill> : null}
                    <Pill tone={group.inFinance ? "ok" : "muted"}>
                      {group.inFinance ? t("accounting.settings.groups.pillIn") : t("accounting.settings.groups.pillOut")}
                    </Pill>
                    {group.archived ? <Pill tone="warn">{t("accounting.common.archived")}</Pill> : null}
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {live ? (
                    <>
                      <IconButton label={t("accounting.common.moveUp")} icon={<ArrowUp aria-hidden className="size-4" />} disabled={position <= 0} onClick={() => void move(group, -1)} />
                      <IconButton label={t("accounting.common.moveDown")} icon={<ArrowDown aria-hidden className="size-4" />} disabled={position >= liveIds.length - 1} onClick={() => void move(group, 1)} />
                      <Button variant="secondary" size="sm" onClick={() => setEditing(group)}>
                        {t("accounting.common.edit")}
                      </Button>
                      {group.systemKey === "OFFLINE_INSTRUCTORS" ? (
                        <span className="text-meta text-muted">{t("accounting.settings.groups.cannotArchive")}</span>
                      ) : (
                        <Button variant="ghost" size="sm" onClick={() => setArchiving(group)}>
                          {t("accounting.common.archive")}
                        </Button>
                      )}
                    </>
                  ) : (
                    <Button variant="secondary" size="sm" onClick={() => void restore(group)}>
                      {t("accounting.common.restore")}
                    </Button>
                  )}
                </div>
              </li>
            );
          })}
        </ListCard>
      )}

      {editing ? (
        <GroupForm
          key={editing === "new" ? "new" : editing.id}
          group={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
        />
      ) : null}

      <ConfirmDialog
        open={archiving !== null}
        onOpenChange={(open) => !open && setArchiving(null)}
        title={t("accounting.settings.groups.archiveTitle", { name: archiving ? pickName(archiving, locale) : "" })}
        body={t("accounting.settings.groups.archiveBody")}
        confirmLabel={t("accounting.common.archive")}
        tone="danger"
        onConfirm={async () => { if (archiving) await archive(archiving); }}
      />
    </div>
  );
}

/** Adds a group, or changes the name and Count in Finance of one. Mode and direction are set once, when the group is made. */
function GroupForm({ group, onClose }: { group: Group | null; onClose: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const save = useSaveGroup();
  const [nameEn, setNameEn] = useState(group?.nameEn ?? "");
  const [nameBn, setNameBn] = useState(group?.nameBn ?? "");
  const [inFinance, setInFinance] = useState(group?.inFinance ?? true);
  const [mode, setMode] = useState<GroupMode>(group?.mode ?? "PAYEES");
  const [direction, setDirection] = useState<Direction>(group?.direction ?? "EXPENSE");
  const [nameError, setNameError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  // An income group is always Log (plan 4.1), so the mode follows the direction.
  const effectiveMode: GroupMode = direction === "INCOME" ? "LOG" : mode;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = nameEn.trim();
    if (!trimmed || trimmed.length > 100) {
      setNameError(t("accounting.settings.groups.nameEn"));
      return;
    }
    setNameError(null);
    setFormError(null);
    const bnName = nameBn.trim() || null;
    try {
      if (group) {
        await save.mutateAsync({ id: group.id, body: { nameEn: trimmed, nameBn: bnName, inFinance, version: group.version } });
      } else {
        await save.mutateAsync({
          body: { nameEn: trimmed, nameBn: bnName, inFinance, mode: effectiveMode, direction },
        });
      }
      toast({ title: t("accounting.settings.groups.saved"), tone: "ok" });
      onClose();
    } catch (error) {
      setFormError(writeErrorText(error, t));
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={group ? t("accounting.settings.groups.editTitle") : t("accounting.settings.groups.addTitle")}>
      <form className="grid gap-4" onSubmit={(event) => void submit(event)} noValidate>
        <Field label={t("accounting.settings.groups.nameEn")} required error={nameError ?? undefined}>
          <TextInput value={nameEn} onChange={(event) => setNameEn(event.target.value)} maxLength={100} invalid={Boolean(nameError)} />
        </Field>
        <Field label={t("accounting.settings.groups.nameBn")} hint={t("accounting.settings.groups.nameHint")}>
          <TextInput value={nameBn} onChange={(event) => setNameBn(event.target.value)} maxLength={100} />
        </Field>
        {group ? (
          <p className="text-meta text-muted">
            {t(`accounting.groupMode.${group.mode}`)} · {t(`accounting.direction.${group.direction}`)}
          </p>
        ) : (
          <>
            <Field label={t("accounting.settings.groups.direction")}>
              <Select value={direction} onChange={(event) => setDirection(event.target.value as Direction)}>
                <option value="EXPENSE">{t("accounting.direction.EXPENSE")}</option>
                <option value="INCOME">{t("accounting.direction.INCOME")}</option>
              </Select>
            </Field>
            <Field label={t("accounting.settings.groups.mode")}>
              <Select value={effectiveMode} disabled={direction === "INCOME"} onChange={(event) => setMode(event.target.value as GroupMode)}>
                <option value="PAYEES">{t("accounting.groupMode.PAYEES")}</option>
                <option value="LOG">{t("accounting.groupMode.LOG")}</option>
              </Select>
            </Field>
          </>
        )}
        <p className="text-meta text-muted">{t("accounting.settings.groups.lockedHint")}</p>
        <div className="grid gap-1">
          <Switch checked={inFinance} onCheckedChange={setInFinance} label={t("accounting.settings.groups.inFinance")} />
          <p className="text-meta text-muted">{t("accounting.settings.groups.inFinanceHint")}</p>
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
