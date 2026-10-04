"use client";

import Link from "next/link";
import { FormEvent, ReactNode, useEffect, useId, useState } from "react";
import { AuthorizedFile } from "@/components/authorized-file";
import { FilePicker } from "@/components/material-files";
import { CoursePlacePath } from "@/components/course-place-path";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { AccessType, CategoryTreeNode, ContentLanguage, CourseView, DeliveryMode } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

const fieldClass = "mt-1 w-full rounded-md border border-zinc-300 px-3 py-2";
const NEW_CATEGORY = "__new__";

type LevelChoice = { id: string; titleEn: string; titleBn: string };

const emptyLevel = (): LevelChoice => ({ id: "", titleEn: "", titleBn: "" });

export function CourseDetailsForm({
  course,
  onSaved,
  aside,
}: {
  course: CourseView | null;
  onSaved: (course: CourseView) => void;
  aside?: ReactNode;
}) {
  const { t } = useLocale();
  const user = useUser();
  const isMaster = user?.role === "MASTER_ADMIN";
  const creating = course === null;
  const titleEnId = useId();
  const titleBnId = useId();
  const languageId = useId();
  const descriptionId = useId();
  const deliveryId = useId();
  const accessId = useId();
  const priceId = useId();
  const coverId = useId();
  const levelId = useId();

  const [titleEn, setTitleEn] = useState(course?.titleEn ?? "");
  const [titleBn, setTitleBn] = useState(course?.titleBn ?? "");
  const [contentLanguage, setContentLanguage] = useState<ContentLanguage>(course?.contentLanguage ?? "EN");
  const [description, setDescription] = useState(course?.description ?? "");
  const [deliveryMode, setDeliveryMode] = useState<DeliveryMode>(course?.deliveryMode ?? "ONLINE");
  const [accessType, setAccessType] = useState<AccessType>(course?.accessType ?? "FREEMIUM");
  const [price, setPrice] = useState(course?.price != null ? String(course.price) : "");
  const [cover, setCover] = useState<File | null>(null);
  const [tree, setTree] = useState<CategoryTreeNode[]>([]);
  const [categoriesReady, setCategoriesReady] = useState(false);
  const [levels, setLevels] = useState<LevelChoice[]>([emptyLevel()]);
  const [showPreview, setShowPreview] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [savedCourseId, setSavedCourseId] = useState<string | null>(course?.id ?? null);
  const [submitting, setSubmitting] = useState(false);

  const effectiveAccess = creating ? accessType : course.accessType;
  const showPrice = isMaster && effectiveAccess === "FREEMIUM";
  const chain = chosenChain(levels);
  const placementReady =
    chain.length > 0 &&
    chain.every((level) => level.id !== NEW_CATEGORY || (level.titleEn.trim() !== "" && level.titleBn.trim() !== ""));
  const previewCourseId = course?.id ?? "preview-course";
  const preview = showPreview
    ? buildPreview(tree, chain, previewCourseId, titleEn.trim() || t("courses.thisCourse"), titleBn.trim())
    : null;

  useEffect(() => {
    let active = true;
    void api
      .listCourseCategories()
      .then((next) => {
        if (!active) {
          return;
        }
        setTree(next);
        if (course) {
          const path = categoryPath(next, course.id);
          if (path && path.length > 0) {
            setLevels([...path.map((id) => ({ id, titleEn: "", titleBn: "" })), emptyLevel()]);
          }
        }
        setCategoriesReady(true);
      })
      .catch((err) => {
        if (active) {
          setError(err);
        }
      });
    return () => {
      active = false;
    };
  }, [course]);

  function chooseLevel(index: number, id: string) {
    setShowPreview(false);
    setLevels((current) => {
      const next = current.slice(0, index);
      next.push({ id, titleEn: "", titleBn: "" });
      if (id !== "") {
        next.push(emptyLevel());
      }
      return next;
    });
  }

  function setLevelTitle(index: number, field: "titleEn" | "titleBn", value: string) {
    setShowPreview(false);
    setLevels((current) => current.map((level, i) => (i === index ? { ...level, [field]: value } : level)));
  }

  async function removeCategory(id: string) {
    if (!window.confirm(t("courses.deleteCategoryConfirm"))) {
      return;
    }
    try {
      await api.deleteCourseCategory(id);
      const next = await api.listCourseCategories();
      setTree(next);
      setShowPreview(false);
      setLevels((current) => {
        const index = current.findIndex((level) => level.id === id);
        if (index < 0) {
          return current;
        }
        if (index === 0) {
          return [emptyLevel()];
        }
        return [...current.slice(0, index), emptyLevel()];
      });
      setError(null);
    } catch (err) {
      setError(err);
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    const body: Record<string, unknown> = {
      titleEn,
      titleBn,
      contentLanguage,
      description,
      deliveryMode,
    };
    if (creating) {
      body.accessType = accessType;
    }
    if (showPrice) {
      body.price = price.trim() ? Number(price) : null;
    }
    try {
      const placeId = categoriesReady ? await resolvePlaceId() : null;
      let saved: CourseView;
      if (course === null) {
        saved = await api.createCourse(body);
        if (placeId) {
          saved = await api.updateCourse(saved.id, { categoryId: placeId });
        }
      } else {
        if (categoriesReady && placeId !== (course.categoryId ?? null)) {
          body.categoryId = placeId;
        }
        saved = await api.updateCourse(course.id, body);
      }
      setSavedCourseId(saved.id);
      if (cover) {
        await api.uploadCourseCover(saved.id, cover);
      }
      onSaved(saved);
    } catch (err) {
      setError(err);
      setSubmitting(false);
    }
  }

  async function resolvePlaceId(): Promise<string | null> {
    let parentId: string | undefined;
    let placeId: string | null = null;
    for (const level of levels) {
      if (level.id === "") {
        break;
      }
      if (level.id === NEW_CATEGORY) {
        const created = await api.createCourseCategory({
          titleEn: level.titleEn.trim(),
          titleBn: level.titleBn.trim(),
          ...(parentId ? { parentId } : {}),
        });
        parentId = created.id;
        placeId = created.id;
      } else {
        parentId = level.id;
        placeId = level.id;
      }
    }
    return placeId;
  }

  return (
    <form className="grid items-start gap-8 lg:grid-cols-2" onSubmit={onSubmit}>
      <div className="space-y-4">
      <FormError error={error} />
      <label className="block text-sm font-medium" htmlFor={titleEnId}>
        {t("courses.titleEn")}
        <input
          id={titleEnId}
          required
          className={fieldClass}
          value={titleEn}
          onChange={(e) => setTitleEn(e.target.value)}
        />
      </label>
      <label className="block text-sm font-medium" htmlFor={titleBnId}>
        {t("courses.titleBn")}
        <input
          id={titleBnId}
          required
          className={fieldClass}
          value={titleBn}
          onChange={(e) => setTitleBn(e.target.value)}
        />
      </label>
      <label className="block text-sm font-medium" htmlFor={languageId}>
        {t("courses.contentLanguage")}
        <select
          id={languageId}
          className={fieldClass}
          value={contentLanguage}
          onChange={(e) => setContentLanguage(e.target.value as ContentLanguage)}
        >
          <option value="EN">{t("catalog.langEn")}</option>
          <option value="BN">{t("catalog.langBn")}</option>
        </select>
      </label>
      <label className="block text-sm font-medium" htmlFor={descriptionId}>
        {t("courses.description")}
        <textarea
          id={descriptionId}
          required
          rows={4}
          className={fieldClass}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </label>
      <label className="block text-sm font-medium" htmlFor={deliveryId}>
        {t("courses.deliveryMode")}
        <select
          id={deliveryId}
          className={fieldClass}
          value={deliveryMode}
          onChange={(e) => setDeliveryMode(e.target.value as DeliveryMode)}
        >
          <option value="ONLINE">{t("catalog.deliveryOnline")}</option>
          <option value="OFFLINE">{t("catalog.deliveryOffline")}</option>
          <option value="HYBRID">{t("catalog.deliveryHybrid")}</option>
        </select>
      </label>
      {creating ? (
        <label className="block text-sm font-medium" htmlFor={accessId}>
          {t("courses.accessType")}
          <select
            id={accessId}
            className={fieldClass}
            value={accessType}
            onChange={(e) => setAccessType(e.target.value as AccessType)}
          >
            <option value="FREEMIUM">{t("catalog.accessFreemium")}</option>
            <option value="FULL_FREE">{t("catalog.accessFullFree")}</option>
          </select>
        </label>
      ) : (
        <p className="text-sm">
          {t("courses.accessType")}:{" "}
          {course.accessType === "FULL_FREE" ? t("catalog.accessFullFree") : t("catalog.accessFreemium")}
        </p>
      )}
      {showPrice ? (
        <label className="block text-sm font-medium" htmlFor={priceId}>
          {t("courses.price")}
          <input
            id={priceId}
            type="number"
            min="0"
            className={fieldClass}
            value={price}
            onChange={(e) => setPrice(e.target.value)}
          />
        </label>
      ) : null}
      <button
        type="submit"
        disabled={submitting}
        className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
      >
        {t("courses.saveAndContinue")}
      </button>
      {creating && savedCourseId && error ? (
        <p className="text-sm">
          <Link href={`/courses/${savedCourseId}`} className="underline">
            {t("courses.detailTitle")}
          </Link>
        </p>
      ) : null}
      </div>

      <div className="space-y-4">
      <div className="space-y-3 rounded-md border border-zinc-200 bg-white p-4">
        {levels.map((level, index) => {
          const options = categoriesAt(tree, levels, index);
          const selected = options.find((node) => node.id === level.id) ?? null;
          const label =
            index === 0 ? t("courses.stepCategory") : t("courses.subcategoryLevel", { level: index });
          return (
            <div key={`${levelId}-${index}`} className="space-y-3">
              <div className="flex items-end gap-2">
                <label className="block flex-1 text-sm font-medium" htmlFor={`${levelId}-${index}`}>
                  {label}
                  <select
                    id={`${levelId}-${index}`}
                    className={fieldClass}
                    value={level.id}
                    onChange={(e) => chooseLevel(index, e.target.value)}
                  >
                    <option value="">{index === 0 ? "—" : t("courses.onThisCategory")}</option>
                    {options.map((node) => (
                      <option key={node.id} value={node.id}>
                        {node.titleEn} {node.titleBn}
                      </option>
                    ))}
                    <option value={NEW_CATEGORY}>
                      {index === 0 ? t("courses.addMainCategory") : t("courses.addSubcategory")}
                    </option>
                  </select>
                </label>
                {isMaster && selected ? (
                  <button
                    type="button"
                    className="rounded border px-3 py-2 text-sm"
                    aria-label={`${t("common.delete")} ${selected.titleEn}`}
                    onClick={() => void removeCategory(selected.id)}
                  >
                    {t("common.delete")}
                  </button>
                ) : null}
              </div>
              {level.id === NEW_CATEGORY ? (
                <fieldset
                  className="space-y-3"
                  aria-label={index === 0 ? t("courses.addMainCategory") : t("courses.addSubcategory")}
                >
                  <label className="block text-sm font-medium" htmlFor={`${levelId}-${index}-en`}>
                    {t("courses.titleEn")}
                    <input
                      id={`${levelId}-${index}-en`}
                      required
                      className={fieldClass}
                      value={level.titleEn}
                      onChange={(e) => setLevelTitle(index, "titleEn", e.target.value)}
                    />
                  </label>
                  <label className="block text-sm font-medium" htmlFor={`${levelId}-${index}-bn`}>
                    {t("courses.titleBn")}
                    <input
                      id={`${levelId}-${index}-bn`}
                      required
                      className={fieldClass}
                      value={level.titleBn}
                      onChange={(e) => setLevelTitle(index, "titleBn", e.target.value)}
                    />
                  </label>
                </fieldset>
              ) : null}
            </div>
          );
        })}
        {tree.length === 0 && categoriesReady ? (
          <p className="text-sm text-zinc-600">{t("courses.categoryEmpty")}</p>
        ) : null}
        {chain.length > 0 ? (
          <button
            type="button"
            disabled={!placementReady}
            className="rounded-md border border-zinc-900 px-4 py-2 text-sm font-medium disabled:opacity-50"
            onClick={() => setShowPreview(true)}
          >
            {t("courses.done")}
          </button>
        ) : null}
        {preview ? (
          <div className="space-y-3 border-t border-zinc-200 pt-3">
            <p className="text-sm text-zinc-700">
              {preview.path.length > 0
                ? t("courses.placedIn", { path: preview.path.join(" / ") })
                : t("courses.notPlaced")}
            </p>
            {preview.nodes.length === 0 ? (
              <p className="text-sm text-zinc-600">{t("courses.categoryEmpty")}</p>
            ) : (
              <CoursePlacePath nodes={preview.nodes} courseId={previewCourseId} />
            )}
          </div>
        ) : null}
      </div>

      <div className="rounded-md border border-zinc-200 bg-white p-4">
        <p className="text-sm font-medium">{t("courses.cover")}</p>
        {course?.coverFileId ? (
          <AuthorizedFile
            fileId={course.coverFileId}
            alt={course.titleEn}
            className="mt-2 h-40 w-auto rounded object-contain"
          />
        ) : null}
        <div className="mt-3">
          <FilePicker
            id={coverId}
            label={t("courses.uploadCover")}
            accept="image/*"
            onFile={setCover}
          />
        </div>
      </div>
      {aside}
      </div>
    </form>
  );
}

function chosenChain(levels: LevelChoice[]): LevelChoice[] {
  const chain: LevelChoice[] = [];
  for (const level of levels) {
    if (level.id === "") {
      break;
    }
    chain.push(level);
  }
  return chain;
}

function categoriesAt(tree: CategoryTreeNode[], levels: LevelChoice[], index: number): CategoryTreeNode[] {
  let nodes = tree.filter((node) => node.kind === "CATEGORY");
  for (let i = 0; i < index; i++) {
    const choice = levels[i];
    if (!choice || choice.id === "" || choice.id === NEW_CATEGORY) {
      return [];
    }
    const found = nodes.find((node) => node.id === choice.id);
    if (!found) {
      return [];
    }
    nodes = (found.children ?? []).filter((node) => node.kind === "CATEGORY");
  }
  return [...nodes].sort((a, b) => a.position - b.position);
}

function categoryPath(nodes: CategoryTreeNode[], courseId: string, trail: string[] = []): string[] | null {
  for (const node of nodes) {
    if (node.kind === "COURSE" && node.id === courseId) {
      return trail;
    }
    if (node.kind === "CATEGORY" && node.children?.length) {
      const found = categoryPath(node.children, courseId, [...trail, node.id]);
      if (found) {
        return found;
      }
    }
  }
  return null;
}

function withoutCourse(nodes: CategoryTreeNode[], courseId: string): CategoryTreeNode[] {
  return nodes
    .filter((node) => !(node.kind === "COURSE" && node.id === courseId))
    .map((node) => ({
      ...node,
      children: node.children ? withoutCourse(node.children, courseId) : node.children,
    }));
}

function buildPreview(
  tree: CategoryTreeNode[],
  chain: LevelChoice[],
  courseId: string,
  courseTitleEn: string,
  courseTitleBn: string,
): { nodes: CategoryTreeNode[]; path: string[] } {
  const currentPath = categoryPath(tree, courseId) ?? [];
  const samePlace =
    chain.every((level) => level.id !== NEW_CATEGORY) &&
    currentPath.length === chain.length &&
    currentPath.every((id, index) => id === chain[index]?.id);
  if (samePlace) {
    return { nodes: tree, path: pathTitles(tree, chain) };
  }
  const nodes = withoutCourse(tree, courseId);
  let parentList = nodes;
  const path: string[] = [];
  chain.forEach((level, index) => {
    if (level.id === NEW_CATEGORY) {
      const created: CategoryTreeNode = {
        kind: "CATEGORY",
        id: `new-${index}`,
        parentId: null,
        titleEn: level.titleEn.trim(),
        titleBn: level.titleBn.trim(),
        position: parentList.length,
        children: [],
      };
      parentList.push(created);
      path.push(created.titleEn);
      parentList = created.children ?? [];
      return;
    }
    const existing = parentList.find((node) => node.kind === "CATEGORY" && node.id === level.id);
    if (!existing) {
      return;
    }
    path.push(existing.titleEn);
    if (!existing.children) {
      existing.children = [];
    }
    parentList = existing.children;
  });
  parentList.push({
    kind: "COURSE",
    id: courseId,
    parentId: null,
    titleEn: courseTitleEn,
    titleBn: courseTitleBn,
    position: parentList.length,
    children: null,
  });
  return { nodes, path };
}

function pathTitles(nodes: CategoryTreeNode[], chain: LevelChoice[]): string[] {
  const titles: string[] = [];
  let level = nodes;
  for (const choice of chain) {
    const found = level.find((node) => node.kind === "CATEGORY" && node.id === choice.id);
    if (!found) {
      break;
    }
    titles.push(found.titleEn);
    level = found.children ?? [];
  }
  return titles;
}
