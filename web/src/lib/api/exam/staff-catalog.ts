"use client";

/**
 * Catalog authoring for staff: categories, modules, exams, questions, roster
 * and the one instructor per module. Query keys are prefixed `staffCatalog`.
 * Mutations invalidate only the keys they change.
 */

import { useMutation, useQuery, useQueryClient, keepPreviousData } from "@tanstack/react-query";
import { apiRequest, apiUpload } from "@/lib/api/client";
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
};

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
  id: string;
  moduleId: string;
  examType: ExamType;
  paperLanguage: PaperLanguage;
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
    | "price"
  >
>;

export type ModuleInput = Partial<
  Pick<StaffModule, "categoryId" | "titleEn" | "titleBn" | "descriptionEn" | "descriptionBn" | "price" | "position">
>;

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

export type CategoryInput = { parentId: string | null; titleEn: string; titleBn: string; position: number };

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

/** Save a module. A published or final module returns to draft on the server. */
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

export function useAssignInstructor(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (instructorId: string) =>
      apiRequest<unknown>(`/api/v1/live-exam-modules/${moduleId}/instructor`, {
        method: "POST",
        body: { instructorId },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: staffCatalogKeys.module(moduleId) });
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "instructors"] });
    },
  });
}

export function useUnassignInstructor(moduleId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiRequest<void>(`/api/v1/live-exam-modules/${moduleId}/instructor`, { method: "DELETE" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: staffCatalogKeys.module(moduleId) });
      queryClient.invalidateQueries({ queryKey: ["staffCatalog", "instructors"] });
    },
  });
}
