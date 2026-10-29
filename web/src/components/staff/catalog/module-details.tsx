"use client";

import { useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { CoverUploader } from "@/components/staff/catalog/cover-uploader";
import {
  primaryExamAction,
  useCategoryTree,
  useDeleteModule,
  useDiscardPendingChanges,
  useModuleAction,
  usePendingChanges,
  usePublishPendingChanges,
  useSaveCategory,
  useSaveModule,
  useSetPackagePopular,
  type CategoryTreeNode,
  type ModuleInput,
  type StaffModule,
} from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { ModuleStudentPreview } from "@/components/staff/catalog/module-student-preview";
import { DateTimeInput } from "@/components/ui/date-time-input";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { RichTextTopics } from "@/components/staff/catalog/rich-text-topics";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { catalogErrorKey, fieldErrorFor } from "@/components/staff/catalog/errors";
import { useUnsavedWarning } from "@/components/staff/catalog/use-unsaved-warning";
import { ExamChecklist, type ChecklistItem } from "@/components/staff/catalog/exam-checklist";
import { ChangeListDialog } from "@/components/staff/catalog/change-list-dialog";
import { PendingChangesBanner } from "@/components/staff/catalog/pending-changes-banner";
import { parseDiscount } from "@/components/staff/catalog/package-rows";
import { richTextValue } from "@/components/staff/catalog/exam-form-state";

type FormState = {
  titleEn: string;
  titleBn: string;
  descriptionEn: string;
  descriptionBn: string;
  categoryId: string;
  price: string;
  discountPercent: string;
  discountEndsAt: string | null;
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
    discountPercent: module?.discountPercent == null ? "" : String(module.discountPercent),
    discountEndsAt: module?.discountEndsAt ?? null,
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
  const saveCategory = useSaveCategory();
  const setPopular = useSetPackagePopular(module?.id ?? "");
  // The change list is read only when the server says something is staged.
  const changes = usePendingChanges("package", module?.pendingChanges ? module.id : undefined);
  const publishChanges = usePublishPendingChanges("package", module?.id ?? "");
  const discardChanges = useDiscardPendingChanges("package", module?.id ?? "");

  const [form, setForm] = useState<FormState>(() => initialState(module));
  const [baseline, setBaseline] = useState<FormState>(() => initialState(module));
  const [status, setStatus] = useState<StaffModule | undefined>(module);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<"archive" | "delete" | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const [discarding, setDiscarding] = useState(false);
  const [popularFailure, setPopularFailure] = useState<string | null>(null);
  const [addingCategory, setAddingCategory] = useState(false);
  const [newCategory, setNewCategory] = useState({ titleEn: "", titleBn: "", parentId: "" });
  const [newCategoryErrors, setNewCategoryErrors] = useState<{ titleEn?: string; titleBn?: string }>({});
  const [newCategoryError, setNewCategoryError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  // On the create page nothing is on the server yet, so the description's Save only confirms the text on the page; the
  // package is created, with that text, by "Create package".
  const [confirmed, setConfirmed] = useState({ en: "", bn: "" });
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  useUnsavedWarning(dirty);

  const archived = status?.status === "ARCHIVED";
  const readOnly = archived || (module ? !role.canAuthor : false);
  const creating = !module;
  const categories = useMemo(() => flatCategories(tree.data ?? []), [tree.data]);
  const selectedCategory = categories.find((c) => c.id === form.categoryId) ?? null;

  // Price, discount and the popular flag are the Master Admin's (the API answers a System Admin with 403).
  const canSetPrice = role.canEditPrice;
  const masterAdmin = role.canPublishExam;
  const priceDisplay = status?.price ?? null;

  // Publishing a package needs its final save, the same as an exam; the staged state decides the rest.
  const checklist: ChecklistItem[] = [
    {
      key: "finalSave",
      label: t("staffCatalog.checklist.moduleFinalSave"),
      done: Boolean(status?.finallySaved) && !dirty,
      anchor: "module-final-save",
    },
  ];
  const publishable = checklist.every((item) => item.done) && status?.status !== "PUBLISHED" && !archived;
  const primary = status ? primaryExamAction(status, { dirty, masterAdmin }) : "none";

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  /** Opens the new-category panel. A parent id makes the new category a subcategory of that one. */
  function openCategoryPanel(parentId: string) {
    setNewCategory({ titleEn: "", titleBn: "", parentId });
    setNewCategoryErrors({});
    setNewCategoryError(null);
    setAddingCategory(true);
  }

  function cancelCategory() {
    setAddingCategory(false);
    setNewCategory({ titleEn: "", titleBn: "", parentId: "" });
    setNewCategoryErrors({});
    setNewCategoryError(null);
  }

  /** Creates the category, then selects it here so the package details stay as they are. */
  async function createCategory() {
    const next: { titleEn?: string; titleBn?: string } = {};
    if (!newCategory.titleEn.trim()) {
      next.titleEn = t("staffCatalog.categoryForm.titleEnRequired");
    }
    if (!newCategory.titleBn.trim()) {
      next.titleBn = t("staffCatalog.categoryForm.titleBnRequired");
    }
    setNewCategoryErrors(next);
    if (next.titleEn || next.titleBn) {
      return;
    }
    setNewCategoryError(null);
    try {
      const created = await saveCategory.mutateAsync({
        parentId: newCategory.parentId || null,
        titleEn: newCategory.titleEn.trim(),
        titleBn: newCategory.titleBn.trim(),
      });
      set("categoryId", created.id);
      cancelCategory();
      toast({ title: t("staffCatalog.categoryForm.saved"), tone: "ok" });
    } catch (error) {
      setNewCategoryError(t(catalogErrorKey(error)));
    }
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
    const discount = parseDiscount(form.discountPercent);
    if (!discount.ok) {
      next.discountPercent = t("staffCatalog.moduleForm.discountInvalid");
    } else if (discount.value === null && form.discountEndsAt) {
      next.discountEndsAt = t("staffCatalog.moduleForm.discountEndNeedsDiscount");
    }
    // A discount end that is set or changed cannot be in the past; an unchanged one that has run out may stay.
    if (form.discountEndsAt && form.discountEndsAt !== baseline.discountEndsAt && Date.parse(form.discountEndsAt) < Date.now()) {
      next.discountEndsAt = t("staffCatalog.moduleForm.discountEndPast");
    }
    setErrors(next);
    if (Object.keys(next).length > 0 || !price.ok || !discount.ok) {
      return null;
    }
    const body: ModuleInput = {
      titleEn: form.titleEn.trim(),
      titleBn: form.titleBn.trim(),
      // Rich text: the editor's HTML is sent as it is; the server sanitizes it (R3).
      descriptionEn: form.descriptionEn.trim() || null,
      descriptionBn: form.descriptionBn.trim() || null,
      categoryId: form.categoryId,
    };
    if (canSetPrice) {
      body.price = price.value;
      body.discountPercent = discount.value;
      body.discountEndsAt = discount.value === null ? null : form.discountEndsAt;
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
        title: saved.status === "PUBLISHED" ? t("staffCatalog.moduleForm.savedStaged") : t("staffCatalog.moduleForm.saved"),
        tone: "ok",
      });
    } catch (error) {
      setFormError(t(catalogErrorKey(error)));
      setErrors({
        titleEn: fieldErrorFor(error, "titleEn") ?? undefined,
        titleBn: fieldErrorFor(error, "titleBn") ?? undefined,
        price: fieldErrorFor(error, "price") ?? undefined,
        discountPercent: fieldErrorFor(error, "discountPercent") ?? undefined,
        discountEndsAt: fieldErrorFor(error, "discountEndsAt") ?? undefined,
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

  async function togglePopular(next: boolean) {
    setPopularFailure(null);
    try {
      await setPopular.mutateAsync(next);
      setStatus((current) => (current ? { ...current, popular: next } : current));
    } catch (e) {
      setPopularFailure(t(catalogErrorKey(e)));
    }
  }

  if (!module && !role.canAuthor) {
    return null;
  }

  const banner = module?.pendingChanges ? <PendingChangesBanner masterAdmin={masterAdmin} /> : null;

  // The form's Save, at the foot of the third column.
  const saveRow = !readOnly ? (
    <div className="grid gap-2">
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" loading={save.isPending} disabled={save.isPending || (!creating && !dirty)}>
          {creating ? t("staffCatalog.moduleForm.create") : t("staffCatalog.moduleForm.save")}
        </Button>
        {dirty ? (
          <span role="status" className="text-meta text-warn">
            {t("staffCatalog.moduleForm.dirty")}
          </span>
        ) : null}
      </div>
      {status?.status === "PUBLISHED" ? <p className="text-meta text-muted">{t("staffCatalog.moduleForm.stagedHint")}</p> : null}
    </div>
  ) : null;

  return (
    <div className="grid gap-6">
      {banner}
      <form ref={formRef} id="module-details-form" onSubmit={onSubmit} noValidate className="grid max-w-5xl gap-5">
        <fieldset disabled={readOnly} className="grid gap-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("staffCatalog.moduleForm.titleEn")} required error={errors.titleEn}>
              <TextInput value={form.titleEn} onChange={(e) => set("titleEn", e.target.value)} dir="ltr" />
            </Field>
            <Field label={t("staffCatalog.moduleForm.titleBn")} required error={errors.titleBn}>
              <TextInput value={form.titleBn} onChange={(e) => set("titleBn", e.target.value)} />
            </Field>
          </div>
          <RichTextTopics
            saving={creating ? false : save.isPending}
            onSave={() => (creating ? setConfirmed({ en: form.descriptionEn, bn: form.descriptionBn }) : formRef.current?.requestSubmit())}
            previewNote={t("staffCatalog.richText.previewNoteDescription")}
            topics={[
              {
                title: t("staffCatalog.richText.descriptionTitle"),
                valueEn: form.descriptionEn,
                valueBn: form.descriptionBn,
                savedEn: creating ? confirmed.en : baseline.descriptionEn,
                savedBn: creating ? confirmed.bn : baseline.descriptionBn,
                errorEn: errors.descriptionEn,
                errorBn: errors.descriptionBn,
                onChange: (language, html) =>
                  language === "en"
                    ? set("descriptionEn", richTextValue(html, form.descriptionEn))
                    : set("descriptionBn", richTextValue(html, form.descriptionBn)),
              },
            ]}
          />
          <div className="grid items-start gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div className="grid gap-3">
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
              {!addingCategory ? (
                <div className="flex flex-wrap gap-3">
                  <Button type="button" variant="secondary" onClick={() => openCategoryPanel("")}>
                    {t("staffCatalog.categories.new")}
                  </Button>
                  {selectedCategory ? (
                    <Button type="button" variant="secondary" onClick={() => openCategoryPanel(selectedCategory.id)}>
                      {t("staffCatalog.moduleForm.addSubcategory", {
                        name: locale === "bn" ? selectedCategory.titleBn : selectedCategory.titleEn,
                      })}
                    </Button>
                  ) : null}
                </div>
              ) : (
                <div role="group" aria-label={t("staffCatalog.moduleForm.newCategoryHeading")} className="grid gap-4 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-4">
                  <h3 className="text-label font-semibold">{t("staffCatalog.moduleForm.newCategoryHeading")}</h3>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label={t("staffCatalog.categoryForm.titleEn")} required error={newCategoryErrors.titleEn}>
                      <TextInput
                        value={newCategory.titleEn}
                        onChange={(e) => setNewCategory((current) => ({ ...current, titleEn: e.target.value }))}
                        dir="ltr"
                      />
                    </Field>
                    <Field label={t("staffCatalog.categoryForm.titleBn")} required error={newCategoryErrors.titleBn}>
                      <TextInput
                        value={newCategory.titleBn}
                        onChange={(e) => setNewCategory((current) => ({ ...current, titleBn: e.target.value }))}
                      />
                    </Field>
                  </div>
                  <Field label={t("staffCatalog.categoryForm.parent")} hint={t("staffCatalog.categoryForm.parentHint")}>
                    <Select
                      value={newCategory.parentId}
                      onChange={(e) => setNewCategory((current) => ({ ...current, parentId: e.target.value }))}
                    >
                      <option value="">{t("staffCatalog.categoryForm.parentNone")}</option>
                      {categories.map((c) => (
                        <option key={c.id} value={c.id}>
                          {`${"— ".repeat(c.depth)}${locale === "bn" ? c.titleBn : c.titleEn}`}
                        </option>
                      ))}
                    </Select>
                  </Field>
                  {newCategoryError ? (
                    <p role="alert" className="text-meta text-danger">
                      {newCategoryError}
                    </p>
                  ) : null}
                  <div className="flex flex-wrap gap-3">
                    <Button
                      type="button"
                      loading={saveCategory.isPending}
                      disabled={saveCategory.isPending}
                      onClick={() => void createCategory()}
                    >
                      {t("staffCatalog.moduleForm.createCategory")}
                    </Button>
                    <Button type="button" variant="secondary" onClick={cancelCategory}>
                      {t("common.cancel")}
                    </Button>
                  </div>
                </div>
              )}
            </div>
            {/* Three columns from lg: the category, the price, and the discount with the Save button, so there is less to scroll. */}
            {canSetPrice ? (
              <>
                <Field label={t("staffCatalog.moduleForm.price")} hint={t("staffCatalog.moduleForm.priceHint")} error={errors.price}>
                  <NumberInput inputMode="decimal" min={0} step="0.01" value={form.price} onChange={(e) => set("price", e.target.value)} className="max-w-48" />
                </Field>
                <div className="grid gap-4">
                  <Field label={t("staffCatalog.moduleForm.discountPercent")} hint={t("staffCatalog.moduleForm.discountHint")} error={errors.discountPercent}>
                    <NumberInput inputMode="numeric" min={1} max={99} step={1} value={form.discountPercent} onChange={(e) => set("discountPercent", e.target.value)} className="max-w-32" />
                  </Field>
                  <Field label={t("staffCatalog.moduleForm.discountEndsAt")} error={errors.discountEndsAt}>
                    <DateTimeInput
                      value={form.discountEndsAt}
                      onChange={(iso) => set("discountEndsAt", iso)}
                      invalid={Boolean(errors.discountEndsAt)}
                      disabled={form.discountPercent.trim() === ""}
                      dateOnly
                      futureOnly
                    />
                  </Field>
                  {saveRow}
                </div>
              </>
            ) : (
              <>
                <div className="grid gap-1">
                  <span className="text-label font-medium text-muted">{t("staffCatalog.moduleForm.price")}</span>
                  <p className="text-body">{formatMoney(priceDisplay) ?? t("staffCatalog.moduleForm.noPrice")}</p>
                  <p className="text-meta text-muted">{t("staffCatalog.moduleForm.priceReadOnly")}</p>
                </div>
                <div className="grid gap-4">{saveRow}</div>
              </>
            )}
          </div>
        </fieldset>
        {formError ? (
          <p role="alert" className="text-meta text-danger">
            {formError}
          </p>
        ) : null}
      </form>

      {module && role.canPublishExam ? (
        <div className="grid max-w-3xl gap-2">
          <Switch
            id="module-popular"
            checked={status?.popular ?? module.popular ?? false}
            onCheckedChange={(value) => togglePopular(value)}
            disabled={setPopular.isPending || readOnly}
            label={t("staffCatalog.moduleForm.popular")}
          />
          <p className="text-meta text-muted">{t("staffCatalog.moduleForm.popularHint")}</p>
          {popularFailure ? (
            <p role="alert" className="text-meta text-danger">
              {popularFailure}
            </p>
          ) : null}
        </div>
      ) : null}

      {module ? (
        <Dialog
          open={previewing}
          onOpenChange={setPreviewing}
          title={t("staffCatalog.moduleForm.studentViewTitle")}
          description={t("staffCatalog.moduleForm.studentViewBody")}
          className="sm:max-w-4xl"
        >
          {previewing ? <ModuleStudentPreview module={module} /> : null}
        </Dialog>
      ) : null}

      {module && role.canManageCover ? <CoverUploader module={module} /> : null}

      {module && !readOnly && role.canAuthor ? (
        <section aria-labelledby="module-publish-heading" className="grid max-w-3xl gap-4 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="module-publish-heading" className="text-card-title font-semibold">
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
            <Button variant="secondary" onClick={() => setPreviewing(true)}>
              {t("staffCatalog.preview.open")}
            </Button>
            {/* A draft always shows Publish; it stays disabled until the final save is done (the server checks the same). */}
            {primary === "publish" || primary === "finalSave" ? (
              <Button disabled={!publishable || publish.isPending} loading={publish.isPending} onClick={() => runAction("publish")}>
                {t("staffCatalog.moduleForm.publish")}
              </Button>
            ) : null}
            {primary === "publishChanges" ? (
              <Button onClick={() => setReviewing(true)}>{t("staffCatalog.changes.publishChanges")}</Button>
            ) : null}
            {module.pendingChanges && masterAdmin ? (
              <Button variant="secondary" onClick={() => setDiscarding(true)}>
                {t("staffCatalog.changes.discardChanges")}
              </Button>
            ) : null}
          </div>
        </section>
      ) : null}

      {module && masterAdmin && changes.data ? (
        <ChangeListDialog
          open={reviewing}
          onOpenChange={setReviewing}
          changes={changes.data}
          onPublish={(revisionId) => publishChanges.mutateAsync(revisionId)}
          onFailed={(message) => setFormError(message)}
        />
      ) : null}

      {module && masterAdmin ? (
        <ConfirmDialog
          open={discarding}
          onOpenChange={setDiscarding}
          title={t("staffCatalog.changes.discardTitle")}
          body={t("staffCatalog.changes.discardBody")}
          confirmLabel={t("staffCatalog.changes.discard")}
          tone="danger"
          onConfirm={async () => {
            try {
              await discardChanges.mutateAsync();
              toast({ title: t("staffCatalog.changes.discarded"), tone: "ok" });
            } catch (e) {
              throw new Error(t(catalogErrorKey(e)));
            }
          }}
        />
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

/** Create a package (no `module`) or edit its details. Both titles and descriptions are always visible. */
export function ModuleDetails({ module }: { module?: StaffModule }) {
  return <DetailsBody key={module?.id ?? "new"} module={module} />;
}
