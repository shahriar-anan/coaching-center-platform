"use client";

import { FormEvent, useEffect, useState } from "react";
import { FilePicker, FilePreview, MaterialFileCard, OpenFileButton } from "@/components/material-files";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { CourseNodeView, CourseView, MaterialView } from "@/lib/api/types";
import { youtubeWatchUrl } from "@/lib/catalog/youtube";

export function SectionCard({
  section,
  number,
  childrenOf,
  materials,
  lectureFormFor,
  selectedId,
  isMaster,
  ends,
  onAddLecture,
  onAdd,
  onOpen,
  onMove,
  onDelete,
}: {
  section: CourseNodeView;
  number: number;
  childrenOf: (parentId: string) => CourseNodeView[];
  materials: MaterialView[];
  lectureFormFor: string | null;
  selectedId: string | null;
  isMaster: boolean;
  ends: (node: CourseNodeView) => { isFirst: boolean; isLast: boolean };
  onAddLecture: (sectionId: string) => void;
  onAdd: (e: FormEvent<HTMLFormElement>, parentId: string) => void;
  onOpen: (id: string) => void;
  onMove: (node: CourseNodeView, direction: -1 | 1) => void;
  onDelete: (node: CourseNodeView) => void;
}) {
  const { t } = useLocale();
  const children = childrenOf(section.id);
  const lectures = children.filter((node) => node.nodeType === "LECTURE");
  const subsections = children.filter((node) => node.nodeType === "SECTION");
  const formOpen = lectureFormFor === section.id;
  const boundary = ends(section);
  const heading = `${number}. ${section.title}`;

  return (
    <section className="rounded-lg border border-zinc-200 bg-white" aria-label={heading}>
      <header className="flex items-center justify-between gap-3 border-b border-zinc-200 px-4 py-3">
        <h2 className="text-lg font-semibold">{heading}</h2>
        <ItemMenu
          title={section.title}
          isMaster={isMaster}
          isFirst={boundary.isFirst}
          isLast={boundary.isLast}
          onMove={(direction) => onMove(section, direction)}
          onDelete={() => onDelete(section)}
        />
      </header>
      {lectures.length > 0 ? (
        <ul>
          {lectures.map((lecture, index) => (
            <LectureRow
              key={lecture.id}
              lecture={lecture}
              number={index + 1}
              selected={selectedId === lecture.id}
              fileCount={materials.filter((material) => material.lectureNodeId === lecture.id).length}
              isMaster={isMaster}
              ends={ends(lecture)}
              onOpen={onOpen}
              onMove={(direction) => onMove(lecture, direction)}
              onDelete={() => onDelete(lecture)}
            />
          ))}
        </ul>
      ) : null}
      <div className="border-t border-zinc-100 px-4 py-3">
        {formOpen ? (
          <form
            className="flex flex-wrap items-end gap-2"
            aria-label={`${t("courses.addLecture")} ${section.title}`}
            onSubmit={(e) => onAdd(e, section.id)}
          >
            <label className="block min-w-0 flex-1 text-sm">
              {t("courses.lectureTitle")}
              <input name="title" required className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2" />
            </label>
            <button type="submit" className="rounded-md bg-zinc-900 px-3 py-2 text-sm text-white">
              {t("courses.addLecture")}
            </button>
          </form>
        ) : (
          <button
            type="button"
            className="rounded-md border border-zinc-300 px-3 py-2 text-sm"
            aria-label={`${t("courses.addLecture")} ${section.title}`}
            onClick={() => onAddLecture(section.id)}
          >
            {t("courses.addLecture")}
          </button>
        )}
      </div>
      {subsections.length > 0 ? (
        <div className="space-y-3 border-t border-zinc-100 p-3">
          {subsections.map((child, index) => (
            <SectionCard
              key={child.id}
              section={child}
              number={index + 1}
              childrenOf={childrenOf}
              materials={materials}
              lectureFormFor={lectureFormFor}
              selectedId={selectedId}
              isMaster={isMaster}
              ends={ends}
              onAddLecture={onAddLecture}
              onAdd={onAdd}
              onOpen={onOpen}
              onMove={onMove}
              onDelete={onDelete}
            />
          ))}
        </div>
      ) : null}
    </section>
  );
}

export function LectureRow({
  lecture,
  number,
  selected,
  fileCount,
  isMaster,
  ends,
  onOpen,
  onMove,
  onDelete,
}: {
  lecture: CourseNodeView;
  number: number;
  selected: boolean;
  fileCount: number;
  isMaster: boolean;
  ends: { isFirst: boolean; isLast: boolean };
  onOpen: (id: string) => void;
  onMove: (direction: -1 | 1) => void;
  onDelete: () => void;
}) {
  const { t } = useLocale();
  const status = lectureStatus(lecture, fileCount, t);
  return (
    <li className={`flex items-center gap-2 border-b border-zinc-100 px-4 py-2 last:border-b-0 ${selected ? "bg-zinc-100" : ""}`}>
      <button
        type="button"
        aria-current={selected ? "true" : undefined}
        aria-label={`${t("courses.lecture")} ${lecture.title}`}
        className="min-w-0 flex-1 py-1 text-left"
        onClick={() => onOpen(lecture.id)}
      >
        <span className="block text-sm font-medium">
          {number}. {lecture.title}
        </span>
        {status ? <span className="block text-xs text-zinc-500">{status}</span> : null}
      </button>
      <ItemMenu
        title={lecture.title}
        isMaster={isMaster}
        isFirst={ends.isFirst}
        isLast={ends.isLast}
        onMove={onMove}
        onDelete={onDelete}
      />
    </li>
  );
}

function ItemMenu({
  title,
  isMaster,
  isFirst,
  isLast,
  onMove,
  onDelete,
}: {
  title: string;
  isMaster: boolean;
  isFirst: boolean;
  isLast: boolean;
  onMove: (direction: -1 | 1) => void;
  onDelete: () => void;
}) {
  const { t } = useLocale();
  return (
    <details className="relative">
      <summary
        role="button"
        aria-label={`${t("courses.actions")} ${title}`}
        className="cursor-pointer list-none rounded-md border border-zinc-300 px-2 py-1 text-xs"
      >
        {t("courses.actions")}
      </summary>
      <div className="absolute right-0 z-10 mt-1 flex w-36 flex-col rounded-md border border-zinc-200 bg-white p-1 shadow-sm">
        <button type="button" className="px-2 py-1 text-left text-sm disabled:text-zinc-300" disabled={isFirst} onClick={() => onMove(-1)}>
          {t("courses.moveUp")}
        </button>
        <button type="button" className="px-2 py-1 text-left text-sm disabled:text-zinc-300" disabled={isLast} onClick={() => onMove(1)}>
          {t("courses.moveDown")}
        </button>
        {isMaster ? (
          <button
            type="button"
            className="px-2 py-1 text-left text-sm text-red-700"
            aria-label={`${t("common.delete")} ${title}`}
            onClick={onDelete}
          >
            {t("common.delete")}
          </button>
        ) : null}
      </div>
    </details>
  );
}

export function LectureEditor({
  course,
  node,
  nodes,
  materials,
  isMaster,
  onSaved,
  onUpload,
  onReplace,
  onDeleteFile,
  onMarkFree,
  onError,
  onDone,
  onRegisterSave,
}: {
  course: CourseView;
  node: CourseNodeView;
  nodes: CourseNodeView[];
  materials: MaterialView[];
  isMaster: boolean;
  onSaved: () => Promise<void>;
  onUpload: (file: File) => Promise<void>;
  onReplace: (materialId: string, file: File) => Promise<void>;
  onDeleteFile: (material: MaterialView) => void;
  onMarkFree: (materialId: string) => void;
  onError: (error: unknown) => void;
  onDone: () => void;
  onRegisterSave: (save: (() => Promise<void>) | null) => void;
}) {
  const { t } = useLocale();
  const [title, setTitle] = useState(node.title);
  const [notes, setNotes] = useState(node.notes ?? "");
  const [videoUrl, setVideoUrl] = useState(youtubeWatchUrl(node.videoRef));
  const [sectionId, setSectionId] = useState(node.parentId ?? "");
  const [markFree, setMarkFree] = useState(false);
  const sections = nodes.filter((item) => item.nodeType === "SECTION");
  const watch = youtubeWatchUrl(node.videoRef);

  async function persist() {
    const body: Record<string, unknown> = {
      title,
      notes,
      videoUrl: videoUrl.trim() ? videoUrl.trim() : null,
    };
    const nextParent = sectionId === "" ? null : sectionId;
    if (nextParent !== (node.parentId ?? null)) {
      body.parentId = nextParent;
    }
    if (course.accessType === "FREEMIUM" && !node.free && markFree) {
      body.free = true;
    }
    try {
      await api.updateCourseNode(course.id, node.id, body);
      await onSaved();
      onDone();
    } catch (err) {
      onError(err);
      throw err;
    }
  }

  useEffect(() => {
    onRegisterSave(() => persist());
    return () => onRegisterSave(null);
  });

  async function save(e: FormEvent) {
    e.preventDefault();
    try {
      await persist();
    } catch {
      /* persist already reports the error */
    }
  }

  async function clearVideo() {
    try {
      await api.updateCourseNode(course.id, node.id, { videoUrl: null });
      setVideoUrl("");
      await onSaved();
    } catch (err) {
      onError(err);
    }
  }

  return (
    <section className="space-y-4 rounded-lg border border-zinc-200 bg-white p-4">
      <h2 className="text-lg font-semibold">
        {t("courses.lecture")}: {node.title}
      </h2>
      <form className="space-y-4" aria-label={t("courses.lecture")} onSubmit={(e) => void save(e)}>
        <label className="block text-sm">
          {t("courses.lectureTitle")}
          <input
            required
            className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </label>
        <label className="block text-sm">
          {t("courses.notes")}
          <textarea
            rows={4}
            className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />
        </label>
        <fieldset className="space-y-2 rounded-md border border-zinc-200 p-3">
          <label className="block text-sm">
            {t("courses.videoUrl")}
            <input
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={videoUrl}
              onChange={(e) => setVideoUrl(e.target.value)}
              placeholder="https://www.youtube.com/watch?v="
            />
          </label>
          {watch ? (
            <a href={watch} className="block text-sm text-zinc-700 underline" target="_blank" rel="noreferrer">
              {watch}
            </a>
          ) : null}
          <button type="button" className="text-sm underline" onClick={() => void clearVideo()}>
            {t("courses.clearVideo")}
          </button>
        </fieldset>
        <label className="block text-sm">
          {t("courses.section")}
          <select
            className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
            value={sectionId}
            onChange={(e) => setSectionId(e.target.value)}
          >
            <option value="">{t("courses.noSection")}</option>
            {sections.map((section) => (
              <option key={section.id} value={section.id}>
                {sectionLabel(section, nodes)}
              </option>
            ))}
          </select>
        </label>
        {course.accessType === "FREEMIUM" && !node.free ? (
          <div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" name="markFree" checked={markFree} onChange={(e) => setMarkFree(e.target.checked)} />
              {t("courses.markFree")}
            </label>
            <p className="mt-1 text-xs text-zinc-500">{t("courses.markFreeHint")}</p>
          </div>
        ) : null}
        {node.free ? <p className="text-sm text-green-700">{t("courses.free")}</p> : null}
        <FileList
          materials={materials}
          emptyLabel={t("courses.noLectureFiles")}
          course={course}
          isMaster={isMaster}
          onReplace={onReplace}
          onDelete={onDeleteFile}
          onMarkFree={onMarkFree}
        />
        <FilePicker
          label={t("courses.addFile")}
          accessibleName={`${t("courses.addFile")} ${node.title}`}
          onFile={(file) => void onUpload(file)}
        />
        <button type="submit" className="rounded-md bg-zinc-900 px-3 py-2 text-sm text-white">
          {t("common.save")}
        </button>
      </form>
    </section>
  );
}

export function LectureDetails({
  node,
  nodes,
  materials,
  onEdit,
}: {
  node: CourseNodeView;
  nodes: CourseNodeView[];
  materials: MaterialView[];
  onEdit?: () => void;
}) {
  const { t } = useLocale();
  const watch = youtubeWatchUrl(node.videoRef);
  const section = nodes.find((item) => item.id === node.parentId);

  return (
    <section className="space-y-4 rounded-lg border border-zinc-200 bg-white p-4" aria-label={t("courses.lectureDetails")}>
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">{t("courses.lectureDetails")}</h2>
        {onEdit ? (
          <button type="button" className="rounded-md border border-zinc-300 px-3 py-1.5 text-sm" onClick={onEdit}>
            {t("courses.edit")}
          </button>
        ) : null}
      </div>
      <dl className="space-y-3 text-sm">
        <div>
          <dt className="text-zinc-500">{t("courses.lectureTitle")}</dt>
          <dd className="mt-1">{node.title}</dd>
        </div>
        <div>
          <dt className="text-zinc-500">{t("courses.section")}</dt>
          <dd className="mt-1">{section ? sectionLabel(section, nodes) : t("courses.noSection")}</dd>
        </div>
        <div>
          <dt className="text-zinc-500">{t("courses.notes")}</dt>
          <dd className="mt-1 whitespace-pre-wrap">{node.notes?.trim() ? node.notes : t("courses.noNotes")}</dd>
        </div>
        <div>
          <dt className="text-zinc-500">{t("courses.videoUrl")}</dt>
          <dd className="mt-1">
            {watch ? (
              <a href={watch} className="text-zinc-700 underline" target="_blank" rel="noreferrer">
                {watch}
              </a>
            ) : (
              t("courses.noVideo")
            )}
          </dd>
        </div>
      </dl>
      {node.free ? <p className="text-sm text-green-700">{t("courses.free")}</p> : null}
      <div>
        <h3 className="text-sm font-medium">{t("courses.materials")}</h3>
        {materials.length === 0 ? (
          <p className="mt-1 text-sm text-zinc-600">{t("courses.noLectureFiles")}</p>
        ) : (
          <ul className="mt-2 space-y-2">
            {materials.map((material) => (
              <li key={material.id} className="flex items-center gap-3">
                <FilePreview fileId={material.id} name={material.originalFileName} contentType={material.contentType} />
                <div className="min-w-0">
                  <p className="truncate text-sm">
                    {material.originalFileName}
                    {material.free ? <span className="ml-2 text-green-700">{t("courses.free")}</span> : null}
                  </p>
                  <OpenFileButton material={material} className="mt-2" />
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

export function CourseFiles({
  course,
  materials,
  isMaster,
  onUpload,
  onReplace,
  onDelete,
  onMarkFree,
}: {
  course: CourseView;
  materials: MaterialView[];
  isMaster: boolean;
  onUpload: (file: File) => Promise<void>;
  onReplace: (materialId: string, file: File) => Promise<void>;
  onDelete: (material: MaterialView) => void;
  onMarkFree: (materialId: string) => void;
}) {
  const { t } = useLocale();
  return (
    <section className="space-y-3 rounded-lg border border-zinc-200 bg-white p-4" aria-label={t("courses.courseFiles")}>
      <h2 className="text-lg font-semibold">{t("courses.courseFiles")}</h2>
      <FileList
        materials={materials}
        emptyLabel={t("courses.noCourseFiles")}
        course={course}
        isMaster={isMaster}
        onReplace={onReplace}
        onDelete={onDelete}
        onMarkFree={onMarkFree}
      />
      <FilePicker
        label={t("courses.addFile")}
        accessibleName={t("courses.addCourseFile")}
        onFile={(file) => void onUpload(file)}
      />
    </section>
  );
}

function FileList({
  materials,
  emptyLabel,
  course,
  isMaster,
  onReplace,
  onDelete,
  onMarkFree,
}: {
  materials: MaterialView[];
  emptyLabel: string;
  course: CourseView;
  isMaster: boolean;
  onReplace: (materialId: string, file: File) => Promise<void>;
  onDelete: (material: MaterialView) => void;
  onMarkFree: (materialId: string) => void | Promise<void>;
}) {
  if (materials.length === 0) {
    return <p className="text-sm text-zinc-600">{emptyLabel}</p>;
  }
  return (
    <ul className="space-y-2">
      {materials.map((material) => (
        <MaterialFileCard
          key={material.id}
          material={material}
          course={course}
          isMaster={isMaster}
          onReplace={(materialId, file) => void onReplace(materialId, file)}
          onDelete={onDelete}
          onMarkFree={(materialId) => void onMarkFree(materialId)}
        />
      ))}
    </ul>
  );
}

function lectureStatus(
  lecture: CourseNodeView,
  fileCount: number,
  t: (key: string, params?: Record<string, string | number>) => string,
): string {
  const parts: string[] = [];
  if (lecture.videoRef) {
    parts.push(t("courses.hasVideo"));
  }
  if (fileCount === 1) {
    parts.push(t("courses.oneFile"));
  } else if (fileCount > 1) {
    parts.push(t("courses.manyFiles", { count: fileCount }));
  }
  if (lecture.free) {
    parts.push(t("courses.free"));
  }
  return parts.join(" · ");
}

function sectionLabel(section: CourseNodeView, nodes: CourseNodeView[]): string {
  const parent = nodes.find((node) => node.id === section.parentId);
  if (!parent) {
    return section.title;
  }
  return `${sectionLabel(parent, nodes)} / ${section.title}`;
}
