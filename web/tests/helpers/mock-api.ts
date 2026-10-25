import { vi } from "vitest";

export type RouteBody = unknown;
export type RouteHandler = (req: { url: URL; method: string; body: unknown }) => RouteBody | Promise<RouteBody>;

/**
 * Maps "METHOD /path" to a JSON body, or to a function that returns one. Query
 * strings are ignored when matching. Unknown routes answer 404 in the API's
 * error shape, so screens that handle a missing endpoint can be tested.
 */
export function mockApi(routes: Record<string, RouteBody | RouteHandler>) {
  const calls: { method: string; path: string; search: string; body: unknown }[] = [];

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(typeof input === "string" ? input : input.toString(), "http://localhost");
    const method = (init?.method ?? "GET").toUpperCase();
    const key = `${method} ${url.pathname}`;
    const body = typeof init?.body === "string" ? JSON.parse(init.body) : init?.body;
    calls.push({ method, path: url.pathname, search: url.search, body });

    if (!(key in routes)) {
      return jsonResponse(404, {
        code: "RESOURCE_NOT_FOUND",
        message: "Resource not found.",
        timestamp: new Date().toISOString(),
        requestId: "test",
      });
    }
    const handler = routes[key];
    const payload =
      typeof handler === "function" ? await (handler as RouteHandler)({ url, method, body }) : handler;
    if (payload instanceof ErrorBody) {
      return jsonResponse(payload.status, payload.body);
    }
    return jsonResponse(200, payload);
  });

  vi.stubGlobal("fetch", fetchMock);
  return { fetch: fetchMock, calls };
}

/** Return this from a handler to answer with an error in the API's shape. */
export class ErrorBody {
  constructor(
    readonly status: number,
    readonly body: { code: string; message: string },
  ) {}
}

export function apiError(status: number, code: string, message = code): ErrorBody {
  return new ErrorBody(status, {
    code,
    message,
    timestamp: new Date().toISOString(),
    requestId: "test",
  } as { code: string; message: string });
}

function jsonResponse(status: number, body: unknown): Response {
  const text = body === undefined ? "" : JSON.stringify(body);
  return new Response(text, {
    status,
    headers: { "Content-Type": "application/json", Date: new Date().toUTCString() },
  });
}
