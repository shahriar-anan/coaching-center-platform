"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiBlob, apiRequest, apiUpload } from "@/lib/api/client";
import type { AccountStatus, ExamType, PageResponse } from "@/lib/api/exam/types";

/*
 * Staff people and marking: students, instructors, system admins, participation,
 * and the submission marking screens. Owned by WS6a. Query keys start with "staffPeople".
 */

/** Builds a query string, leaving out empty values. */
function queryString(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === "") {
      continue;
    }
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

/* ---------- accounts ---------- */

/** `GET /api/v1/students` rows and `GET /api/v1/students/{id}`. */
export type StudentRow = {
  id: string;
  fullName: string;
  phone: string;
  email: string;
  role: string;
  status: AccountStatus;
  studentCode: string | null;
};

/** `POST /api/v1/students`, `/instructors`, `/admins`: the one-time code is shown once. */
export type CreatedAccount = {
  id: string;
  fullName: string;
  phone: string;
  email: string;
  role: string;
  status: AccountStatus;
  activationCode: string;
  studentCode: string | null;
};

export type NewPerson = { fullName: string; phone: string; email: string };

export type InstructorRow = {
  id: string;
  fullName: string;
  phone: string;
  email: string;
  status: AccountStatus;
  createdAt: string;
  modules: { id: string; titleEn: string; titleBn: string; status: string }[];
};

export type InstructorFilters = {
  page: number;
  size: number;
  q: string;
  status: string;
  assigned: string;
};

export function useStudents(filters: { page: number; size: number; q: string }) {
  return useQuery({
    queryKey: ["staffPeople", "students", filters],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<StudentRow>>(
        `/api/v1/students${queryString({ page: filters.page, size: filters.size, q: filters.q })}`,
        { signal },
      ),
    staleTime: 0,
  });
}

export function useStudent(id: string) {
  return useQuery({
    queryKey: ["staffPeople", "student", id],
    queryFn: ({ signal }) => apiRequest<StudentRow>(`/api/v1/students/${id}`, { signal }),
    staleTime: 0,
  });
}

/** Invalidates every people list after a change, so the list and the detail agree. */
function useInvalidatePeople() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["staffPeople"] });
}

export function useCreateStudent() {
  const invalidate = useInvalidatePeople();
  return useMutation({
    mutationFn: (body: NewPerson) =>
      apiRequest<CreatedAccount>("/api/v1/students", { method: "POST", body }),
    onSuccess: invalidate,
  });
}

export function useUpdateStudentName() {
  const invalidate = useInvalidatePeople();
  return useMutation({
    mutationFn: ({ id, fullName }: { id: string; fullName: string }) =>
      apiRequest<StudentRow>(`/api/v1/students/${id}`, { method: "PATCH", body: { fullName } }),
    onSuccess: invalidate,
  });
}

/** Sets a student or a system admin to ACTIVE or INACTIVE. The API has no other status. */
export function useSetStatus(kind: "students" | "admins") {
  const invalidate = useInvalidatePeople();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: "ACTIVE" | "INACTIVE" }) =>
      apiRequest<void>(`/api/v1/${kind}/${id}/status`, { method: "PATCH", body: { status } }),
    onSuccess: invalidate,
  });
}

export function useReissueCode(kind: "students" | "instructors" | "admins") {
  return useMutation({
    mutationFn: (id: string) =>
      apiRequest<{ activationCode: string }>(`/api/v1/${kind}/${id}/activation-code`, { method: "POST" }),
  });
}

export function useDeletePerson(kind: "students" | "admins") {
  const invalidate = useInvalidatePeople();
  return useMutation({
    mutationFn: (id: string) => apiRequest<void>(`/api/v1/${kind}/${id}`, { method: "DELETE" }),
    onSuccess: invalidate,
  });
}

export function useAdmins(page: number) {
  return useQuery({
    queryKey: ["staffPeople", "admins", page],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<StudentRow>>(`/api/v1/admins${queryString({ page, size: 20 })}`, { signal }),
    staleTime: 0,
  });
}

export function useCreateAdmin() {
  const invalidate = useInvalidatePeople();
  return useMutation({
    mutationFn: (body: NewPerson) => apiRequest<CreatedAccount>("/api/v1/admins", { method: "POST", body }),
    onSuccess: invalidate,
  });
}

export function useInstructors(filters: InstructorFilters) {
  return useQuery({
    queryKey: ["staffPeople", "instructors", filters],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<InstructorRow>>(
        `/api/v1/instructors${queryString({
          page: filters.page,
          size: filters.size,
          q: filters.q,
          status: filters.status,
          assigned: filters.assigned,
        })}`,
        { signal },
      ),
    staleTime: 0,
  });
}

export function useCreateInstructor() {
  const invalidate = useInvalidatePeople();
  return useMutation({
    mutationFn: (body: NewPerson) =>
      apiRequest<CreatedAccount>("/api/v1/instructors", { method: "POST", body }),
    onSuccess: invalidate,
  });
}

/* ---------- participation ---------- */

/** A module as the module filters list it. Instructors see their assigned modules only. */
export type ModuleOption = { id: string; titleEn: string; titleBn: string };

export function useModuleOptions() {
  return useQuery({
    queryKey: ["staffPeople", "moduleOptions"],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<ModuleOption>>("/api/v1/live-exam-modules?size=100", { signal }),
    staleTime: 60_000,
  });
}

export type ParticipationRow = {
  examId: string;
  moduleId: string;
  moduleTitleEn: string;
  moduleTitleBn: string;
  /** The exam's own category (API: categoryId, categoryTitleEn, categoryTitleBn). */
  categoryId?: string | null;
  categoryTitleEn?: string | null;
  categoryTitleBn?: string | null;
  examType: ExamType;
  titleEn: string;
  titleBn: string;
  liveAt: string | null;
  endAt: string | null;
  sitting: number;
  submitted: number;
  failedToSubmit: number;
  /** Written exams only. Null for an MCQ, which staff do not mark. */
  awaitingMarking: number | null;
  draftsAwaitingPublish: number | null;
  /** Corrections to released results that wait for release (part of `draftsAwaitingPublish`). */
  correctionsWaiting?: number | null;
  published: number | null;
  retakes: number;
};

export type ParticipationFilters = {
  page: number;
  size: number;
  moduleId: string;
  type: string;
  from: string;
  to: string;
  q: string;
  sort: string;
  /** Empty for every category (API: categoryId). */
  categoryId?: string;
};

export type ParticipationSummary = { awaitingMarking: number; draftsAwaitingPublish: number };

export function useParticipation(filters: ParticipationFilters) {
  return useQuery({
    queryKey: ["staffPeople", "participation", filters],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<ParticipationRow>>(
        `/api/v1/live-exam-participation${queryString({ ...filters })}`,
        { signal },
      ),
    staleTime: 0,
  });
}

export function useParticipationSummary() {
  return useQuery({
    queryKey: ["staffPeople", "participationSummary"],
    queryFn: ({ signal }) =>
      apiRequest<ParticipationSummary>("/api/v1/live-exam-participation/summary", { signal }),
    staleTime: 0,
  });
}

/* ---------- submissions and marking ---------- */

/** One participant row. The API sends no comment on read, so the comment field starts empty. */
export type SubmissionRow = {
  id: string;
  examId: string;
  studentId: string;
  studentName: string;
  status: "SUBMITTED" | "FAILED_TO_SUBMIT" | "IN_PROGRESS";
  score: number | null;
  maximum: number;
  passed: boolean | null;
  kind: "LIVE" | "RETAKE";
  answerPdf: boolean;
  evaluatedPdf: boolean | null;
  evaluatorName: string | null;
  published: boolean;
};

/** A module's written rows. Disabled while there is no module id, so no request goes to a bare path. */
export function useModuleSubmissions(moduleId: string) {
  return useQuery({
    queryKey: ["staffPeople", "submissions", moduleId],
    queryFn: ({ signal }) =>
      apiRequest<SubmissionRow[]>(`/api/v1/live-exam-modules/${moduleId}/submissions`, { signal }),
    enabled: moduleId.length > 0,
    staleTime: 0,
  });
}

export function useSubmission(participantId: string) {
  return useQuery({
    queryKey: ["staffPeople", "submission", participantId],
    queryFn: ({ signal }) =>
      apiRequest<SubmissionRow>(`/api/v1/live-exam-participants/${participantId}`, { signal }),
    staleTime: 0,
  });
}

export function answerPdfBlob(participantId: string): Promise<Blob> {
  return apiBlob(`/api/v1/live-exam-participants/${participantId}/answer-pdf`);
}

export function useSaveDraft(participantId: string) {
  const invalidate = useInvalidatePeople();
  return useMutation({
    mutationFn: ({ marks, comment, file }: { marks: number; comment: string; file: File }) => {
      const form = new FormData();
      form.append("marks", String(marks));
      if (comment.trim()) {
        form.append("comment", comment.trim());
      }
      form.append("file", file);
      return apiUpload<SubmissionRow>(`/api/v1/live-exam-participants/${participantId}/evaluation`, form);
    },
    onSuccess: invalidate,
  });
}

export function usePublishResult(participantId: string) {
  const invalidate = useInvalidatePeople();
  return useMutation({
    mutationFn: () =>
      apiRequest<SubmissionRow>(`/api/v1/live-exam-participants/${participantId}/publish`, { method: "POST" }),
    onSuccess: invalidate,
  });
}
