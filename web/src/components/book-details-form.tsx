"use client";

import { FormEvent, useEffect, useId, useRef, useState } from "react";
import { AuthorizedFile } from "@/components/authorized-file";
import { FilePicker } from "@/components/material-files";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import type { BookCategoryView, BookView } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

const fieldClass = "mt-1 w-full rounded-md border border-zinc-300 bg-white px-3 py-2";
const NEW_CATEGORY = "__new__";

export function BookDetailsForm({
  book,
  onSaved,
}: {
  book: BookView | null;
  onSaved: (book: BookView) => void;
}) {
  const { t } = useLocale();
  const user = useUser();
  const isMaster = user?.role === "MASTER_ADMIN";
  const creating = book === null;
  const titleEnId = useId();
  const titleBnId = useId();
  const authorId = useId();
  const descriptionId = useId();
  const priceId = useId();
  const availableId = useId();
  const categoryIdField = useId();
  const coverId = useId();
  const newEnId = useId();
  const newBnId = useId();

  const [titleEn, setTitleEn] = useState(book?.titleEn ?? "");
  const [titleBn, setTitleBn] = useState(book?.titleBn ?? "");
  const [author, setAuthor] = useState(book?.author ?? "");
  const [description, setDescription] = useState(book?.description ?? "");
  const [price, setPrice] = useState(book?.price != null ? String(book.price) : "");
  const [available, setAvailable] = useState(book?.available ?? false);
  const [categories, setCategories] = useState<BookCategoryView[]>([]);
  const [categoryId, setCategoryId] = useState(book?.categoryId ?? "");
  const [newTitleEn, setNewTitleEn] = useState("");
  const [newTitleBn, setNewTitleBn] = useState("");
  const [cover, setCover] = useState<File | null>(null);
  const [coverPreview, setCoverPreview] = useState<string | null>(null);
  const coverPreviewRef = useRef<string | null>(null);
  const [createdId, setCreatedId] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    return () => {
      if (coverPreviewRef.current) {
        URL.revokeObjectURL(coverPreviewRef.current);
      }
    };
  }, []);

  function chooseCover(file: File) {
    if (coverPreviewRef.current) {
      URL.revokeObjectURL(coverPreviewRef.current);
    }
    const next = URL.createObjectURL(file);
    coverPreviewRef.current = next;
    setCover(file);
    setCoverPreview(next);
  }

  useEffect(() => {
    let active = true;
    void api.listBookCategories().then((next) => {
      if (active) {
        setCategories(next);
      }
    }).catch((err) => {
      if (active) {
        setError(err);
      }
    });
    return () => {
      active = false;
    };
  }, []);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    const body: Record<string, unknown> = {
      titleEn: titleEn.trim(),
      titleBn: titleBn.trim(),
      author: author.trim(),
      description: description.trim(),
      available,
    };
    if (isMaster) {
      body.price = price.trim() ? Number(price) : null;
    }
    const current = book?.categoryId ?? "";
    try {
      if (categoryId === NEW_CATEGORY) {
        const created = await api.createBookCategory({
          titleEn: newTitleEn.trim(),
          titleBn: newTitleBn.trim(),
        });
        body.categoryId = created.id;
        setCategories((currentCategories) => [...currentCategories, created]);
        setCategoryId(created.id);
        setNewTitleEn("");
        setNewTitleBn("");
      } else if (categoryId !== current) {
        body.categoryId = categoryId ? categoryId : null;
      }
      let saved: BookView;
      const existingId = book?.id ?? createdId;
      if (!existingId) {
        const createBody: Record<string, unknown> = {
          titleEn: body.titleEn,
          titleBn: body.titleBn,
          author: body.author,
          description: body.description,
        };
        if (isMaster && price.trim()) {
          createBody.price = Number(price);
        }
        saved = await api.createBook(createBody);
        setCreatedId(saved.id);
        saved = await api.updateBook(saved.id, body);
      } else {
        saved = await api.updateBook(existingId, body);
      }
      if (cover) {
        saved = await api.uploadBookCover(saved.id, cover);
        setCover(null);
      }
      onSaved(saved);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  async function removeCategory() {
    if (!categoryId || categoryId === NEW_CATEGORY || !window.confirm(t("books.deleteCategoryConfirm"))) {
      return;
    }
    setError(null);
    try {
      await api.deleteBookCategory(categoryId);
      setCategories((current) => current.filter((item) => item.id !== categoryId));
      setCategoryId(book?.categoryId && book.categoryId !== categoryId ? book.categoryId : "");
    } catch (err) {
      if (err instanceof ApiError && err.code === "CATEGORY_NOT_EMPTY") {
        setError(new Error(t("books.categoryNotEmpty")));
        return;
      }
      setError(err);
    }
  }

  const selected = categories.find((item) => item.id === categoryId);

  return (
    <form className="space-y-4" onSubmit={(event) => void onSubmit(event)}>
      <FormError error={error} />
      <div className="grid items-start gap-8 lg:grid-cols-2">
        <div className="space-y-4 rounded-lg border border-zinc-200 bg-white p-5">
          <h2 className="text-lg font-semibold">{t("books.stepDetails")}</h2>
          <label className="block text-sm font-medium" htmlFor={titleEnId}>
            {t("books.titleEn")}
            <input id={titleEnId} className={fieldClass} value={titleEn} required onChange={(e) => setTitleEn(e.target.value)} />
          </label>
          <label className="block text-sm font-medium" htmlFor={titleBnId}>
            {t("books.titleBn")}
            <input id={titleBnId} className={fieldClass} value={titleBn} required onChange={(e) => setTitleBn(e.target.value)} />
          </label>
          <label className="block text-sm font-medium" htmlFor={authorId}>
            {t("books.author")}
            <input id={authorId} className={fieldClass} value={author} required onChange={(e) => setAuthor(e.target.value)} />
          </label>
          <label className="block text-sm font-medium" htmlFor={descriptionId}>
            {t("books.description")}
            <textarea
              id={descriptionId}
              className={fieldClass}
              rows={5}
              value={description}
              required
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
          <label className="flex items-center gap-2 text-sm font-medium" htmlFor={availableId}>
            <input id={availableId} type="checkbox" checked={available} onChange={(e) => setAvailable(e.target.checked)} />
            {t("books.available")}
          </label>
          {isMaster ? (
            <label className="block text-sm font-medium" htmlFor={priceId}>
              {t("books.price")}
              <input id={priceId} type="number" className={fieldClass} value={price} onChange={(e) => setPrice(e.target.value)} />
            </label>
          ) : null}
          <button
            type="submit"
            className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            disabled={submitting}
          >
            {creating ? t("common.create") : t("common.save")}
          </button>
        </div>
        <div className="space-y-4">
          <div className="rounded-lg border border-zinc-200 bg-white p-5">
            <label className="block text-sm font-medium" htmlFor={categoryIdField}>
              {t("books.category")}
              <select
                id={categoryIdField}
                className={fieldClass}
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
              >
                <option value="">—</option>
                {categories.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.titleEn} {item.titleBn}
                  </option>
                ))}
                <option value={NEW_CATEGORY}>{t("books.addCategory")}</option>
              </select>
            </label>
            {categoryId === NEW_CATEGORY ? (
              <div className="mt-4 space-y-3 rounded-md border border-dashed border-zinc-300 p-3" role="group" aria-label={t("books.addCategory")}>
                <label className="block text-sm font-medium" htmlFor={newEnId}>
                  {t("books.titleEn")}
                  <input id={newEnId} className={fieldClass} required value={newTitleEn} onChange={(e) => setNewTitleEn(e.target.value)} />
                </label>
                <label className="block text-sm font-medium" htmlFor={newBnId}>
                  {t("books.titleBn")}
                  <input id={newBnId} className={fieldClass} required value={newTitleBn} onChange={(e) => setNewTitleBn(e.target.value)} />
                </label>
              </div>
            ) : null}
            {isMaster && selected ? (
              <button
                type="button"
                className="mt-4 rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-800"
                onClick={() => void removeCategory()}
              >
                {t("books.deleteCategory")}
              </button>
            ) : null}
          </div>
          <div className="rounded-lg border border-zinc-200 bg-white p-5">
            <p className="text-sm font-medium">{t("books.cover")}</p>
            {coverPreview ? (
              <img src={coverPreview} alt={titleEn || t("books.cover")} className="mt-3 h-40 max-w-full rounded object-contain" />
            ) : book?.coverFileId ? (
              <AuthorizedFile fileId={book.coverFileId} alt={book.titleEn} className="mt-3 h-40 w-auto rounded object-contain" />
            ) : (
              <div className="mt-3 h-40 rounded-md border border-dashed border-zinc-300 bg-zinc-50" />
            )}
            <div className="mt-3">
              <FilePicker id={coverId} label={t("books.uploadCover")} accept="image/*" onFile={chooseCover} />
            </div>
            {cover ? <p className="mt-2 truncate text-sm text-zinc-600">{cover.name}</p> : null}
          </div>
        </div>
      </div>
    </form>
  );
}
