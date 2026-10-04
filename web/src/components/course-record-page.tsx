"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { CourseDetailsForm } from "@/components/course-details-form";
import { CourseShell } from "@/components/course-shell";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { AccountView, CourseView } from "@/lib/api/types";

export function CourseRecordPage({ courseId }: { courseId: string }) {
  const { t } = useLocale();
  const router = useRouter();
  const [course, setCourse] = useState<CourseView | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [students, setStudents] = useState<AccountView[]>([]);
  const [admitStudentId, setAdmitStudentId] = useState("");
  const [admitDelivery, setAdmitDelivery] = useState<"ONLINE" | "OFFLINE">("ONLINE");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCourse(await api.getCourse(courseId));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [courseId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- client-side fetch
    void load();
  }, [load]);

  const showAdmission =
    course?.accessType === "FREEMIUM" && course.status === "PUBLISHED" && !course.closed;

  useEffect(() => {
    if (!showAdmission) {
      return;
    }
    void api.listStudents("", 0, 100).then((page) => setStudents(page.content));
  }, [showAdmission]);

  async function admit() {
    if (!admitStudentId) {
      return;
    }
    const body: { studentId: string; deliveryMode?: string } = { studentId: admitStudentId };
    if (course?.deliveryMode === "HYBRID") {
      body.deliveryMode = admitDelivery;
    }
    try {
      await api.admitStudent(courseId, body);
      setAdmitStudentId("");
      setError(null);
    } catch (err) {
      setError(err);
    }
  }

  if (loading) {
    return <p className="text-zinc-600">{t("common.loading")}</p>;
  }

  if (!course) {
    return <FormError error={error} />;
  }

  return (
    <CourseShell course={course} step="details" onUpdated={setCourse} onError={setError}>
      <FormError error={error} />
      <CourseDetailsForm
        key={`${course.id}-${course.coverFileId ?? ""}-${course.price ?? ""}-${course.status}`}
        course={course}
        onSaved={(saved) => {
          setCourse(saved);
          router.push(`/courses/${saved.id}`);
        }}
        aside={
          showAdmission ? (
        <section className="rounded-lg border border-zinc-200 bg-white p-4">
          <h2 className="text-lg font-semibold">{t("courses.admission")}</h2>
          <div className="mt-4 flex flex-wrap items-end gap-3">
            <label className="text-sm">
              {t("courses.selectStudent")}
              <select
                className="mt-1 block rounded border px-3 py-2"
                value={admitStudentId}
                onChange={(e) => setAdmitStudentId(e.target.value)}
              >
                <option value="">—</option>
                {students.map((student) => (
                  <option key={student.id} value={student.id}>
                    {student.fullName} ({student.phone})
                  </option>
                ))}
              </select>
            </label>
            {course.deliveryMode === "HYBRID" ? (
              <label className="text-sm">
                {t("courses.hybridDelivery")}
                <select
                  className="mt-1 block rounded border px-3 py-2"
                  value={admitDelivery}
                  onChange={(e) => setAdmitDelivery(e.target.value as "ONLINE" | "OFFLINE")}
                >
                  <option value="ONLINE">{t("catalog.deliveryOnline")}</option>
                  <option value="OFFLINE">{t("catalog.deliveryOffline")}</option>
                </select>
              </label>
            ) : null}
            <button type="button" className="rounded bg-zinc-900 px-4 py-2 text-sm text-white" onClick={() => void admit()}>
              {t("courses.admit")}
            </button>
          </div>
        </section>
          ) : null
        }
      />
    </CourseShell>
  );
}
