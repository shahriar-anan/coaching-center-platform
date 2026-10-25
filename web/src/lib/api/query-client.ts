import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/errors";

/**
 * Defaults for every exam page. Areas override per query:
 * catalog reads keep `staleTime` at 60 s; sitting and payment status set it to 0;
 * the dashboard, notices and sitting set `refetchOnWindowFocus: true`.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          // A 4xx answer will not change on retry.
          if (error instanceof ApiError && error.code !== "INTERNAL_ERROR") {
            return false;
          }
          return failureCount < 1;
        },
      },
      mutations: {
        retry: false,
      },
    },
  });
}

let browserClient: QueryClient | null = null;

/** The one client in the browser; a fresh one per server render, so nothing is shared between requests. */
export function getQueryClient(): QueryClient {
  if (typeof window === "undefined") {
    return createQueryClient();
  }
  browserClient ??= createQueryClient();
  return browserClient;
}
