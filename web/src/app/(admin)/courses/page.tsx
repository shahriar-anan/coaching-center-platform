"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { CourseView } from "@/lib/api/types";

export default function CoursesPage() {
  const { t } = useLocale();
  const [rows, setRows] = useState<CourseView[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.listCourses(page);
      setRows(data.content);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- client-side list fetch
    void load();
  }, [load]);

  return (
    <div>
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold">{t("courses.title")}</h1>
        <Link
          href="/courses/new"
          className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-800"
        >
          {t("courses.create")}
        </Link>
      </div>
      <FormError error={error} />
      {loading ? (
        <p className="mt-8 text-zinc-600">{t("common.loading")}</p>
      ) : rows.length === 0 ? (
        <p className="mt-8 text-zinc-600">{t("courses.empty")}</p>
      ) : (
        <table className="mt-6 w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-zinc-200 text-left">
              <th className="px-2 py-2">{t("courses.titleEn")}</th>
              <th className="px-2 py-2">{t("courses.status")}</th>
              <th className="px-2 py-2">{t("courses.accessType")}</th>
              <th className="px-2 py-2">{t("courses.deliveryMode")}</th>
              <th className="px-2 py-2" />
            </tr>
          </thead>
          <tbody>
            {rows.map((course) => (
              <tr key={course.id} className="border-b border-zinc-100">
                <td className="px-2 py-2">
                  <Link href={`/courses/${course.id}`} className="font-medium text-zinc-900 hover:underline">
                    {course.titleEn}
                  </Link>
                  <div className="text-zinc-500">{course.titleBn}</div>
                </td>
                <td className="px-2 py-2">
                  {course.status}
                  {course.closed ? ` · ${t("courses.closed")}` : ""}
                </td>
                <td className="px-2 py-2">{course.accessType}</td>
                <td className="px-2 py-2">{course.deliveryMode}</td>
                <td className="px-2 py-2 text-right">
                  <Link
                    href={`/courses/${course.id}`}
                    className="inline-block rounded-md bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-zinc-800"
                  >
                    {t("courses.viewCourse")}
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {totalPages > 1 ? (
        <div className="mt-6 flex gap-2">
          <button
            type="button"
            disabled={page === 0}
            className="rounded border border-zinc-300 px-3 py-1 disabled:opacity-40"
            onClick={() => setPage((p) => p - 1)}
          >
            {t("common.previous")}
          </button>
          <span className="px-2 py-1 text-sm text-zinc-600">
            {t("common.page", { page: page + 1, total: totalPages })}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            className="rounded border border-zinc-300 px-3 py-1 disabled:opacity-40"
            onClick={() => setPage((p) => p + 1)}
          >
            {t("common.next")}
          </button>
        </div>
      ) : null}
    </div>
  );
}
