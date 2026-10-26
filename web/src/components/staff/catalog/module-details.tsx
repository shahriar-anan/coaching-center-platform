"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import {
  useCategoryTree,
  useDeleteModule,
  useModuleAction,
  useSaveModule,
  type CategoryTreeNode,
  type ModuleInput,
  type StaffModule,
} from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { catalogErrorKey, fieldErrorFor } from "@/components/staff/catalog/errors";
import { useUnsavedWarning } from "@/components/staff/catalog/use-unsaved-warning";
import { ExamChecklist, type ChecklistItem } from "@/components/staff/catalog/exam-checklist";

type FormState = {
  titleEn: string;
  titleBn: string;
  descriptionEn: string;
  descriptionBn: string;
  categoryId: string;
  price: string;
};

type Errors = Partial<Record<keyof FormState, string>>;

function flatCategories(nodes: CategoryTreeNode[], depth = 0): { id: string; titleEn: string; titleBn: string; depth: number }[] {
  return nodes.flatMap((node) => [
    { id: node.id, titleEn: node.titleEn, titleBn: node.titleBn, depth },
    ...flatCategories(node.children, depth + 1),
  ]);
}

/** A price is blank, or a number above zero with at most two decimal places. */
export function parsePrice(text: string): { ok: true; value: number | null } | { ok: false } {
  const trimmed = text.trim();
  if (trimmed === "") {
    return { ok: true, value: null };
  }
  if (!/^\d+(\.\d{1,2})?$/.test(trimmed)) {
    return { ok: false };
  }
  const value = Number(trimmed);
  return value > 0 ? { ok: true, value } : { ok: false };
}

function initialState(module: StaffModule | undefined): FormState {
  return {
    titleEn: module?.titleEn ?? "",
    titleBn: module?.titleBn ?? "",
    descriptionEn: module?.descriptionEn ?? "",
    descriptionBn: module?.descriptionBn ?? "",
    categoryId: module?.categoryId ?? "",
    price: module?.price == null ? "" : String(module.price),
  };
}

function DetailsBody({ module }: { module?: StaffModule }) {
  const { t, locale } = useLocale();
  const { formatMoney } = useFormat();
  const role = useRole();
  const router = useRouter();
  const { toast } = useToast();
  const tree = useCategoryTree();
  const save = useSaveModule(module?.id);
  const finalSave = useModuleAction(module?.id ?? "", "final-save");
  const publish = useModuleAction(module?.id ?? "", "publish");
  const archive = useModuleAction(module?.id ?? "", "archive");
  const remove = useDeleteModule(module?.id ?? "");

  const [form, setForm] = useState<FormState>(() => initialState(module));
  const [baseline, setBaseline] = useState<FormState>(() => initialState(module));
  const [status, setStatus] = useState<StaffModule | undefined>(module);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<"archive" | "delete" | null>(null);
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  useUnsavedWarning(dirty);

  const archived = status?.status === "ARCHIVED";
  const readOnly = archived || (module ? !role.canAuthor : false);
  const creating = !module;
  const categories = useMemo(() => flatCategories(tree.data ?? []), [tree.data]);

  const priceDisplay = status?.price ?? null;
  const canSetPrice = role.canEditPrice;

  // The checklist before publish. Each item links to the field it names.
  const checklist: ChecklistItem[] = [
    {
      key: "finalSave",
      label: t("staffCatalog.checklist.moduleFinalSave"),
      done: Boolean(status?.finallySaved) && !dirty,
      anchor: "module-final-save",
    },
  ];
  const publishable = checklist.every((item) => item.done) && status?.status !== "PUBLISHED" && !archived;

  function set<K extends keyof FormState>(key: K, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function validate(): ModuleInput | null {
    const next: Errors = {};
    if (!form.titleEn.trim()) {
      next.titleEn = t("staffCatalog.moduleForm.titleEnRequired");
    }
    if (!form.titleBn.trim()) {
      next.titleBn = t("staffCatalog.moduleForm.titleBnRequired");
    }
    if (!form.categoryId) {
      next.categoryId = t("staffCatalog.moduleForm.categoryRequired");
    }
    const price = parsePrice(form.price);
    if (!price.ok) {
      next.price = t("staffCatalog.moduleForm.priceInvalid");
    }
    setErrors(next);
    if (Object.keys(next).length > 0 || !price.ok) {
      return null;
    }
    const body: ModuleInput = {
      titleEn: form.titleEn.trim(),
      titleBn: form.titleBn.trim(),
      descriptionEn: form.descriptionEn.trim() || null,
      descriptionBn: form.descriptionBn.trim() || null,
      categoryId: form.categoryId,
    };
    if (canSetPrice) {
      body.price = price.ok ? price.value : null;
    }
    return body;
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    const body = validate();
    if (!body) {
      return;
    }
    try {
      const saved = await save.mutateAsync(body);
      if (creating) {
        toast({ title: t("staffCatalog.moduleForm.created"), tone: "ok" });
        router.replace(`/staff/modules/${saved.id}`);
        return;
      }
      setStatus(saved);
      const next = initialState(saved);
      setForm(next);
      setBaseline(next);
      toast({
        title: saved.status === "DRAFT" && module?.status === "PUBLISHED" ? t("staffCatalog.moduleForm.savedReturnedToDraft") : t("staffCatalog.moduleForm.saved"),
        tone: "ok",
      });
    } catch (error) {
      setFormError(t(catalogErrorKey(error)));
      setErrors({
        titleEn: fieldErrorFor(error, "titleEn") ?? undefined,
        titleBn: fieldErrorFor(error, "titleBn") ?? undefined,
        price: fieldErrorFor(error, "price") ?? undefined,
      });
    }
  }

  async function runAction(action: "final" | "publish") {
    setFormError(null);
    try {
      const result = action === "final" ? await finalSave.mutateAsync() : await publish.mutateAsync();
      if (result) {
        setStatus(result);
      }
      toast({
        title: action === "final" ? t("staffCatalog.moduleForm.finalSaved") : t("staffCatalog.moduleForm.published"),
        tone: "ok",
      });
    } catch (error) {
      setFormError(t(catalogErrorKey(error)));
    }
  }

  if (!module && !role.canAuthor) {
    return null;
  }

  return (
    <div className="grid gap-6">
      <form onSubmit={onSubmit} noValidate className="grid max-w-3xl gap-5">
        <fieldset disabled={readOnly} className="grid gap-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("staffCatalog.moduleForm.titleEn")} required error={errors.titleEn}>
              <TextInput value={form.titleEn} onChange={(e) => set("titleEn", e.target.value)} dir="ltr" />
            </Field>
            <Field label={t("staffCatalog.moduleForm.titleBn")} required error={errors.titleBn}>
              <TextInput value={form.titleBn} onChange={(e) => set("titleBn", e.target.value)} />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("staffCatalog.moduleForm.descriptionEn")} error={errors.descriptionEn}>
              <Textarea rows={4} value={form.descriptionEn} onChange={(e) => set("descriptionEn", e.target.value)} dir="ltr" />
            </Field>
            <Field label={t("staffCatalog.moduleForm.descriptionBn")} error={errors.descriptionBn}>
              <Textarea rows={4} value={form.descriptionBn} onChange={(e) => set("descriptionBn", e.target.value)} />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("staffCatalog.moduleForm.category")} required error={errors.categoryId}>
              <Select value={form.categoryId} onChange={(e) => set("categoryId", e.target.value)}>
                <option value="">{t("staffCatalog.moduleForm.categoryPick")}</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {`${"— ".repeat(c.depth)}${locale === "bn" ? c.titleBn : c.titleEn}`}
                  </option>
                ))}
              </Select>
            </Field>
            {canSetPrice ? (
              <Field
                label={t("staffCatalog.moduleForm.price")}
                hint={t("staffCatalog.moduleForm.priceHint")}
                error={errors.price}
              >
                <NumberInput inputMode="decimal" min={0} step="0.01" value={form.price} onChange={(e) => set("price", e.target.value)} className="max-w-48" />
              </Field>
            ) : (
              <div className="grid gap-1">
                <span className="text-label font-medium text-muted">{t("staffCatalog.moduleForm.price")}</span>
                <p className="text-body">{formatMoney(priceDisplay) ?? t("staffCatalog.moduleForm.noPrice")}</p>
              </div>
            )}
          </div>
          {!readOnly ? (
            <div className="flex flex-wrap items-center gap-3">
              <Button type="submit" loading={save.isPending} disabled={save.isPending}>
                {creating ? t("staffCatalog.moduleForm.create") : t("staffCatalog.moduleForm.save")}
              </Button>
              {dirty ? (
                <span role="status" className="text-meta text-warn">
                  {t("staffCatalog.moduleForm.dirty")}
                </span>
              ) : null}
              <p className="text-meta text-muted">{t("staffCatalog.moduleForm.returnsToDraftHelp")}</p>
            </div>
          ) : null}
        </fieldset>
        {formError ? (
          <p role="alert" className="text-meta text-danger">
            {formError}
          </p>
        ) : null}
      </form>

      {module && !readOnly && role.canAuthor ? (
        <section aria-labelledby="module-publish-heading" className="grid max-w-3xl gap-4 rounded-card border border-border bg-card p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="module-publish-heading" className="text-card font-semibold">
              {t("staffCatalog.moduleForm.publishHeading")}
            </h2>
            {status ? <StatusPill status={status.status} /> : null}
          </div>
          <ExamChecklist items={checklist} />
          <div className="flex flex-wrap gap-3">
            <span id="module-final-save">
              <Button
                variant="secondary"
                disabled={dirty || finalSave.isPending || Boolean(status?.finallySaved)}
                loading={finalSave.isPending}
                onClick={() => runAction("final")}
              >
                {t("staffCatalog.moduleForm.finalSave")}
              </Button>
            </span>
            <Button disabled={!publishable || publish.isPending} loading={publish.isPending} onClick={() => runAction("publish")}>
              {t("staffCatalog.moduleForm.publish")}
            </Button>
          </div>
        </section>
      ) : null}

      {module && role.canDelete && !archived ? (
        <section className="flex flex-wrap gap-3 border-t border-border pt-5">
          <Button variant="danger" onClick={() => setConfirm("archive")}>
            {t("staffCatalog.moduleForm.archive")}
          </Button>
          <Button variant="danger" onClick={() => setConfirm("delete")}>
            {t("staffCatalog.moduleForm.delete")}
          </Button>
        </section>
      ) : null}

      {module && role.canDelete ? (
        <>
          <ConfirmDialog
            open={confirm === "archive"}
            onOpenChange={(open) => setConfirm(open ? "archive" : null)}
            title={t("staffCatalog.moduleForm.archiveTitle", { name: module.titleBn })}
            body={t("staffCatalog.moduleForm.archiveBody")}
            confirmLabel={t("staffCatalog.moduleForm.archiveConfirm")}
            onConfirm={async () => {
              try {
                await archive.mutateAsync();
                setStatus((current) => (current ? { ...current, status: "ARCHIVED" } : current));
                toast({ title: t("staffCatalog.moduleForm.archived"), tone: "ok" });
              } catch (error) {
                throw new Error(t(catalogErrorKey(error)));
              }
            }}
          />
          <ConfirmDialog
            open={confirm === "delete"}
            onOpenChange={(open) => setConfirm(open ? "delete" : null)}
            title={t("staffCatalog.moduleForm.deleteTitle", { name: module.titleBn })}
            body={t("staffCatalog.moduleForm.deleteBody")}
            confirmLabel={t("staffCatalog.moduleForm.deleteConfirm")}
            tone="danger"
            typeToConfirm={module.titleBn}
            onConfirm={async () => {
              try {
                await remove.mutateAsync();
                router.replace("/staff/modules");
              } catch (error) {
                throw new Error(t(catalogErrorKey(error)));
              }
            }}
          />
        </>
      ) : null}
    </div>
  );
}

/** Create a module (no `module`) or edit its details. Both titles and descriptions are always visible. */
export function ModuleDetails({ module }: { module?: StaffModule }) {
  return <DetailsBody key={module?.id ?? "new"} module={module} />;
}
