"use client";

/**
 * Catalog authoring for staff: categories, modules, exams, questions, roster
 * and the one instructor per module. Query keys are prefixed `staffCatalog`.
 * Mutations invalidate only the keys they change.
 */

import { useMutation, useQuery, useQueryClient, keepPreviousData, type QueryClient } from "@tanstack/react-query";
import { apiBlob, apiRequest, apiUpload } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import type { PageResponse, PaperLanguage, ExamType } from "@/lib/api/exam/types";

export type CategoryTreeNode = {
  id: string;
  parentId: string | null;
  titleEn: string;
  titleBn: string;
  position: number;
  archived: boolean;
  children: CategoryTreeNode[];
  modules: ModuleSummary[];
};

export type CategoryView = Omit<CategoryTreeNode, "children" | "modules">;

export type ModuleSummary = {
  id: string;
  categoryId: string;
  titleEn: string;
  titleBn: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  position: number;
};

export type StaffModule = {
  id: string;
  categoryId: string;
  titleEn: string;
  titleBn: string;
  descriptionEn: string | null;
  descriptionBn: string | null;
  accessMode: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  finallySaved: boolean;
  position: number;
  /** `null` when no price is set. Only Master Admin sees it as an input. */
  price: number | null;
  /** From the single module read (G4): the one assigned instructor, or `null`. Absent on list rows. */
  instructor?: ModuleInstructor | null;
  /** Exam counts per type, on list rows and the single read (G5). */
  mcqCount?: number;
  writtenCount?: number;
  /** The package cover path, or null. Only the Master Admin changes it (see the cover uploader). */
  coverUrl?: string | null;
  /** Discount and popular flag (NR-01). Only the Master Admin changes them. */
  discountPercent?: number | null;
  discountEndsAt?: string | null;
  popular?: boolean;
  /** True when a staged change waits for the Master Admin. */
  pendingChanges?: boolean;
  pricing?: PriceView | null;
};

/** The price block of a package or an exam (NR-01). Amounts are in taka; `null` when not set. */
export type PriceView = {
  price: number | null;
  discountPercent: number | null;
  discountEndsAt: string | null;
  effectivePrice: number | null;
  free: boolean;
  moduleOnly: boolean;
};

export type PackageRef = { id: string; titleEn: string; titleBn: string };

export type ExamInstructor = { id: string; name: string };

export type ModuleInstructor = { id: string; name: string; phone: string | null };

export type StaffQuestionOption = { id: string; text: string; correct: boolean; position: number };

export type StaffQuestion = {
  id: string;
  stem: string;
  marks: number;
  explanation: string | null;
  position: number;
  options: StaffQuestionOption[];
};

/** `status` is DRAFT, PUBLISHED or ARCHIVED; `myStatus` is only set for students. */
export type StaffExam = {
  /** The exam's own cover path, or null (Phase 4, E1). Only the Master Admin changes it (see the exam cover uploader). */
  coverUrl?: string | null;
  id: string;
  /** The primary package, or `null` for a standalone exam (NR-01). */
  moduleId: string | null;
  examType: ExamType;
  /** `null` when no paper language was chosen. Nothing is shown for it. */
  paperLanguage: PaperLanguage | null;
  titleEn: string;
  titleBn: string;
  descriptionEn: string | null;
  descriptionBn: string | null;
  rulesEn: string | null;
  rulesBn: string | null;
  /** ISO UTC, or `null` for "তারিখ এখনো নয়". */
  liveAt: string | null;
  endAt: string | null;
  durationMinutes: number;
  passPercent: number | null;
  maximum: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  finallySaved: boolean;
  price: number | null;
  questionPdf: boolean | null;
  questions: StaffQuestion[] | null;
  /** NR-01 fields. Optional here so that older rows and test data still type-check. */
  categoryId?: string | null;
  packages?: PackageRef[];
  mode?: "LIVE" | "PRACTICE";
  phase?: string | null;
  negativeMarks?: number | null;
  answersChangeable?: boolean;
  previewEnabled?: boolean;
  popular?: boolean;
  accessDays?: number | null;
  free?: boolean;
  discountPercent?: number | null;
  discountEndsAt?: string | null;
  pricing?: PriceView | null;
  instructor?: ExamInstructor | null;
  /** True when a staged change waits for the Master Admin (staff reads only). */
  pendingChanges?: boolean | null;
};

export type StudentMatch = { id: string; fullName: string; phone: string | null; email: string | null };

/** One row of `GET .../roster`: everyone already marked for the module. */
export type RosterMember = {
  studentId: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  studentCode: string | null;
  addedAt: string;
  /** True when the student has a sitting in this module: the result stays after removal. */
  startedAny: boolean;
};

export type PasteResult = { marked: StudentMatch[]; unknown: string[] };

export type InstructorRow = {
  id: string;
  fullName: string | null;
  phone: string | null;
  email: string | null;
  status: "ACTIVE" | "PENDING_ACTIVATION" | "INACTIVE";
  createdAt: string;
  modules: { id: string; titleEn: string; titleBn: string; status: string }[];
};

export type ExamInput = Partial<
  Pick<
    StaffExam,
    | "titleEn"
    | "titleBn"
    | "descriptionEn"
    | "descriptionBn"
    | "rulesEn"
    | "rulesBn"
    | "paperLanguage"
    | "durationMinutes"
    | "passPercent"
    | "maximum"
    | "liveAt"
    | "endAt"
    | "price"
    | "mode"
    | "negativeMarks"
    | "answersChangeable"
    | "previewEnabled"
    | "free"
    | "discountPercent"
    | "discountEndsAt"
    | "accessDays"
  >
>;

export type ModuleInput = Partial<
  Pick<
    StaffModule,
    "categoryId" | "titleEn" | "titleBn" | "descriptionEn" | "descriptionBn" | "price" | "position" | "discountPercent" | "discountEndsAt"
  >
>;

/** One field of a staged change: the published value and the staged one (`before`, `after`). */
export type PendingFieldChange = { field: string; before: unknown; after: unknown };

export type PendingQuestionChange = {
  questionId: string;
  position: number | null;
  change: string;
  before: unknown;
  after: unknown;
};

export type PendingPaperChange = {
  beforeVersion: number | null;
  afterVersion: number | null;
  afterFileName: string | null;
};

export type PendingMembershipChange = { examId: string; titleEn: string; titleBn: string; change: string };

/** How many papers are to be re-scored and how many students are to be told, once the change is published. */
export type PendingImpact = { papersToRescore: number; notifiesStudents: boolean; studentsToNotify: number };

/** The change list of an exam or a package: what the Master Admin reads before publishing (NR-01, E31). */
export type PendingChangeList = {
  revisionId: string;
  fields: PendingFieldChange[];
  questions: PendingQuestionChange[];
  paper: PendingPaperChange | null;
  packageExams: PendingMembershipChange[];
  impact: PendingImpact;
};

export type QuestionInput = {
  stem: string;
  marks: number;
  explanation: string | null;
  position?: number;
  options: { text: string; correct: boolean }[];
};

export const staffCatalogKeys = {
  categories: ["staffCatalog", "categories"] as const,
  category: (id: string) => ["staffCatalog", "category", id] as const,
  modules: (params: ModuleListParams) => ["staffCatalog", "modules", params] as const,
  module: (id: string) => ["staffCatalog", "module", id] as const,
  moduleExams: (moduleId: string) => ["staffCatalog", "moduleExams", moduleId] as const,
  exam: (id: string) => ["staffCatalog", "exam", id] as const,
  roster: (moduleId: string, q: string, page: number) => ["staffCatalog", "roster", moduleId, q, page] as const,
  rosterSearch: (moduleId: string, q: string) => ["staffCatalog", "rosterSearch", moduleId, q] as const,
  instructors: (params: InstructorListParams) => ["staffCatalog", "instructors", params] as const,
  pending: (kind: ChangeKind, id: string) => ["staffCatalog", "pending", kind, id] as const,
};

/* ---------- categories ---------- */

export function useCategoryTree() {
  return useQuery({
    queryKey: staffCatalogKeys.categories,
    queryFn: () => apiRequest<CategoryTreeNode[]>("/api/v1/live-exam-categories"),
    staleTime: 60_000,
  });
}

export function useCategory(id: string | undefined) {
  return useQuery({
    queryKey: staffCatalogKeys.category(id ?? ""),
    queryFn: () => apiRequest<CategoryView>(`/api/v1/live-exam-categories/${id}`),
    enabled: Boolean(id),
  });
}

export type CategoryInput = { parentId: string | null; titleEn: string; titleBn: string };

export function useSaveCategory(id?: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CategoryInput) =>
      id
        ? apiRequest<CategoryView>(`/api/v1/live-exam-categories/${id}`, { method: "PATCH", body })
        : apiRequest<CategoryView>("/api/v1/live-exam-categories", { method: "POST", body }),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "categories"] });
      queryClient.setQueryData(staffCatalogKeys.category(saved.id), saved);
    },
  });
}

export function useDeleteCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequest<void>(`/api/v1/live-exam-categories/${id}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffCatalog", "categories"] }),
  });
}

export function useArchiveCategory() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      apiRequest<void>(`/api/v1/live-exam-categories/${id}/archive`, { method: "POST", body: {} }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffCatalog", "categories"] }),
  });
}

/* ---------- modules ---------- */

export type ModuleListParams = { categoryId?: string; q?: string; status?: string };

/** Search (`q`, either title) and status are applied by the server (G5). */
export function useModules(params: ModuleListParams = {}) {
  const query = new URLSearchParams({ size: "100" });
  if (params.categoryId) {
    query.set("categoryId", params.categoryId);
  }
  if (params.q) {
    query.set("q", params.q);
  }
  if (params.status) {
    query.set("status", params.status);
  }
  return useQuery({
    queryKey: staffCatalogKeys.modules(params),
    queryFn: () => apiRequest<PageResponse<StaffModule>>(`/api/v1/live-exam-modules?${query}`),
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

export function useModule(id: string | undefined) {
  return useQuery({
    queryKey: staffCatalogKeys.module(id ?? ""),
    queryFn: () => apiRequest<StaffModule>(`/api/v1/live-exam-modules/${id}`),
    enabled: Boolean(id),
  });
}

/** Save a module. On a published package the edit is staged; it is published with the change list (NR-01). */
export function useSaveModule(id?: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ModuleInput) =>
      id
        ? apiRequest<StaffModule>(`/api/v1/live-exam-modules/${id}`, { method: "PATCH", body })
        : apiRequest<StaffModule>("/api/v1/live-exam-modules", { method: "POST", body }),
    onSuccess: (saved) => {
      queryClient.setQueryData(staffCatalogKeys.module(saved.id), saved);
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "modules"] });
    },
  });
}

/** Uploads the package cover (Master Admin only). The server replaces any previous cover. */
export function useUploadModuleCover(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append("file", file);
      return apiRequest<StaffModule>(`/api/v1/live-exam-modules/${id}/cover`, { method: "POST", body: form });
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(staffCatalogKeys.module(saved.id), saved);
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "modules"] });
    },
  });
}

/**
 * The module's cover for the staff screens, as a blob URL. The public cover address serves only published
 * modules and an image tag cannot send the login token, so the image is read with the token here.
 * `version` changes with each upload (`coverUrl` carries it), so a new cover is fetched.
 */
export function useStaffModuleCover(moduleId: string, version: string | null | undefined) {
  return useQuery({
    queryKey: ["staffCatalog", "cover", moduleId, version ?? ""],
    queryFn: async () => URL.createObjectURL(await apiBlob(`/api/v1/live-exam-modules/${moduleId}/cover`)),
    enabled: Boolean(version),
    staleTime: Infinity,
  });
}

/** Replaces the exam's cover (Master Admin only). The change is live at once, as the package cover is (Phase 4, E1). */
export function useUploadExamCover(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append("file", file);
      return apiRequest<StaffExam>(`/api/v1/live-exams/${examId}/cover`, { method: "POST", body: form });
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "exam", saved.id] });
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "exams"] });
    },
  });
}

/** The exam's cover for the staff screen, as an object URL. `version` changes with each upload, so a new cover is fetched. */
export function useStaffExamCover(examId: string, version: string | null | undefined) {
  return useQuery({
    queryKey: ["staffCatalog", "examCover", examId, version ?? ""],
    queryFn: async () => URL.createObjectURL(await apiBlob(`/api/v1/live-exams/${examId}/cover`)),
    enabled: Boolean(version),
    staleTime: Infinity,
  });
}

/** Removes the exam's cover (Master Admin only). */
export function useRemoveExamCover(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<StaffExam>(`/api/v1/live-exams/${examId}/cover`, { method: "DELETE" }),
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "exam", saved.id] });
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "exams"] });
    },
  });
}

/** Removes the package cover (Master Admin only). */
export function useRemoveModuleCover(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<StaffModule>(`/api/v1/live-exam-modules/${id}/cover`, { method: "DELETE" }),
    onSuccess: (saved) => {
      queryClient.setQueryData(staffCatalogKeys.module(saved.id), saved);
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "modules"] });
    },
  });
}

export function useModuleAction(id: string, action: "final-save" | "publish" | "archive") {
  const queryClient = useQueryClient();
  return useMutation({
    /** Resolves to the module for publish and final save; `null` for archive, which returns no body. */
    mutationFn: async (): Promise<StaffModule | null> =>
      action === "archive"
        ? (await apiRequest<void>(`/api/v1/live-exam-modules/${id}/archive`, { method: "POST", body: {} }), null)
        : apiRequest<StaffModule>(`/api/v1/live-exam-modules/${id}/${action}`, { method: "POST", body: {} }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: staffCatalogKeys.module(id) });
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "modules"] });
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "categories"] });
    },
  });
}

export function useDeleteModule(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<void>(`/api/v1/live-exam-modules/${id}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffCatalog", "modules"] }),
  });
}

/* ---------- exams ---------- */

/** The query for one module's exams, shared by the module page and the admin agenda (UF6). */
export function moduleExamsQuery(moduleId: string) {
  return {
    queryKey: staffCatalogKeys.moduleExams(moduleId),
    queryFn: () => apiRequest<PageResponse<StaffExam>>(`/api/v1/live-exam-modules/${moduleId}/exams?size=100`),
  };
}

export function useModuleExams(moduleId: string | undefined) {
  return useQuery({
    ...moduleExamsQuery(moduleId ?? ""),
    enabled: Boolean(moduleId),
  });
}

export function useExam(id: string | undefined) {
  return useQuery({
    queryKey: staffCatalogKeys.exam(id ?? ""),
    queryFn: () => apiRequest<StaffExam>(`/api/v1/live-exams/${id}`),
    enabled: Boolean(id),
  });
}

export function useCreateExam(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ExamInput & { examType: ExamType }) =>
      apiRequest<StaffExam>(`/api/v1/live-exam-modules/${moduleId}/exams`, { method: "POST", body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: staffCatalogKeys.moduleExams(moduleId) }),
  });
}

/**
 * The create call of the New exam dialog: a package's exam list, or a standalone exam with its category (R2). The
 * dialog builds the path and the body; any exam list may hold the new exam, so all of them are refreshed.
 */
export function useCreateNewExam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ path, body }: { path: string; body: Record<string, unknown> }) =>
      apiRequest<StaffExam>(path, { method: "POST", body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffCatalog", "moduleExams"] }),
  });
}

/* ---------- staged changes, packages, instructor and popular (NR-01) ---------- */

/** Which record a pending-changes call is for. An exam and a package share the same change list shape. */
export type ChangeKind = "exam" | "package";

function changesPath(kind: ChangeKind, id: string): string {
  return kind === "exam" ? `/api/v1/live-exams/${id}/pending-changes` : `/api/v1/live-exam-modules/${id}/pending-changes`;
}

function publishPath(kind: ChangeKind, id: string): string {
  return kind === "exam" ? `/api/v1/live-exams/${id}/publish` : `/api/v1/live-exam-modules/${id}/publish`;
}

/** Refreshes what a change to a staged record touches: its own read, its list rows, and the change list. */
function refreshChanged(queryClient: QueryClient, kind: ChangeKind, id: string): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: staffCatalogKeys.pending(kind, id) }),
    queryClient.invalidateQueries({ queryKey: kind === "exam" ? staffCatalogKeys.exam(id) : staffCatalogKeys.module(id) }),
    queryClient.invalidateQueries({ queryKey: ["staffCatalog", "moduleExams"] }),
    queryClient.invalidateQueries({ queryKey: ["staffCatalog", "modules"] }),
  ]);
}

/**
 * The staged change list of an exam or a package, or `null` when nothing is staged. The server answers 404 for that
 * case, and it is not an error here.
 */
export function usePendingChanges(kind: ChangeKind, id: string | undefined) {
  return useQuery({
    queryKey: staffCatalogKeys.pending(kind, id ?? ""),
    queryFn: async (): Promise<PendingChangeList | null> => {
      try {
        return await apiRequest<PendingChangeList>(changesPath(kind, id ?? ""));
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
    enabled: Boolean(id),
    retry: false,
  });
}

/** Throws away the staged changes. The published record stays as it is. */
export function useDiscardPendingChanges(kind: ChangeKind, id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<void>(changesPath(kind, id), { method: "DELETE" }),
    onSuccess: () => refreshChanged(queryClient, kind, id),
  });
}

/**
 * Publishes the staged changes named by `revisionId` (the one the change list showed). A stale id is refused with
 * `PENDING_CHANGES_STALE`, and the dialog then asks the reader to read the list again.
 */
export function usePublishPendingChanges(kind: ChangeKind, id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (revisionId: string) =>
      apiRequest<StaffExam | StaffModule>(publishPath(kind, id), { method: "POST", body: { revisionId } }),
    onSuccess: (saved) => {
      queryClient.setQueryData(kind === "exam" ? staffCatalogKeys.exam(id) : staffCatalogKeys.module(id), saved);
      return refreshChanged(queryClient, kind, id);
    },
  });
}

/** The three package membership calls. Add and remove name the exam in the path; reorder sends the whole order (NR-01). */
export type MembershipAction = { kind: "add" | "remove"; examId: string } | { kind: "reorder"; examIds: string[] };

export function membershipRequest(moduleId: string, action: MembershipAction): { method: "POST" | "DELETE" | "PATCH"; path: string; body?: { examIds: string[] } } {
  if (action.kind === "reorder") {
    return { method: "PATCH", path: `/api/v1/live-exam-modules/${moduleId}/exams/order`, body: { examIds: action.examIds } };
  }
  return {
    method: action.kind === "add" ? "POST" : "DELETE",
    path: `/api/v1/live-exam-modules/${moduleId}/exams/${action.examId}`,
  };
}

/** Sends one membership call and refreshes the package. */
function useMembership(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (action: MembershipAction) => {
      const request = membershipRequest(moduleId, action);
      return apiRequest<unknown>(request.path, { method: request.method, body: request.body ?? {} });
    },
    onSuccess: () => refreshChanged(queryClient, "package", moduleId),
  });
}

/** Adds an exam to a package. An exam already in it is refused with `EXAM_ALREADY_IN_PACKAGE`. */
export function useAddExamToPackage(moduleId: string) {
  const membership = useMembership(moduleId);
  return useMutation({
    mutationFn: (examId: string) => membership.mutateAsync({ kind: "add", examId }),
  });
}

export function useRemoveExamFromPackage(moduleId: string) {
  const membership = useMembership(moduleId);
  return useMutation({
    mutationFn: (examId: string) => membership.mutateAsync({ kind: "remove", examId }),
  });
}

/** Sets the new order of a package's exams. `examIds` lists every exam of the package, in that order. */
export function useReorderPackageExams(moduleId: string) {
  const membership = useMembership(moduleId);
  return useMutation({
    mutationFn: (examIds: string[]) => membership.mutateAsync({ kind: "reorder", examIds }),
  });
}

/** Exams matching a search, for "Add existing exam". Nothing is searched below two characters. */
export function useExamSearch(q: string) {
  const term = q.trim();
  return useQuery({
    queryKey: ["staffCatalog", "examSearch", term],
    queryFn: () => apiRequest<PageResponse<StaffExam>>(`/api/v1/live-exams?${new URLSearchParams({ q: term, size: "20" })}`),
    enabled: term.length >= 2,
    placeholderData: keepPreviousData,
  });
}

/** One instructor per exam (Master Admin). */
export function useAssignExamInstructor(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (instructorId: string) =>
      apiRequest<unknown>(`/api/v1/live-exams/${examId}/instructor`, { method: "POST", body: { instructorId } }),
    onSuccess: () => refreshChanged(queryClient, "exam", examId),
  });
}

export function useUnassignExamInstructor(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<void>(`/api/v1/live-exams/${examId}/instructor`, { method: "DELETE" }),
    onSuccess: () => refreshChanged(queryClient, "exam", examId),
  });
}

/** The popular flag of an exam (Master Admin). */
export function useSetExamPopular(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (popular: boolean) =>
      apiRequest<unknown>(`/api/v1/live-exams/${examId}/popular`, { method: "PUT", body: { popular } }),
    onSuccess: () => refreshChanged(queryClient, "exam", examId),
  });
}

/** The popular flag of a package (Master Admin). */
export function useSetPackagePopular(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (popular: boolean) =>
      apiRequest<unknown>(`/api/v1/live-exam-modules/${moduleId}/popular`, { method: "PUT", body: { popular } }),
    onSuccess: () => refreshChanged(queryClient, "package", moduleId),
  });
}

/** What the header's one primary button does for an exam (plan 7.2). */
export type PrimaryExamAction = "save" | "finalSave" | "publish" | "publishChanges" | "none";

/**
 * Chooses the primary button from the exam's state. Unsaved edits come first. A published exam with staged changes
 * offers "Publish changes" to the Master Admin only; a System Admin sees the waiting banner instead. A draft needs a
 * final save before it can be published.
 */
export function primaryExamAction(
  exam: Pick<StaffExam, "status" | "finallySaved" | "pendingChanges">,
  state: { dirty: boolean; masterAdmin: boolean },
): PrimaryExamAction {
  if (exam.status === "ARCHIVED") {
    return "none";
  }
  if (state.dirty) {
    return "save";
  }
  if (exam.status === "PUBLISHED") {
    return exam.pendingChanges === true && state.masterAdmin ? "publishChanges" : "none";
  }
  if (!state.masterAdmin) {
    return "none";
  }
  return exam.finallySaved ? "publish" : "finalSave";
}

export function useSaveExam(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ExamInput) =>
      apiRequest<StaffExam>(`/api/v1/live-exams/${id}`, { method: "PATCH", body }),
    onSuccess: (saved) => {
      queryClient.setQueryData(staffCatalogKeys.exam(id), saved);
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "moduleExams", saved.moduleId] });
    },
  });
}

/** A draft copy in the same module, with no date and no price. The server adds "(কপি)" to the title (G13). */
export function useCopyExam(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (examId: string) => apiRequest<StaffExam>(`/api/v1/live-exams/${examId}/copy`, { method: "POST", body: {} }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: staffCatalogKeys.moduleExams(moduleId) }),
  });
}

export function useExamAction(id: string, action: "final-save" | "publish") {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<StaffExam>(`/api/v1/live-exams/${id}/${action}`, { method: "POST", body: {} }),
    onSuccess: (saved) => {
      queryClient.setQueryData(staffCatalogKeys.exam(id), saved);
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "moduleExams", saved.moduleId] });
    },
  });
}

export function useDeleteExam(id: string, moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<void>(`/api/v1/live-exams/${id}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: staffCatalogKeys.moduleExams(moduleId) }),
  });
}

/** Archive hides the exam from students and keeps its posted results (Master Admin only, 2026-10-27). */
export function useArchiveExam(id: string, moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<StaffExam>(`/api/v1/live-exams/${id}/archive`, { method: "POST", body: {} }),
    onSuccess: (saved) => {
      queryClient.setQueryData(staffCatalogKeys.exam(id), saved);
      queryClient.invalidateQueries({ queryKey: staffCatalogKeys.moduleExams(moduleId) });
    },
  });
}

export function useAddQuestion(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: QuestionInput) =>
      apiRequest<StaffQuestion>(`/api/v1/live-exams/${examId}/questions`, { method: "POST", body }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: staffCatalogKeys.exam(examId) }),
  });
}

export function useUpdateQuestion(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ questionId, body }: { questionId: string; body: Partial<QuestionInput> }) =>
      apiRequest<StaffQuestion>(`/api/v1/live-exams/${examId}/questions/${questionId}`, {
        method: "PATCH",
        body,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: staffCatalogKeys.exam(examId) }),
  });
}

export function useDeleteQuestion(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (questionId: string) =>
      apiRequest<void>(`/api/v1/live-exams/${examId}/questions/${questionId}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: staffCatalogKeys.exam(examId) }),
  });
}

/** Uploads the written paper PDF. Progress is reported while the file is sent. */
export function useUploadQuestionPdf(examId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ file, onProgress }: { file: File; onProgress?: (percent: number) => void }) => {
      const form = new FormData();
      form.append("file", file);
      return apiUpload<StaffExam>(`/api/v1/live-exams/${examId}/question-pdf`, form, { onProgress });
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(staffCatalogKeys.exam(examId), saved);
    },
  });
}

/* ---------- roster (Master Admin) ---------- */

/** The full member list, paged, with `q` matching name, phone, email or code. */
export function useRoster(moduleId: string, q: string, page: number) {
  const query = new URLSearchParams({ page: String(page), size: "20" });
  if (q) {
    query.set("q", q);
  }
  return useQuery({
    queryKey: staffCatalogKeys.roster(moduleId, q, page),
    queryFn: () => apiRequest<PageResponse<RosterMember>>(`/api/v1/live-exam-modules/${moduleId}/roster?${query}`),
    placeholderData: keepPreviousData,
  });
}

export function useRosterSearch(moduleId: string, q: string) {
  return useQuery({
    queryKey: staffCatalogKeys.rosterSearch(moduleId, q),
    queryFn: () =>
      apiRequest<StudentMatch[]>(
        `/api/v1/live-exam-modules/${moduleId}/roster/search?q=${encodeURIComponent(q)}`,
      ),
    enabled: q.trim().length >= 2,
    placeholderData: keepPreviousData,
  });
}

export function useAddToRoster(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (studentId: string) =>
      apiRequest<StudentMatch>(`/api/v1/live-exam-modules/${moduleId}/roster`, {
        method: "POST",
        body: { studentId },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffCatalog", "roster", moduleId] }),
  });
}

export function usePasteRoster(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (values: string[]) =>
      apiRequest<PasteResult>(`/api/v1/live-exam-modules/${moduleId}/roster/paste`, {
        method: "POST",
        body: { values },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffCatalog", "roster", moduleId] }),
  });
}

export function useRemoveFromRoster(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (studentId: string) =>
      apiRequest<void>(`/api/v1/live-exam-modules/${moduleId}/roster/${studentId}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffCatalog", "roster", moduleId] }),
  });
}

/* ---------- instructors (Master Admin) ---------- */

export type InstructorListParams = { q?: string; status?: string; assigned?: "true" | "false" | ""; page?: number };

export function useInstructorDirectory(params: InstructorListParams) {
  const query = new URLSearchParams({ size: "20", page: String(params.page ?? 0) });
  if (params.q) {
    query.set("q", params.q);
  }
  if (params.status) {
    query.set("status", params.status);
  }
  if (params.assigned) {
    query.set("assigned", params.assigned);
  }
  return useQuery({
    queryKey: staffCatalogKeys.instructors(params),
    queryFn: () => apiRequest<PageResponse<InstructorRow>>(`/api/v1/instructors?${query}`),
    placeholderData: keepPreviousData,
  });
}

/** The package's default instructor. With `applyToExams` the exams that held the old one take the new one (NR-01). */
export function useAssignInstructor(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ instructorId, applyToExams }: { instructorId: string; applyToExams: boolean }) =>
      apiRequest<unknown>(`/api/v1/live-exam-modules/${moduleId}/instructor`, {
        method: "POST",
        body: { instructorId, applyToExams },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: staffCatalogKeys.module(moduleId) });
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "instructors"] });
    },
  });
}

/** Clears the package default. With `applyToExams` (the server's default) the exams that held it are cleared too. */
export function useUnassignInstructor(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (applyToExams: boolean) =>
      apiRequest<void>(`/api/v1/live-exam-modules/${moduleId}/instructor?applyToExams=${applyToExams}`, { method: "DELETE" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: staffCatalogKeys.module(moduleId) });
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "instructors"] });
    },
  });
}
