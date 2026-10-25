// Local stand-in for WS0's renderWithProviders and mockApi. Replace after WS0 merges.
import type { ReactElement } from "react";
import { render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { vi } from "vitest";
import { LocaleProvider, type Locale } from "@/components/student/sitting/_tmp/locale";

export function renderWs4(ui: ReactElement, options: { locale?: Locale } = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 0 }, mutations: { retry: false } },
  });
  return {
    client,
    ...render(
      <QueryClientProvider client={client}>
        <LocaleProvider initial={options.locale ?? "bn"}>{ui}</LocaleProvider>
      </QueryClientProvider>,
    ),
  };
}

type Handler = unknown | ((request: { url: string; body: unknown }) => unknown);

/** Mocks `fetch` by "METHOD /path" (query string ignored). A handler may return `{ status, body }` via `reply`. */
export function mockApi(routes: Record<string, Handler>) {
  const calls: { method: string; path: string; body: unknown }[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    const method = (init?.method ?? "GET").toUpperCase();
    const body = typeof init?.body === "string" ? JSON.parse(init.body) : init?.body;
    calls.push({ method, path: url.pathname, body });
    const handler = routes[`${method} ${url.pathname}`];
    if (handler === undefined) {
      return json(404, { code: "RESOURCE_NOT_FOUND", message: "not mocked", timestamp: "", requestId: "" });
    }
    const value = typeof handler === "function" ? await handler({ url: url.pathname, body }) : handler;
    if (value instanceof Reply) {
      return json(value.status, value.body);
    }
    if (value instanceof Blob) {
      return new Response(value, { status: 200 });
    }
    return json(200, value);
  });
  vi.stubGlobal("fetch", fetchMock);
  return { calls, fetchMock };
}

class Reply {
  constructor(
    readonly status: number,
    readonly body: unknown,
  ) {}
}

export function reply(status: number, body: unknown = undefined) {
  return new Reply(status, body);
}

export function apiError(status: number, code: string) {
  return reply(status, { code, message: code, timestamp: "", requestId: "" });
}

function json(status: number, body: unknown) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
