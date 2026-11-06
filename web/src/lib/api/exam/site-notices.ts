"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";

// The public notice board (refunds, stats and notice board plan, section 6). Paths and field names follow the API contract.
// Staff reads and writes are signed in; the public reads need no sign-in. Text is plain: it is shown as text, never as HTML.

export type SiteNoticeStatus = "DRAFT" | "PUBLISHED" | "UNPUBLISHED";

/** The four texts of a notice. A blank text is null. */
export type NoticeTexts = {
  titleEn: string | null;
  titleBn: string | null;
  bodyEn: string | null;
  bodyBn: string | null;
};

/**
 * A photo of a notice: its id, and the path that serves it. The path carries a version, so it may be cached. The staff
 * path needs the sign-in; the public path does not.
 */
export type NoticePhoto = {
  id: string;
  url: string;
};

/** The PDF of a notice: the path that serves it, its name and its size in bytes. */
export type NoticePdf = {
  url: string;
  fileName: string;
  byteSize: number;
};

/** One notice in the staff list and editor. `photos` and `pdf` are optional so that an older API still works. */
export type StaffNotice = NoticeTexts & {
  id: string;
  status: SiteNoticeStatus;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  photos?: NoticePhoto[];
  pdf?: NoticePdf | null;
};

/** A published notice as the public sees it. */
export type PublicNotice = NoticeTexts & {
  id: string;
  publishedAt: string;
  photos?: NoticePhoto[];
  pdf?: NoticePdf | null;
};

/** The most photos one notice may carry. */
export const NOTICE_PHOTO_MAX = 6;

/** The size of a public page. The API fixes it at ten. */
export const PUBLIC_NOTICE_PAGE = 10;

/** The staff list page size. */
export const STAFF_NOTICE_PAGE = 20;

export const siteNoticeKeys = {
  all: ["siteNotices"] as const,
  staffList: (status: SiteNoticeStatus | null, page: number) => ["siteNotices", "staff", "list", status ?? "ALL", page] as const,
  publicList: (page: number) => ["siteNotices", "public", "list", page] as const,
  publicOne: (id: string) => ["siteNotices", "public", "one", id] as const,
};

/** One page of notices for staff, most recently changed first. `null` status is every notice. */
export function useStaffNotices(status: SiteNoticeStatus | null, page: number) {
  const query = new URLSearchParams({ page: String(page), size: String(STAFF_NOTICE_PAGE) });
  if (status) {
    query.set("status", status);
  }
  return useQuery({
    queryKey: siteNoticeKeys.staffList(status, page),
    queryFn: ({ signal }) => apiRequest<PageResponse<StaffNotice>>(`/api/v1/site-notices?${query}`, { signal }),
  });
}

/** Creates a draft (no `id`) or saves the four texts of an existing notice. */
export function useSaveNotice() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, texts }: { id?: string; texts: NoticeTexts }) =>
      id
        ? apiRequest<StaffNotice>(`/api/v1/site-notices/${id}`, { method: "PUT", body: texts })
        : apiRequest<StaffNotice>("/api/v1/site-notices", { method: "POST", body: texts }),
    retry: false,
    onSettled: () => invalidateNotices(client),
  });
}

export function usePublishNotice() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequest<StaffNotice>(`/api/v1/site-notices/${id}/publish`, { method: "POST", body: {} }),
    retry: false,
    onSettled: () => invalidateNotices(client),
  });
}

export function useUnpublishNotice() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequest<StaffNotice>(`/api/v1/site-notices/${id}/unpublish`, { method: "POST", body: {} }),
    retry: false,
    onSettled: () => invalidateNotices(client),
  });
}

export function useDeleteNotice() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequest<void>(`/api/v1/site-notices/${id}`, { method: "DELETE" }),
    retry: false,
    onSettled: () => invalidateNotices(client),
  });
}

/** Adds a photo after the existing ones. The server re-encodes it. */
export function useAddNoticePhoto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => {
      const body = new FormData();
      body.append("file", file);
      return apiRequest<StaffNotice>(`/api/v1/site-notices/${id}/photos`, { method: "POST", body });
    },
    retry: false,
    onSettled: () => invalidateNotices(client),
  });
}

export function useRemoveNoticePhoto() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, fileId }: { id: string; fileId: string }) =>
      apiRequest<StaffNotice>(`/api/v1/site-notices/${id}/photos/${fileId}`, { method: "DELETE" }),
    retry: false,
    onSettled: () => invalidateNotices(client),
  });
}

/** Puts the photos in this order. `ids` must be exactly the notice's current photo ids. */
export function useReorderNoticePhotos() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ids }: { id: string; ids: string[] }) =>
      apiRequest<StaffNotice>(`/api/v1/site-notices/${id}/photos/order`, { method: "PUT", body: { ids } }),
    retry: false,
    onSettled: () => invalidateNotices(client),
  });
}

/** Sets the notice's PDF, replacing any earlier one. */
export function usePutNoticePdf() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => {
      const body = new FormData();
      body.append("file", file);
      return apiRequest<StaffNotice>(`/api/v1/site-notices/${id}/pdf`, { method: "PUT", body });
    },
    retry: false,
    onSettled: () => invalidateNotices(client),
  });
}

export function useRemoveNoticePdf() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiRequest<StaffNotice>(`/api/v1/site-notices/${id}/pdf`, { method: "DELETE" }),
    retry: false,
    onSettled: () => invalidateNotices(client),
  });
}

/** One page of published notices for the public board. */
export function usePublicNotices(page: number) {
  return useQuery({
    queryKey: siteNoticeKeys.publicList(page),
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<PublicNotice>>(`/api/v1/public/site-notices?page=${page}`, { signal }),
  });
}

/** One published notice. A notice that is not published is not found. */
export function usePublicNotice(id: string) {
  return useQuery({
    queryKey: siteNoticeKeys.publicOne(id),
    queryFn: ({ signal }) => apiRequest<PublicNotice>(`/api/v1/public/site-notices/${id}`, { signal }),
    enabled: Boolean(id),
    retry: false,
  });
}

/** A write changes both the staff lists and what the public sees. */
function invalidateNotices(client: ReturnType<typeof useQueryClient>) {
  return client.invalidateQueries({ queryKey: siteNoticeKeys.all });
}
