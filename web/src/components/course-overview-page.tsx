"use client";

import { useCallback, useEffect, useState } from "react";
import { AuthorizedFile } from "@/components/authorized-file";
import { MaterialViewCard } from "@/components/material-files";
import { LectureDetails } from "@/components/course-content-parts";
import { CoursePlacePath } from "@/components/course-place-path";
import { CourseShell } from "@/components/course-shell";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { AccountView, CategoryTreeNode, CourseNodeView, CourseView, MaterialView } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

export function CourseOverviewPage({ courseId }: { courseId: string }) {
  const { t } = useLocale();
  const user = useUser();
  const isMaster = user?.role === "MASTER_ADMIN";
  const [course, setCourse] = useState<CourseView | null>(null);
  const [tree, setTree] = useState<CategoryTreeNode[]>([]);
  const [nodes, setNodes] = useState<CourseNodeView[]>([]);
  const [materials, setMaterials] = useState<MaterialView[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [students, setStudents] = useState<AccountView[]>([]);
  const [admitStudentId, setAdmitStudentId] = useState("");
  const [admitDelivery, setAdmitDelivery] = useState<"ONLINE" | "OFFLINE">("ONLINE");
  const [tab, setTab] = useState<"overview" | "category" | "curriculum" | "materials">("overview");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextCourse, nextTree, nextNodes, nextMaterials] = await Promise.all([
        api.getCourse(courseId),
        api.listCourseCategories(),
        api.listCourseNodes(courseId),
        api.listCourseMaterials(courseId),
      ]);
      setCourse(nextCourse);
      setTree(nextTree);
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

  const showAdmission =
    course?.accessType === "FREEMIUM" && course.status === "PUBLISHED" && !course.closed;

  useEffect(() => {
    if (!showAdmission) {
      return;
    }
    void api.listStudents("", 0, 100).then((page) => setStudents(page.content));
  }, [showAdmission]);

  const path = course ? categoryPath(tree, course.id) : null;
  const courseFiles = materials.filter(
    (material) => !material.lectureNodeId && material.id !== course?.coverFileId,
  );

  async function admit() {
    if (!course || !admitStudentId) {
      return;
    }
    const body: { studentId: string; deliveryMode?: string } = { studentId: admitStudentId };
    if (course.deliveryMode === "HYBRID") {
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

  const language =
    course.contentLanguage === "BN" ? t("catalog.langBn") : t("catalog.langEn");
  const delivery =
    course.deliveryMode === "OFFLINE"
      ? t("catalog.deliveryOffline")
      : course.deliveryMode === "HYBRID"
        ? t("catalog.deliveryHybrid")
        : t("catalog.deliveryOnline");
  const access =
    course.accessType === "FULL_FREE" ? t("catalog.accessFullFree") : t("catalog.accessFreemium");

  const tabs = [
    ["overview", t("courses.tabOverview")],
    ["category", t("courses.stepCategory")],
    ["curriculum", t("courses.tabCurriculum")],
    ["materials", t("courses.materials")],
  ] as const;

  return (
    <CourseShell course={course} step="view" onUpdated={setCourse} onError={setError}>
      <FormError error={error} />
      <section className="grid gap-6 rounded-lg border border-zinc-200 bg-white p-4 lg:grid-cols-[14rem_minmax(0,1fr)]">
        <div>
          {course.coverFileId ? (
            <AuthorizedFile
              fileId={course.coverFileId}
              alt={course.titleEn}
              className="aspect-[4/3] w-full rounded-lg object-cover"
            />
          ) : (
            <div className="flex aspect-[4/3] items-center justify-center rounded-lg border border-dashed border-zinc-300 text-sm text-zinc-500">
              {t("courses.noCover")}
            </div>
          )}
        </div>
        <div>
          <h1 className="text-2xl font-semibold">{course.titleEn}</h1>
          <p className="text-zinc-600">{course.titleBn}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-700">
              {course.status === "PUBLISHED"
                ? t("courses.published")
                : course.status === "ARCHIVED"
                  ? t("courses.archived")
                  : t("courses.draft")}
            </span>
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-700">
              {course.closed ? t("courses.closed") : t("courses.open")}
            </span>
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-700">
              {t("courses.opening")}: {course.openingNumber}
            </span>
          </div>
          <dl className="mt-4 grid gap-4 sm:grid-cols-2">
            <Fact label={t("courses.contentLanguage")} value={language} />
            <Fact label={t("courses.deliveryMode")} value={delivery} />
            <Fact label={t("courses.accessType")} value={access} />
            {isMaster && course.accessType === "FREEMIUM" ? (
              <Fact label={t("courses.price")} value={course.price != null ? String(course.price) : "—"} />
            ) : null}
          </dl>
        </div>
      </section>
      <div role="tablist" aria-label={t("courses.viewCourse")} className="flex flex-wrap gap-2 border-b border-zinc-200">
        {tabs.map(([id, label]) => (
          <button
            key={id}
            type="button"
            role="tab"
            id={`course-tab-${id}`}
            aria-selected={tab === id}
            aria-controls={`course-panel-${id}`}
            className={`-mb-px border-b-2 px-3 py-2 text-sm ${tab === id ? "border-zinc-900 font-medium text-zinc-900" : "border-transparent text-zinc-500"}`}
            onClick={() => setTab(id)}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "overview" ? (
      <div role="tabpanel" id="course-panel-overview" aria-labelledby="course-tab-overview" className="space-y-6">
        <section className="rounded-lg border border-zinc-200 bg-white p-6">
          <h2 className="text-lg font-semibold">{t("courses.description")}</h2>
          <p className="mt-2 whitespace-pre-wrap text-zinc-800">{course.description}</p>
        </section>
        <section className="rounded-lg border border-zinc-200 bg-white p-6">
          <h2 className="text-lg font-semibold">{t("courses.stepCategory")}</h2>
          <PlaceChips path={path} />
        </section>
        {showAdmission ? (
          <section className="max-w-xl rounded-lg border border-zinc-200 bg-white p-6">
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
        ) : null}
      </div>
      ) : null}

      {tab === "category" ? (
        <div role="tabpanel" id="course-panel-category" aria-labelledby="course-tab-category">
          <section className="rounded-lg border border-zinc-200 bg-white p-6">
            <h2 className="text-lg font-semibold">{t("courses.stepCategory")}</h2>
            {tree.length === 0 ? <p className="mt-4 text-sm text-zinc-600">{t("courses.categoryEmpty")}</p> : null}
            {path && path.length > 0 ? (
              <CoursePlacePath nodes={tree} courseId={course.id} />
            ) : (
              <p className="mt-4 text-sm text-zinc-600">{t("courses.notPlaced")}</p>
            )}
          </section>
        </div>
      ) : null}

      {tab === "curriculum" ? (
        <div role="tabpanel" id="course-panel-curriculum" aria-labelledby="course-tab-curriculum">
          <Curriculum nodes={nodes} materials={materials} />
        </div>
      ) : null}

      {tab === "materials" ? (
        <div role="tabpanel" id="course-panel-materials" aria-labelledby="course-tab-materials">
          <Materials nodes={nodes} courseFiles={courseFiles} materials={materials} />
        </div>
      ) : null}
    </CourseShell>
  );
}

function PlaceChips({ path }: { path: string[] | null }) {
  const { t } = useLocale();
  if (!path || path.length === 0) {
    return <p className="mt-4 text-sm text-zinc-600">{t("courses.notPlaced")}</p>;
  }
  return (
    <div className="mt-4" aria-label={t("courses.placedIn", { path: path.join(" / ") })}>
      <p className="text-sm text-zinc-500">{t("courses.placedInLabel")}</p>
      <ol className="mt-3 flex flex-wrap items-center gap-2">
        {path.map((name, index) => (
          <li key={`${name}-${index}`} className="flex items-center gap-2">
            {index > 0 ? <span className="text-zinc-400">/</span> : null}
            <span className="rounded-full border border-zinc-200 bg-zinc-50 px-3 py-1 text-sm font-medium text-zinc-900">
              {name}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-zinc-500">{label}</dt>
      <dd className="mt-1 text-sm text-zinc-900">{value}</dd>
    </div>
  );
}

function Curriculum({ nodes, materials }: { nodes: CourseNodeView[]; materials: MaterialView[] }) {
  const { t } = useLocale();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const roots = nodes.filter((node) => node.parentId === null).sort((a, b) => a.position - b.position);
  const rootLectures = roots.filter((node) => node.nodeType === "LECTURE");
  const rootSections = roots.filter((node) => node.nodeType === "SECTION");
  const selected = nodes.find((node) => node.id === selectedId && node.nodeType === "LECTURE") ?? null;

  function childrenOf(parentId: string) {
    return nodes.filter((node) => node.parentId === parentId).sort((a, b) => a.position - b.position);
  }

  return (
    <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <div className="space-y-4">
        {nodes.length === 0 ? <p className="text-sm text-zinc-600">{t("courses.contentEmpty")}</p> : null}
        {rootLectures.length > 0 ? (
          <section className="rounded-lg border border-zinc-200 bg-white" aria-label={t("courses.lecturesOnCourse")}>
            <h2 className="border-b border-zinc-200 px-4 py-3 text-lg font-semibold">{t("courses.lecturesOnCourse")}</h2>
            <LectureList lectures={rootLectures} selectedId={selectedId} onOpen={setSelectedId} />
          </section>
        ) : null}
        {rootSections.map((section, index) => (
          <CurriculumSection
            key={section.id}
            section={section}
            number={index + 1}
            childrenOf={childrenOf}
            selectedId={selectedId}
            onOpen={setSelectedId}
          />
        ))}
      </div>
      <div>
        {selected ? (
          <LectureDetails
            node={selected}
            nodes={nodes}
            materials={materials.filter((material) => material.lectureNodeId === selected.id)}
          />
        ) : (
          <p className="rounded-lg border border-dashed border-zinc-300 px-4 py-6 text-sm text-zinc-600">
            {t("courses.selectLecture")}
          </p>
        )}
      </div>
    </div>
  );
}

function CurriculumSection({
  section,
  number,
  childrenOf,
  selectedId,
  onOpen,
}: {
  section: CourseNodeView;
  number: number;
  childrenOf: (parentId: string) => CourseNodeView[];
  selectedId: string | null;
  onOpen: (id: string) => void;
}) {
  const children = childrenOf(section.id);
  const lectures = children.filter((node) => node.nodeType === "LECTURE");
  const subsections = children.filter((node) => node.nodeType === "SECTION");
  const heading = `${number}. ${section.title}`;
  return (
    <section className="rounded-lg border border-zinc-200 bg-white" aria-label={heading}>
      <h2 className="border-b border-zinc-200 px-4 py-3 text-lg font-semibold">{heading}</h2>
      <LectureList lectures={lectures} selectedId={selectedId} onOpen={onOpen} />
      {subsections.length > 0 ? (
        <div className="space-y-3 border-t border-zinc-100 p-3">
          {subsections.map((child, index) => (
            <CurriculumSection
              key={child.id}
              section={child}
              number={index + 1}
              childrenOf={childrenOf}
              selectedId={selectedId}
              onOpen={onOpen}
            />
          ))}
        </div>
      ) : null}
    </section>
  );
}

function LectureList({
  lectures,
  selectedId,
  onOpen,
}: {
  lectures: CourseNodeView[];
  selectedId: string | null;
  onOpen: (id: string) => void;
}) {
  const { t } = useLocale();
  if (lectures.length === 0) {
    return null;
  }
  return (
    <ul>
      {lectures.map((lecture, index) => (
        <li key={lecture.id} className={`border-b border-zinc-100 px-4 py-2 last:border-b-0 ${selectedId === lecture.id ? "bg-zinc-100" : ""}`}>
          <button
            type="button"
            aria-current={selectedId === lecture.id ? "true" : undefined}
            aria-label={`${t("courses.lecture")} ${lecture.title}`}
            className="w-full py-1 text-left text-sm"
            onClick={() => onOpen(lecture.id)}
          >
            <span className="font-medium">
              {index + 1}. {lecture.title}
            </span>
            {lecture.free ? <span className="ml-2 text-xs text-green-700">{t("courses.free")}</span> : null}
          </button>
        </li>
      ))}
    </ul>
  );
}

function Materials({
  nodes,
  courseFiles,
  materials,
}: {
  nodes: CourseNodeView[];
  courseFiles: MaterialView[];
  materials: MaterialView[];
}) {
  const { t } = useLocale();
  const roots = nodes.filter((node) => node.parentId === null).sort((a, b) => a.position - b.position);
  const rootLectures = roots.filter((node) => node.nodeType === "LECTURE");
  const rootSections = roots.filter((node) => node.nodeType === "SECTION");

  function childrenOf(parentId: string) {
    return nodes.filter((node) => node.parentId === parentId).sort((a, b) => a.position - b.position);
  }

  function filesFor(lectureId: string) {
    return materials.filter((material) => material.lectureNodeId === lectureId);
  }

  const lectureFileCount = materials.filter((material) => material.lectureNodeId).length;
  const rootWithFiles = rootLectures
    .map((lecture) => ({ lecture, files: filesFor(lecture.id) }))
    .filter((entry) => entry.files.length > 0);

  return (
    <div className="space-y-8">
      <section className="overflow-hidden rounded-lg border border-zinc-200 bg-white" aria-label={t("courses.courseFiles")}>
        <header className="flex items-baseline justify-between gap-3 border-b border-zinc-200 px-5 py-4">
          <h2 className="text-lg font-semibold">{t("courses.courseFiles")}</h2>
          <p className="text-sm text-zinc-500">{fileCount(t, courseFiles.length)}</p>
        </header>
        {courseFiles.length === 0 ? (
          <p className="px-5 py-8 text-sm text-zinc-600">{t("courses.noCourseFiles")}</p>
        ) : (
          <ul className="grid gap-4 p-5 sm:grid-cols-2 xl:grid-cols-3">
            {courseFiles.map((material) => (
              <MaterialViewCard key={material.id} material={material} />
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-4" aria-label={t("courses.lectureFiles")}>
        <header className="flex items-baseline justify-between gap-3">
          <h2 className="text-lg font-semibold">{t("courses.lectureFiles")}</h2>
          <p className="text-sm text-zinc-500">{fileCount(t, lectureFileCount)}</p>
        </header>
        {lectureFileCount === 0 ? (
          <p className="rounded-lg border border-dashed border-zinc-300 px-5 py-8 text-sm text-zinc-600">{t("courses.noLectureFiles")}</p>
        ) : (
          <div className="space-y-4">
            {rootWithFiles.length > 0 ? (
              <LectureFileGroup heading={t("courses.lecturesOnCourse")} entries={rootWithFiles} />
            ) : null}
            {rootSections.map((section, index) => (
              <SectionFiles
                key={section.id}
                section={section}
                number={`${index + 1}`}
                childrenOf={childrenOf}
                filesFor={filesFor}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function fileCount(t: (key: string, vars?: Record<string, string | number>) => string, count: number) {
  return count === 1 ? t("courses.oneFile") : t("courses.manyFiles", { count });
}

function LectureFileGroup({
  heading,
  entries,
}: {
  heading: string;
  entries: { lecture: CourseNodeView; files: MaterialView[] }[];
}) {
  return (
    <section className="overflow-hidden rounded-lg border border-zinc-200 bg-white" aria-label={heading}>
      <h3 className="border-b border-zinc-200 px-5 py-3 font-semibold">{heading}</h3>
      <ul className="divide-y divide-zinc-100">
        {entries.map((entry, index) => (
          <LectureFiles key={entry.lecture.id} index={index} entry={entry} />
        ))}
      </ul>
    </section>
  );
}

function SectionFiles({
  section,
  number,
  childrenOf,
  filesFor,
}: {
  section: CourseNodeView;
  number: string;
  childrenOf: (parentId: string) => CourseNodeView[];
  filesFor: (lectureId: string) => MaterialView[];
}) {
  const children = childrenOf(section.id);
  const entries = children
    .filter((node) => node.nodeType === "LECTURE")
    .map((lecture) => ({ lecture, files: filesFor(lecture.id) }))
    .filter((entry) => entry.files.length > 0);
  const subsections = children.filter((node) => node.nodeType === "SECTION");
  const nested = subsections
    .map((child, index) => ({ child, number: `${number}.${index + 1}` }))
    .filter(({ child }) => sectionHasFiles(child, childrenOf, filesFor));
  if (entries.length === 0 && nested.length === 0) {
    return null;
  }
  const heading = `${number}. ${section.title}`;
  return (
    <section className="overflow-hidden rounded-lg border border-zinc-200 bg-white" aria-label={heading}>
      <h3 className="border-b border-zinc-200 px-5 py-3 font-semibold">{heading}</h3>
      {entries.length > 0 ? (
        <ul className="divide-y divide-zinc-100">
          {entries.map((entry, index) => (
            <LectureFiles key={entry.lecture.id} index={index} entry={entry} />
          ))}
        </ul>
      ) : null}
      {nested.length > 0 ? (
        <div className="space-y-3 border-t border-zinc-100 bg-zinc-50 p-3">
          {nested.map(({ child, number: nestedNumber }) => (
            <SectionFiles
              key={child.id}
              section={child}
              number={nestedNumber}
              childrenOf={childrenOf}
              filesFor={filesFor}
            />
          ))}
        </div>
      ) : null}
    </section>
  );
}

function LectureFiles({
  index,
  entry,
}: {
  index: number;
  entry: { lecture: CourseNodeView; files: MaterialView[] };
}) {
  const { t } = useLocale();
  return (
    <li className="px-5 py-4">
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <p className="text-sm font-medium text-zinc-900">
          {index + 1}. {entry.lecture.title}
        </p>
        <p className="text-xs text-zinc-500">{fileCount(t, entry.files.length)}</p>
      </div>
      <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {entry.files.map((material) => (
          <MaterialViewCard key={material.id} material={material} />
        ))}
      </ul>
    </li>
  );
}

function sectionHasFiles(
  section: CourseNodeView,
  childrenOf: (parentId: string) => CourseNodeView[],
  filesFor: (lectureId: string) => MaterialView[],
): boolean {
  const children = childrenOf(section.id);
  if (children.some((node) => node.nodeType === "LECTURE" && filesFor(node.id).length > 0)) {
    return true;
  }
  return children
    .filter((node) => node.nodeType === "SECTION")
    .some((child) => sectionHasFiles(child, childrenOf, filesFor));
}

function categoryPath(nodes: CategoryTreeNode[], courseId: string, trail: string[] = []): string[] | null {
  for (const node of nodes) {
    if (node.kind === "COURSE" && node.id === courseId) {
      return trail;
    }
    if (node.children?.length) {
      const nextTrail = node.kind === "CATEGORY" ? [...trail, node.titleEn] : trail;
      const found = categoryPath(node.children, courseId, nextTrail);
      if (found) {
        return found;
      }
    }
  }
  return null;
}
