import { apiBaseUrl } from "@/lib/config";
import { ApiError } from "@/lib/api/errors";
import type { ApiErrorBody } from "@/lib/api/types";
import { getAccessToken, setAccessToken } from "@/lib/auth/token-store";

type RequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
  skipAuthRetry?: boolean;
};

let sessionLostHandler: (() => void) | null = null;

export function onSessionLost(handler: (() => void) | null): void {
  sessionLostHandler = handler;
}

function isAuthRoute(path: string): boolean {
  return path.startsWith("/api/v1/auth");
}

async function parseError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ApiErrorBody;
    if (body.code) {
      return new ApiError(body);
    }
  } catch {
    /* fall through */
  }
  return new ApiError({
    code: "INTERNAL_ERROR",
    message: response.statusText || "Request failed",
    timestamp: new Date().toISOString(),
    requestId: "",
  });
}

async function rawFetch(path: string, options: RequestOptions = {}): Promise<Response> {
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && options.body !== undefined) {
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
        : typeof options.body === "string"
          ? options.body
          : JSON.stringify(options.body),
  };

  return fetch(`${apiBaseUrl}${path}`, init);
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
};
