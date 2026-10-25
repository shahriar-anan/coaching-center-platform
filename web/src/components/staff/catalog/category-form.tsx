"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import {
  useCategory,
  useCategoryTree,
  useSaveCategory,
  type CategoryInput,
  type CategoryTreeNode,
} from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey, fieldErrorFor } from "@/components/staff/catalog/errors";
import { useUnsavedWarning } from "@/components/staff/catalog/use-unsaved-warning";

type FormState = { titleEn: string; titleBn: string; parentId: string; position: string };

/** The category and every category under it. A category cannot be its own parent. */
function descendantIds(nodes: CategoryTreeNode[], id: string): Set<string> {
  const out = new Set<string>();
  const collect = (node: CategoryTreeNode) => {
    out.add(node.id);
    node.children.forEach(collect);
  };
  const find = (list: CategoryTreeNode[]) => {
    for (const node of list) {
      if (node.id === id) {
        collect(node);
        return true;
      }
      if (find(node.children)) {
        return true;
      }
    }
    return false;
  };
  find(nodes);
  return out;
}

function flatten(nodes: CategoryTreeNode[], depth = 0): { node: CategoryTreeNode; depth: number }[] {
  return nodes.flatMap((node) => [{ node, depth }, ...flatten(node.children, depth + 1)]);
}

function CategoryFormBody({ id, initial }: { id?: string; initial: FormState }) {
  const { t, locale } = useLocale();
  const router = useRouter();
  const { toast } = useToast();
  const tree = useCategoryTree();
  const save = useSaveCategory(id);
  const [form, setForm] = useState<FormState>(initial);
  const [baseline, setBaseline] = useState<FormState>(initial);
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  useUnsavedWarning(dirty);

  const options = useMemo(() => {
    const roots = tree.data ?? [];
    const excluded = id ? descendantIds(roots, id) : new Set<string>();
    return flatten(roots).filter(({ node }) => !excluded.has(node.id) && !node.archived);
  }, [tree.data, id]);

  function set<K extends keyof FormState>(key: K, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function validate(): CategoryInput | null {
    const next: Partial<Record<keyof FormState, string>> = {};
    if (!form.titleEn.trim()) {
      next.titleEn = t("staffCatalog.categoryForm.titleEnRequired");
    }
    if (!form.titleBn.trim()) {
      next.titleBn = t("staffCatalog.categoryForm.titleBnRequired");
    }
    const position = Number(form.position);
    if (form.position === "" || !Number.isInteger(position) || position < 0) {
      next.position = t("staffCatalog.categoryForm.positionInvalid");
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return null;
    }
    return {
      parentId: form.parentId || null,
      titleEn: form.titleEn.trim(),
      titleBn: form.titleBn.trim(),
      position,
    };
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
      toast({ title: t("staffCatalog.categoryForm.saved"), tone: "ok" });
      if (!id) {
        router.replace(`/staff/categories/${saved.id}`);
      } else {
        const next = {
          titleEn: saved.titleEn,
          titleBn: saved.titleBn,
          parentId: saved.parentId ?? "",
          position: String(saved.position),
        };
        setForm(next);
        setBaseline(next);
      }
    } catch (error) {
      const titleEn = fieldErrorFor(error, "titleEn");
      setFormError(titleEn ? null : t(catalogErrorKey(error)));
      setErrors({
        titleEn: titleEn ?? undefined,
        titleBn: fieldErrorFor(error, "titleBn") ?? undefined,
        position: fieldErrorFor(error, "position") ?? undefined,
      });
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="grid max-w-2xl gap-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("staffCatalog.categoryForm.titleEn")} required error={errors.titleEn}>
          <TextInput value={form.titleEn} onChange={(e) => set("titleEn", e.target.value)} dir="ltr" />
        </Field>
        <Field label={t("staffCatalog.categoryForm.titleBn")} required error={errors.titleBn}>
          <TextInput value={form.titleBn} onChange={(e) => set("titleBn", e.target.value)} />
        </Field>
      </div>
      <Field
        label={t("staffCatalog.categoryForm.parent")}
        hint={t("staffCatalog.categoryForm.parentHint")}
        error={errors.parentId}
      >
        <Select value={form.parentId} onChange={(e) => set("parentId", e.target.value)}>
          <option value="">{t("staffCatalog.categoryForm.parentNone")}</option>
          {options.map(({ node, depth }) => (
            <option key={node.id} value={node.id}>
              {`${"— ".repeat(depth)}${locale === "bn" ? node.titleBn : node.titleEn}`}
            </option>
          ))}
        </Select>
      </Field>
      <Field label={t("staffCatalog.categoryForm.position")} hint={t("staffCatalog.categoryForm.positionHint")} required error={errors.position}>
        <NumberInput inputMode="numeric" min={0} step={1} value={form.position} onChange={(e) => set("position", e.target.value)} className="max-w-32" />
      </Field>

      {formError ? (
        <p role="alert" className="text-sm text-danger">
          {formError}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" loading={save.isPending} disabled={save.isPending}>
          {t("staffCatalog.categoryForm.save")}
        </Button>
        {dirty ? (
          <span className="text-sm text-warn" role="status">
            {t("staffCatalog.categoryForm.dirty")}
          </span>
        ) : null}
      </div>
    </form>
  );
}

/** Create (no `id`) or edit one category. Both titles are always visible. */
export function CategoryForm({ id }: { id?: string }) {
  const { t } = useLocale();
  const category = useCategory(id);

  if (id && category.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (id && category.isError) {
    return <ErrorState onRetry={() => category.refetch()} message={t("staffCatalog.categoryForm.loadError")} />;
  }
  const initial: FormState = category.data
    ? {
        titleEn: category.data.titleEn,
        titleBn: category.data.titleBn,
        parentId: category.data.parentId ?? "",
        position: String(category.data.position),
      }
    : { titleEn: "", titleBn: "", parentId: "", position: "0" };

  return <CategoryFormBody key={category.data?.id ?? "new"} id={id} initial={initial} />;
}
