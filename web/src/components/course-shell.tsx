"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { AuthorizedFile } from "@/components/authorized-file";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { CourseView } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

export type CourseStep = "view" | "details" | "content";

export function CourseShell({
  course,
  step,
  onUpdated,
  onError,
  children,
}: {
  course: CourseView | null;
  step: CourseStep;
  onUpdated?: (course: CourseView) => void;
  onError?: (error: unknown) => void;
  children: ReactNode;
}) {
  const { t } = useLocale();
  const router = useRouter();
  const user = useUser();
  const isMaster = user?.role === "MASTER_ADMIN";
  const courseId = course?.id;

  async function publish() {
    if (!courseId) {
      return;
    }
    try {
      const updated = await api.updateCourse(courseId, { status: "PUBLISHED" });
      onUpdated?.(updated);
    } catch (err) {
      onError?.(err);
    }
  }

  async function archive() {
    if (!courseId || !window.confirm(t("courses.archiveConfirm"))) {
      return;
    }
    try {
      await api.archiveCourse(courseId);
      onUpdated?.(await api.getCourse(courseId));
    } catch (err) {
      onError?.(err);
    }
  }

  async function remove() {
    if (!courseId || !window.confirm(t("courses.deleteConfirm"))) {
      return;
    }
    try {
      await api.deleteCourse(courseId);
      router.push("/courses");
    } catch (err) {
      onError?.(err);
    }
  }

  async function close() {
    if (!courseId) {
      return;
    }
    try {
      onUpdated?.(await api.closeCourse(courseId));
    } catch (err) {
      onError?.(err);
    }
  }

  async function reopen() {
    if (!courseId) {
      return;
    }
    try {
      onUpdated?.(await api.reopenCourse(courseId));
    } catch (err) {
      onError?.(err);
    }
  }

  const statusLabel = !course
    ? null
    : course.status === "PUBLISHED"
      ? t("courses.published")
      : course.status === "ARCHIVED"
        ? t("courses.archived")
        : t("courses.draft");

  return (
    <div className="space-y-6">
      <header className="space-y-4 rounded-lg border border-zinc-200 bg-white p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <Link href="/courses" className="text-sm text-zinc-600 hover:text-zinc-900">
              {t("courses.title")}
            </Link>
            <nav aria-label={t("courses.steps")} className="flex flex-wrap gap-2 text-sm">
              {courseId ? (
                <StepLink href={`/courses/${courseId}`} current={step === "view"}>
                  {t("courses.viewCourse")}
                </StepLink>
              ) : null}
              <StepLink
                href={courseId ? `/courses/${courseId}/details` : "/courses/new"}
                current={step === "details"}
              >
                {t("courses.stepDetails")}
              </StepLink>
              <StepLink
                href={courseId ? `/courses/${courseId}/content` : undefined}
                current={step === "content"}
              >
                {t("courses.stepContent")}
              </StepLink>
            </nav>
          </div>
          {course ? (
            <div className="flex flex-wrap gap-2">
              {course.status !== "PUBLISHED" ? (
                <button
                  type="button"
                  className="rounded-md bg-zinc-900 px-3 py-1.5 text-sm text-white"
                  onClick={() => void publish()}
                >
                  {t("courses.publish")}
                </button>
              ) : null}
              {isMaster && course.status === "PUBLISHED" && !course.closed ? (
                <button type="button" className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm" onClick={() => void close()}>
                  {t("courses.close")}
                </button>
              ) : null}
              {isMaster && course.status === "PUBLISHED" && course.closed ? (
                <button type="button" className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm" onClick={() => void reopen()}>
                  {t("courses.reopen")}
                </button>
              ) : null}
              {isMaster ? (
                <button type="button" className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm" onClick={() => void archive()}>
                  {t("courses.archive")}
                </button>
              ) : null}
              {isMaster ? (
                <button
                  type="button"
                  className="rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-800"
                  onClick={() => void remove()}
                >
                  {t("common.delete")}
                </button>
              ) : null}
            </div>
          ) : null}
        </div>
        {step === "view" ? null : (
          <div className="flex items-center gap-4">
            {course?.coverFileId ? (
              <AuthorizedFile
                fileId={course.coverFileId}
                alt={course.titleEn}
                className="h-14 w-14 rounded object-cover"
              />
            ) : null}
            <div>
              <h1 className="text-2xl font-semibold">
                {course ? course.titleEn : t("courses.createTitle")}
              </h1>
              {course ? <p className="text-zinc-600">{course.titleBn}</p> : null}
              {course ? (
                <div className="mt-2 flex flex-wrap gap-2">
                  <StatusChip>{statusLabel}</StatusChip>
                  <StatusChip>{course.closed ? t("courses.closed") : t("courses.open")}</StatusChip>
                  <StatusChip>
                    {t("courses.opening")}: {course.openingNumber}
                  </StatusChip>
                </div>
              ) : null}
            </div>
          </div>
        )}
      </header>
      {children}
    </div>
  );
}

function StatusChip({ children }: { children: ReactNode }) {
  return <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-700">{children}</span>;
}

function StepLink({
  href,
  current,
  children,
}: {
  href?: string;
  current: boolean;
  children: ReactNode;
}) {
  const className = current
    ? "rounded-md bg-zinc-900 px-3 py-1.5 font-medium text-white"
    : "rounded-md border border-zinc-300 px-3 py-1.5 text-zinc-700 hover:bg-zinc-50";
  if (!href) {
    return <span className="rounded-md border border-dashed border-zinc-200 px-3 py-1.5 text-zinc-400">{children}</span>;
  }
  return (
    <Link href={href} className={className} aria-current={current ? "page" : undefined}>
      {children}
    </Link>
  );
}
