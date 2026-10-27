"use client";

import { useState } from "react";
import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";
import { useCategoryTree, useSaveCategory, type CategoryTreeNode } from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey, fieldErrorFor } from "@/components/staff/catalog/errors";

type FormState = { titleEn: string; titleBn: string };

function findNode(nodes: CategoryTreeNode[], id: string): CategoryTreeNode | null {
  for (const node of nodes) {
    if (node.id === id) {
      return node;
    }
    const found = findNode(node.children, id);
    if (found) {
      return found;
    }
  }
  return null;
}

/** The subcategories under one category, and a form to add another one under it. */
export function SubcategorySection({ parentId }: { parentId: string }) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const tree = useCategoryTree();
  const save = useSaveCategory();
  const [form, setForm] = useState<FormState>({ titleEn: "", titleBn: "" });
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);

  if (tree.isPending) {
    return <Skeleton variant="card" />;
  }
  if (tree.isError) {
    return <ErrorState onRetry={() => tree.refetch()} message={t("staffCatalog.categories.loadError")} />;
  }

  const parent = findNode(tree.data ?? [], parentId);
  const children = parent?.children ?? [];

  function set<K extends keyof FormState>(key: K, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    const next: Partial<Record<keyof FormState, string>> = {};
    if (!form.titleEn.trim()) {
      next.titleEn = t("staffCatalog.categoryForm.titleEnRequired");
    }
    if (!form.titleBn.trim()) {
      next.titleBn = t("staffCatalog.categoryForm.titleBnRequired");
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return;
    }
    try {
      await save.mutateAsync({ parentId, titleEn: form.titleEn.trim(), titleBn: form.titleBn.trim() });
      toast({ title: t("staffCatalog.subcategories.added"), tone: "ok" });
      setForm({ titleEn: "", titleBn: "" });
    } catch (error) {
      const titleEn = fieldErrorFor(error, "titleEn");
      setFormError(titleEn ? null : t(catalogErrorKey(error)));
      setErrors({
        titleEn: titleEn ?? undefined,
        titleBn: fieldErrorFor(error, "titleBn") ?? undefined,
      });
    }
  }

  return (
    <section aria-labelledby="subcategories-heading" className="mt-10 grid max-w-3xl gap-5">
      <h2 id="subcategories-heading" className="text-card-title font-semibold text-text">
        {t("staffCatalog.subcategories.title")}
      </h2>

      {children.length === 0 ? (
        <p className="text-meta text-muted">{t("staffCatalog.subcategories.empty")}</p>
      ) : (
        <ul className="grid gap-2">
          {children.map((node) => (
            <li key={node.id}>
              <Link href={`/staff/categories/${node.id}`} className="font-medium text-text hover:underline">
                {locale === "bn" ? node.titleBn : node.titleEn}
              </Link>
            </li>
          ))}
        </ul>
      )}

      {parent?.archived ? (
        <p role="status" className="text-meta text-warn">
          {t("staffCatalog.errors.CATEGORY_ARCHIVED")}
        </p>
      ) : (
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("staffCatalog.categoryForm.titleEn")} required error={errors.titleEn}>
              <TextInput value={form.titleEn} onChange={(e) => set("titleEn", e.target.value)} dir="ltr" />
            </Field>
            <Field label={t("staffCatalog.categoryForm.titleBn")} required error={errors.titleBn}>
              <TextInput value={form.titleBn} onChange={(e) => set("titleBn", e.target.value)} />
            </Field>
          </div>

          {formError ? (
            <p role="alert" className="text-meta text-danger">
              {formError}
            </p>
          ) : null}

          <div>
            <Button type="submit" loading={save.isPending} disabled={save.isPending}>
              {t("staffCatalog.subcategories.add")}
            </Button>
          </div>
        </form>
      )}
    </section>
  );
}
