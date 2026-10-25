// Stand-in for apiUpload and apiBlob in @/lib/api/client (WS0). Delete after WS0 merges.
import { apiBaseUrl } from "@/lib/config";
import { ApiError } from "@/lib/api/errors";
import { getAccessToken } from "@/lib/auth/token-store";

function failure(status: number, text: string): ApiError {
  try {
    const body = JSON.parse(text);
    if (body.code) {
      return new ApiError(body);
    }
  } catch {
    /* fall through */
  }
  return new ApiError({
    code: status === 413 ? "FILE_TOO_LARGE" : "INTERNAL_ERROR",
    message: "Request failed",
    timestamp: new Date().toISOString(),
    requestId: "",
  });
}

export async function apiBlob(path: string, options: { signal?: AbortSignal } = {}): Promise<Blob> {
  const token = getAccessToken();
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    signal: options.signal,
  });
  if (!response.ok) {
    throw failure(response.status, await response.text());
  }
  return response.blob();
}

export function apiUpload<T>(
  path: string,
  formData: FormData,
  options: { method?: "POST" | "PUT"; onProgress?: (percent: number) => void; signal?: AbortSignal } = {},
): Promise<T> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(options.method ?? "POST", `${apiBaseUrl}${path}`);
    const token = getAccessToken();
    if (token) {
      xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    }
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        options.onProgress?.(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () => {
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(failure(xhr.status, xhr.responseText));
        return;
      }
      options.onProgress?.(100);
      resolve((xhr.responseText ? JSON.parse(xhr.responseText) : undefined) as T);
    };
    xhr.onerror = () => reject(new TypeError("Network request failed"));
    xhr.onabort = () => reject(new DOMException("Aborted", "AbortError"));
    options.signal?.addEventListener("abort", () => xhr.abort());
    xhr.send(formData);
  });
}
