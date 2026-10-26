import { apiBaseUrl, apiUrl } from "@/lib/config";
import { ApiError } from "@/lib/api/errors";
import type { ApiErrorBody, CourseNodeView } from "@/lib/api/types";
import { getAccessToken, setAccessToken } from "@/lib/auth/token-store";
import { recordServerDate } from "@/lib/time/clock";

type RequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
  skipAuthRetry?: boolean;
};

let sessionLostHandler: (() => void) | null = null;

export function onSessionLost(handler: (() => void) | null): void {
  sessionLostHandler = handler;
}

function courseNode(node: CourseNodeView): CourseNodeView {
  return { ...node, parentId: node.parentId ?? null };
}

function isAuthRoute(path: string): boolean {
  return path.startsWith("/api/v1/auth");
}

async function parseError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorBody;
    if (body.code) {
      return new ApiError(body, response.status);
    }
  } catch {
    /* fall through */
  }
  return new ApiError(
    {
      code: "INTERNAL_ERROR",
      message: response.statusText || "Request failed",
      timestamp: new Date().toISOString(),
      requestId: "",
    },
    response.status,
  );
}

async function rawFetch(path: string, options: RequestOptions = {}): Promise<Response> {
  const headers = new Headers(options.headers);
  if (
    !headers.has("Content-Type") &&
    options.body !== undefined &&
    !(options.body instanceof FormData)
  ) {
    headers.set("Content-Type", "application/json");
  }
  if (isAuthRoute(path)) {
    headers.set("X-Client-Type", "web");
  }
  const token = getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const init: RequestInit = {
    ...options,
    headers,
    credentials: isAuthRoute(path) ? "include" : "same-origin",
    body:
      options.body === undefined
        ? undefined
        : options.body instanceof FormData
          ? options.body
          : typeof options.body === "string"
            ? options.body
            : JSON.stringify(options.body),
  };

  const response = await fetch(apiUrl(path), init);
  recordServerDate(response.headers?.get("Date"));
  return response;
}

async function refreshAccessToken(): Promise<boolean> {
  const response = await rawFetch("/api/v1/auth/refresh", {
    method: "POST",
    body: {},
    skipAuthRetry: true,
  });
  if (!response.ok) {
    setAccessToken(null);
    return false;
  }
  const data = (await response.json()) as { accessToken: string };
  setAccessToken(data.accessToken);
  return true;
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  let response = await rawFetch(path, options);

  if (
    response.status === 401 &&
    !options.skipAuthRetry &&
    !isAuthRoute(path) &&
    getAccessToken()
  ) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      response = await rawFetch(path, { ...options, skipAuthRetry: true });
    }
  }

  if (response.status === 401) {
    setAccessToken(null);
    sessionLostHandler?.();
    throw await parseError(response);
  }

  if (!response.ok) {
    throw await parseError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

/** Fetches an authorized file, such as a question or answer PDF, as a `Blob`. */
export async function apiBlob(path: string, options: { signal?: AbortSignal } = {}): Promise<Blob> {
  let response = await rawFetch(path, { signal: options.signal });
  if (response.status === 401 && getAccessToken()) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      response = await rawFetch(path, { signal: options.signal });
    }
  }
  if (response.status === 401) {
    setAccessToken(null);
    sessionLostHandler?.();
  }
  if (!response.ok) {
    throw await parseError(response);
  }
  return response.blob();
}

type UploadOptions = {
  method?: "POST" | "PUT";
  /** Called with 0–100 while the body is sent. */
  onProgress?: (percent: number) => void;
  signal?: AbortSignal;
};

type UploadResult = { status: number; text: string; date: string | null };

function sendUpload(path: string, formData: FormData, options: UploadOptions): Promise<UploadResult> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(options.method ?? "POST", apiUrl(path));
    xhr.withCredentials = false;
    const token = getAccessToken();
    if (token) {
      xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    }
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        options.onProgress?.(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () =>
      resolve({ status: xhr.status, text: xhr.responseText, date: xhr.getResponseHeader("Date") });
    xhr.onerror = () => reject(new TypeError("Network request failed"));
    xhr.onabort = () => reject(new DOMException("Aborted", "AbortError"));
    options.signal?.addEventListener("abort", () => xhr.abort());
    xhr.send(formData);
  });
}

function uploadError(result: UploadResult): ApiError {
  try {
    const body = JSON.parse(result.text) as ApiErrorBody;
    if (body.code) {
      return new ApiError(body, result.status);
    }
  } catch {
    /* fall through */
  }
  return new ApiError(
    {
      code: result.status === 413 ? "FILE_TOO_LARGE" : "INTERNAL_ERROR",
      message: "Upload failed",
      timestamp: new Date().toISOString(),
      requestId: "",
    },
    result.status,
  );
}

/**
 * Sends a multipart body with upload progress (XHR, because `fetch` has none),
 * with the same token and one refresh retry as `apiRequest`.
 */
export async function apiUpload<T>(
  path: string,
  formData: FormData,
  options: UploadOptions = {},
): Promise<T> {
  let result = await sendUpload(path, formData, options);
  if (result.status === 401 && getAccessToken()) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      result = await sendUpload(path, formData, options);
    }
  }
  recordServerDate(result.date);
  if (result.status === 401) {
    setAccessToken(null);
    sessionLostHandler?.();
  }
  if (result.status < 200 || result.status >= 300) {
    throw uploadError(result);
  }
  options.onProgress?.(100);
  return (result.text ? JSON.parse(result.text) : undefined) as T;
}

export const api = {
  activate(phone: string, code: string, password: string) {
    return apiRequest<void>("/api/v1/auth/activate", {
      method: "POST",
      body: { phone, code, password },
      skipAuthRetry: true,
    });
  },

  login(identifier: string, password: string) {
    return apiRequest<import("@/lib/api/types").TokenResponse>("/api/v1/auth/login", {
      method: "POST",
      body: { identifier, password },
      skipAuthRetry: true,
    });
  },

  refresh() {
    return apiRequest<import("@/lib/api/types").TokenResponse>("/api/v1/auth/refresh", {
      method: "POST",
      body: {},
      skipAuthRetry: true,
    });
  },

  logout() {
    return apiRequest<void>("/api/v1/auth/logout", {
      method: "POST",
      body: {},
    });
  },

  register(body: { fullName: string; phone: string; email: string; password: string }) {
    return apiRequest<import("@/lib/api/types").MeView>("/api/v1/auth/register", {
      method: "POST",
      body,
      skipAuthRetry: true,
    });
  },

  forgotPassword(email: string) {
    return apiRequest<{ message: string }>("/api/v1/auth/forgot-password", {
      method: "POST",
      body: { email },
      skipAuthRetry: true,
    });
  },

  resetPassword(code: string, password: string) {
    return apiRequest<void>("/api/v1/auth/reset-password", {
      method: "POST",
      body: { code, password },
      skipAuthRetry: true,
    });
  },

  resendVerification(email: string) {
    return apiRequest<{ message: string }>("/api/v1/auth/verify-email/resend", {
      method: "POST",
      body: { email },
      skipAuthRetry: true,
    });
  },

  changePassword(currentPassword: string, newPassword: string) {
    return apiRequest<void>("/api/v1/auth/change-password", {
      method: "POST",
      body: { currentPassword, newPassword },
    });
  },

  me() {
    return apiRequest<import("@/lib/api/types").MeView>("/api/v1/me");
  },

  listAdmins(page: number, size = 20) {
    return apiRequest<import("@/lib/api/types").PageResponse<import("@/lib/api/types").AccountView>>(
      `/api/v1/admins?page=${page}&size=${size}`,
    );
  },

  createAdmin(body: import("@/lib/api/types").CreateAccountRequest) {
    return apiRequest<import("@/lib/api/types").CreatedAccount>("/api/v1/admins", {
      method: "POST",
      body,
    });
  },

  changeAdminStatus(id: string, status: "ACTIVE" | "INACTIVE") {
    return apiRequest<void>(`/api/v1/admins/${id}/status`, {
      method: "PATCH",
      body: { status },
    });
  },

  reissueAdminCode(id: string) {
    return apiRequest<import("@/lib/api/types").ActivationCodeResponse>(
      `/api/v1/admins/${id}/activation-code`,
      { method: "POST", body: {} },
    );
  },

  deleteAdmin(id: string) {
    return apiRequest<void>(`/api/v1/admins/${id}`, { method: "DELETE" });
  },

  listStudents(q: string, page: number, size = 20) {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (q.trim()) {
      params.set("q", q.trim());
    }
    return apiRequest<import("@/lib/api/types").PageResponse<import("@/lib/api/types").AccountView>>(
      `/api/v1/students?${params}`,
    );
  },

  createStudent(body: import("@/lib/api/types").CreateAccountRequest) {
    return apiRequest<import("@/lib/api/types").CreatedAccount>("/api/v1/students", {
      method: "POST",
      body,
    });
  },

  reissueStudentCode(id: string) {
    return apiRequest<import("@/lib/api/types").ActivationCodeResponse>(
      `/api/v1/students/${id}/activation-code`,
      { method: "POST", body: {} },
    );
  },

  deleteStudent(id: string) {
    return apiRequest<void>(`/api/v1/students/${id}`, { method: "DELETE" });
  },

  listCourses(page: number, size = 20) {
    return apiRequest<import("@/lib/api/types").PageResponse<import("@/lib/api/types").CourseView>>(
      `/api/v1/courses?page=${page}&size=${size}`,
    );
  },

  getCourse(id: string) {
    return apiRequest<import("@/lib/api/types").CourseView>(`/api/v1/courses/${id}`);
  },

  createCourse(body: Record<string, unknown>) {
    return apiRequest<import("@/lib/api/types").CourseView>("/api/v1/courses", {
      method: "POST",
      body,
    });
  },

  updateCourse(id: string, body: Record<string, unknown>) {
    return apiRequest<import("@/lib/api/types").CourseView>(`/api/v1/courses/${id}`, {
      method: "PATCH",
      body,
    });
  },

  archiveCourse(id: string) {
    return apiRequest<void>(`/api/v1/courses/${id}/archive`, { method: "POST", body: {} });
  },

  deleteCourse(id: string) {
    return apiRequest<void>(`/api/v1/courses/${id}`, { method: "DELETE" });
  },

  closeCourse(id: string) {
    return apiRequest<import("@/lib/api/types").CourseView>(`/api/v1/courses/${id}/close`, {
      method: "POST",
      body: {},
    });
  },

  reopenCourse(id: string) {
    return apiRequest<import("@/lib/api/types").CourseView>(`/api/v1/courses/${id}/reopen`, {
      method: "POST",
      body: {},
    });
  },

  listCourseNodes(courseId: string) {
    return apiRequest<CourseNodeView[]>(`/api/v1/courses/${courseId}/nodes`).then((nodes) =>
      nodes.map(courseNode),
    );
  },

  createCourseNode(courseId: string, body: Record<string, unknown>) {
    return apiRequest<CourseNodeView>(`/api/v1/courses/${courseId}/nodes`, {
      method: "POST",
      body,
    }).then(courseNode);
  },

  updateCourseNode(courseId: string, nodeId: string, body: Record<string, unknown>) {
    return apiRequest<CourseNodeView>(`/api/v1/courses/${courseId}/nodes/${nodeId}`, {
      method: "PATCH",
      body,
    }).then(courseNode);
  },

  reorderCourseNodes(courseId: string, body: { parentId?: string | null; nodeIds: string[] }) {
    return apiRequest<void>(`/api/v1/courses/${courseId}/nodes/reorder`, {
      method: "POST",
      body,
    });
  },

  deleteCourseNode(courseId: string, nodeId: string) {
    return apiRequest<void>(`/api/v1/courses/${courseId}/nodes/${nodeId}`, { method: "DELETE" });
  },

  listCourseMaterials(courseId: string) {
    return apiRequest<import("@/lib/api/types").MaterialView[]>(
      `/api/v1/courses/${courseId}/materials`,
    );
  },

  uploadCourseCover(courseId: string, file: File) {
    const form = new FormData();
    form.append("file", file);
    return apiRequest<import("@/lib/api/types").MaterialView>(
      `/api/v1/courses/${courseId}/cover`,
      { method: "POST", body: form },
    );
  },

  uploadCourseMaterial(
    courseId: string,
    file: File,
    options: { lectureNodeId?: string; free?: boolean } = {},
  ) {
    const form = new FormData();
    form.append("file", file);
    const params = new URLSearchParams();
    if (options.lectureNodeId) {
      params.set("lectureNodeId", options.lectureNodeId);
    }
    if (options.free) {
      params.set("free", "true");
    }
    const query = params.toString();
    const path = `/api/v1/courses/${courseId}/materials${query ? `?${query}` : ""}`;
    return apiRequest<import("@/lib/api/types").MaterialView>(path, { method: "POST", body: form });
  },

  updateCourseMaterialFree(courseId: string, materialId: string, free: boolean) {
    return apiRequest<import("@/lib/api/types").MaterialView>(
      `/api/v1/courses/${courseId}/materials/${materialId}`,
      { method: "PATCH", body: { free } },
    );
  },

  replaceCourseMaterial(courseId: string, materialId: string, file: File) {
    const form = new FormData();
    form.append("file", file);
    return apiRequest<import("@/lib/api/types").MaterialView>(
      `/api/v1/courses/${courseId}/materials/${materialId}`,
      { method: "PUT", body: form },
    );
  },

  deleteCourseMaterial(courseId: string, materialId: string) {
    return apiRequest<void>(`/api/v1/courses/${courseId}/materials/${materialId}`, {
      method: "DELETE",
    });
  },

  listCourseCategories() {
    return apiRequest<import("@/lib/api/types").CategoryTreeNode[]>("/api/v1/course-categories");
  },

  createCourseCategory(body: { titleEn: string; titleBn: string; parentId?: string }) {
    return apiRequest<import("@/lib/api/types").CategoryView>("/api/v1/course-categories", {
      method: "POST",
      body,
    });
  },

  deleteCourseCategory(id: string) {
    return apiRequest<void>(`/api/v1/course-categories/${id}`, { method: "DELETE" });
  },

  listBooks(page: number, size = 20) {
    return apiRequest<import("@/lib/api/types").PageResponse<import("@/lib/api/types").BookView>>(
      `/api/v1/books?page=${page}&size=${size}`,
    );
  },

  getBook(id: string) {
    return apiRequest<import("@/lib/api/types").BookView>(`/api/v1/books/${id}`);
  },

  createBook(body: Record<string, unknown>) {
    return apiRequest<import("@/lib/api/types").BookView>("/api/v1/books", {
      method: "POST",
      body,
    });
  },

  updateBook(id: string, body: Record<string, unknown>) {
    return apiRequest<import("@/lib/api/types").BookView>(`/api/v1/books/${id}`, {
      method: "PATCH",
      body,
    });
  },

  deleteBook(id: string) {
    return apiRequest<void>(`/api/v1/books/${id}`, { method: "DELETE" });
  },

  listBookCategories() {
    return apiRequest<import("@/lib/api/types").BookCategoryView[]>("/api/v1/book-categories");
  },

  createBookCategory(body: { titleEn: string; titleBn: string }) {
    return apiRequest<import("@/lib/api/types").BookCategoryView>("/api/v1/book-categories", {
      method: "POST",
      body,
    });
  },

  deleteBookCategory(id: string) {
    return apiRequest<void>(`/api/v1/book-categories/${id}`, { method: "DELETE" });
  },

  uploadBookCover(bookId: string, file: File) {
    const form = new FormData();
    form.append("file", file);
    return apiRequest<import("@/lib/api/types").BookView>(`/api/v1/books/${bookId}/cover`, {
      method: "POST",
      body: form,
    });
  },

  listPayments(
    status: "PENDING" | "SUCCESS" | "FAILED",
    payableType: "COURSE_ENROLLMENT" | "BOOK_ORDER",
    page = 0,
    size = 20,
  ) {
    return apiRequest<import("@/lib/api/types").PageResponse<import("@/lib/api/types").PaymentView>>(
      `/api/v1/payments?status=${status}&payableType=${payableType}&page=${page}&size=${size}`,
    );
  },

  verifyPayment(id: string, result: "SUCCESS" | "FAILED") {
    return apiRequest<import("@/lib/api/types").VerifyPaymentResult>(
      `/api/v1/payments/${id}/verify`,
      { method: "POST", body: { result } },
    );
  },

  admitStudent(courseId: string, body: { studentId: string; deliveryMode?: string }) {
    return apiRequest<{ enrollmentId: string; status: string }>(
      `/api/v1/courses/${courseId}/admissions`,
      { method: "POST", body },
    );
  },

  listBookOrders(page: number, size = 20) {
    return apiRequest<
      import("@/lib/api/types").PageResponse<import("@/lib/api/types").AdminBookOrderView>
    >(`/api/v1/book-orders?page=${page}&size=${size}`);
  },

  updateBookOrderStatus(orderId: string, status: string) {
    return apiRequest<{ orderId: string; status: string }>(`/api/v1/book-orders/${orderId}`, {
      method: "PATCH",
      body: { status },
    });
  },

  fileUrl(fileId: string) {
    return `${apiBaseUrl}/api/v1/files/${fileId}`;
  },
};
