"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { CourseFiles, LectureDetails, LectureEditor, LectureRow, SectionCard } from "@/components/course-content-parts";
import { CourseShell } from "@/components/course-shell";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { CourseNodeView, CourseView, MaterialView } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

export function CourseContentPage({ courseId }: { courseId: string }) {
  const { t } = useLocale();
  const router = useRouter();
  const user = useUser();
  const saveLecture = useRef<(() => Promise<void>) | null>(null);
  const isMaster = user?.role === "MASTER_ADMIN";
  const [course, setCourse] = useState<CourseView | null>(null);
  const [nodes, setNodes] = useState<CourseNodeView[]>([]);
  const [materials, setMaterials] = useState<MaterialView[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [addingSection, setAddingSection] = useState(false);
  const [lectureFormFor, setLectureFormFor] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextCourse, nextNodes, nextMaterials] = await Promise.all([
        api.getCourse(courseId),
        api.listCourseNodes(courseId),
        api.listCourseMaterials(courseId),
      ]);
      setCourse(nextCourse);
      setNodes(nextNodes);
      setMaterials(nextMaterials);
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

  const roots = useMemo(
    () => nodes.filter((node) => node.parentId === null).sort((a, b) => a.position - b.position),
    [nodes],
  );
  const rootSections = roots.filter((node) => node.nodeType === "SECTION");
  const rootLectures = roots.filter((node) => node.nodeType === "LECTURE");
  const selected = nodes.find((node) => node.id === selectedId && node.nodeType === "LECTURE") ?? null;
  const courseFiles = materials.filter(
    (material) => !material.lectureNodeId && material.id !== course?.coverFileId,
  );

  function childrenOf(parentId: string) {
    return nodes.filter((node) => node.parentId === parentId).sort((a, b) => a.position - b.position);
  }

  async function addNode(e: FormEvent<HTMLFormElement>, nodeType: "SECTION" | "LECTURE", parentId?: string) {
    e.preventDefault();
    const form = e.currentTarget;
    const title = String(new FormData(form).get("title") ?? "").trim();
    if (!title) {
      return;
    }
    const body: Record<string, unknown> = { nodeType, title };
    if (parentId) {
      body.parentId = parentId;
    }
    setError(null);
    try {
      const created = await api.createCourseNode(courseId, body);
      form.reset();
      const nextNodes = await api.listCourseNodes(courseId);
      setNodes(nextNodes.some((node) => node.id === created.id) ? nextNodes : [...nextNodes, created]);
      if (nodeType === "SECTION") {
        setAddingSection(false);
        setLectureFormFor(created.id);
      } else {
        setLectureFormFor(null);
        setSelectedId(created.id);
        setEditing(false);
      }
    } catch (err) {
      setError(err);
    }
  }

  async function moveSibling(node: CourseNodeView, direction: -1 | 1) {
    const siblings = nodes
      .filter((item) => item.parentId === node.parentId)
      .sort((a, b) => a.position - b.position);
    const index = siblings.findIndex((item) => item.id === node.id);
    const target = index + direction;
    if (target < 0 || target >= siblings.length) {
      return;
    }
    const ordered = [...siblings];
    const [removed] = ordered.splice(index, 1);
    ordered.splice(target, 0, removed);
    setError(null);
    try {
      await api.reorderCourseNodes(courseId, {
        parentId: node.parentId,
        nodeIds: ordered.map((item) => item.id),
      });
      setNodes(await api.listCourseNodes(courseId));
    } catch (err) {
      setError(err);
    }
  }

  async function removeNode(node: CourseNodeView) {
    const message =
      node.nodeType === "LECTURE" ? t("courses.deleteLectureConfirm") : t("courses.deleteSectionConfirm");
    if (!window.confirm(message)) {
      return;
    }
    setError(null);
    try {
      await api.deleteCourseNode(courseId, node.id);
      if (selectedId === node.id) {
        setSelectedId(null);
      }
      const [nextNodes, nextMaterials] = await Promise.all([
        api.listCourseNodes(courseId),
        api.listCourseMaterials(courseId),
      ]);
      setNodes(nextNodes);
      setMaterials(nextMaterials);
    } catch (err) {
      setError(err);
    }
  }

  async function uploadFile(file: File, lectureNodeId?: string) {
    setError(null);
    try {
      await api.uploadCourseMaterial(
        courseId,
        file,
        lectureNodeId ? { lectureNodeId } : {},
      );
      setMaterials(await api.listCourseMaterials(courseId));
    } catch (err) {
      setError(err);
    }
  }

  async function replaceFile(materialId: string, file: File) {
    setError(null);
    try {
      await api.replaceCourseMaterial(courseId, materialId, file);
      setMaterials(await api.listCourseMaterials(courseId));
    } catch (err) {
      setError(err);
    }
  }

  async function removeFile(material: MaterialView) {
    if (!window.confirm(t("courses.deleteFileConfirm"))) {
      return;
    }
    setError(null);
    try {
      await api.deleteCourseMaterial(courseId, material.id);
      setMaterials(await api.listCourseMaterials(courseId));
    } catch (err) {
      setError(err);
    }
  }

  async function markMaterialFree(materialId: string) {
    setError(null);
    try {
      await api.updateCourseMaterialFree(courseId, materialId, true);
      setMaterials(await api.listCourseMaterials(courseId));
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

  function openLecture(id: string) {
    setSelectedId(id);
    setEditing(false);
  }

  function ends(node: CourseNodeView) {
    const siblings = nodes
      .filter((item) => item.parentId === node.parentId)
      .sort((a, b) => a.position - b.position);
    const index = siblings.findIndex((item) => item.id === node.id);
    return { isFirst: index <= 0, isLast: index < 0 || index === siblings.length - 1 };
  }

  const lectureMaterials = selected
    ? materials.filter((material) => material.lectureNodeId === selected.id)
    : [];
  const editor = selected ? (
    editing ? (
      <LectureEditor
        key={selected.id}
        course={course}
        node={selected}
        nodes={nodes}
        materials={lectureMaterials}
        isMaster={isMaster}
        onSaved={async () => {
          const [nextNodes, nextMaterials] = await Promise.all([
            api.listCourseNodes(courseId),
            api.listCourseMaterials(courseId),
          ]);
          setNodes(nextNodes);
          setMaterials(nextMaterials);
        }}
        onUpload={(file) => uploadFile(file, selected.id)}
        onReplace={replaceFile}
        onDeleteFile={(material) => void removeFile(material)}
        onMarkFree={(id) => void markMaterialFree(id)}
        onError={setError}
        onDone={() => setEditing(false)}
        onRegisterSave={(save) => {
          saveLecture.current = save;
        }}
      />
    ) : (
      <LectureDetails
        node={selected}
        nodes={nodes}
        materials={lectureMaterials}
        onEdit={() => setEditing(true)}
      />
    )
  ) : (
    <p className="rounded-lg border border-dashed border-zinc-300 px-4 py-6 text-sm text-zinc-600">
      {t("courses.selectLecture")}
    </p>
  );

  return (
    <CourseShell course={course} step="content" onUpdated={setCourse} onError={setError}>
      <FormError error={error} />
      <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div className="space-y-4">
          {nodes.length === 0 ? <p className="text-sm text-zinc-600">{t("courses.contentEmpty")}</p> : null}
          {rootLectures.length > 0 ? (
            <section className="rounded-lg border border-zinc-200 bg-white" aria-label={t("courses.lecturesOnCourse")}>
              <h2 className="border-b border-zinc-200 px-4 py-3 text-lg font-semibold">{t("courses.lecturesOnCourse")}</h2>
              <ul>
                {rootLectures.map((lecture, index) => (
                  <LectureRow
                    key={lecture.id}
                    lecture={lecture}
                    number={index + 1}
                    selected={selected?.id === lecture.id}
                    fileCount={materials.filter((material) => material.lectureNodeId === lecture.id).length}
                    isMaster={isMaster}
                    ends={ends(lecture)}
                    onOpen={openLecture}
                    onMove={(direction) => void moveSibling(lecture, direction)}
                    onDelete={() => void removeNode(lecture)}
                  />
                ))}
              </ul>
            </section>
          ) : null}
          {rootSections.map((section, index) => (
            <SectionCard
              key={section.id}
              section={section}
              number={index + 1}
              childrenOf={childrenOf}
              materials={materials}
              lectureFormFor={lectureFormFor}
              selectedId={selected?.id ?? null}
              isMaster={isMaster}
              ends={ends}
              onAddLecture={setLectureFormFor}
              onAdd={(e, parentId) => void addNode(e, "LECTURE", parentId)}
              onOpen={openLecture}
              onMove={(item, direction) => void moveSibling(item, direction)}
              onDelete={(item) => void removeNode(item)}
            />
          ))}
          {addingSection ? (
            <form
              className="flex flex-wrap items-end gap-2 rounded-lg border border-zinc-200 bg-white p-4"
              aria-label={t("courses.addSection")}
              onSubmit={(e) => void addNode(e, "SECTION")}
            >
              <label className="block min-w-0 flex-1 text-sm">
                {t("courses.sectionTitle")}
                <input name="title" required className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2" />
              </label>
              <button type="submit" className="rounded-md bg-zinc-900 px-3 py-2 text-sm text-white">
                {t("courses.addSection")}
              </button>
            </form>
          ) : (
            <button
              type="button"
              className="rounded-md bg-zinc-900 px-4 py-2 text-sm text-white"
              onClick={() => setAddingSection(true)}
            >
              {t("courses.addSection")}
            </button>
          )}
          <CourseFiles
            course={course}
            materials={courseFiles}
            isMaster={isMaster}
            onUpload={(file) => uploadFile(file)}
            onReplace={replaceFile}
            onDelete={(material) => void removeFile(material)}
            onMarkFree={(id) => void markMaterialFree(id)}
          />
          <div className="flex flex-wrap items-center gap-3 text-sm">
            <Link href={`/courses/${courseId}/details`} className="text-zinc-600 hover:text-zinc-900">
              {t("courses.backToDetails")}
            </Link>
            <button
              type="button"
              className="rounded-md bg-zinc-900 px-3 py-2 text-white"
              onClick={() => {
                void (async () => {
                  try {
                    if (saveLecture.current) {
                      await saveLecture.current();
                    }
                    router.push(`/courses/${courseId}`);
                  } catch {
                    /* the lecture save already reported the error */
                  }
                })();
              }}
            >
              {t("courses.saveAndView")}
            </button>
          </div>
        </div>
        <div className="lg:sticky lg:top-4">{editor}</div>
      </div>
    </CourseShell>
  );
}
