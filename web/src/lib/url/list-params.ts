"use client";

import { useCallback, useMemo } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

export type ListParamValue = string | number | boolean | null | undefined;

export type ListParams = Record<string, ListParamValue>;

/** Values read back from the URL keep the type of their default. */
export type ResolvedListParams<T extends ListParams> = {
  [K in keyof T]: T[K] extends number ? number : T[K] extends boolean ? boolean : string;
};

function parse(raw: string | null, fallback: ListParamValue): ListParamValue {
  if (raw === null) {
    return fallback ?? "";
  }
  if (typeof fallback === "number") {
    const n = Number(raw);
    return Number.isFinite(n) ? n : fallback;
  }
  if (typeof fallback === "boolean") {
    return raw === "true";
  }
  return raw;
}

/**
 * Keeps a list's page, search and filters in the URL query, so Back and
 * refresh return to the same view. Values equal to their default are left out
 * of the URL. Changing anything but `page` resets `page` to its default.
 *
 * ```ts
 * const [params, setParams] = useListParams({ page: 0, size: 20, q: "", status: "" });
 * setParams({ q: "rahim" });
 * ```
 */
export function useListParams<T extends ListParams>(
  defaults: T,
): [ResolvedListParams<T>, (next: Partial<{ [K in keyof T]: ListParamValue }>) => void] {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const defaultsKey = JSON.stringify(defaults);

  const params = useMemo(() => {
    const base = JSON.parse(defaultsKey) as T;
    const out: Record<string, ListParamValue> = {};
    for (const [key, fallback] of Object.entries(base)) {
      out[key] = parse(searchParams?.get(key) ?? null, fallback);
    }
    return out as ResolvedListParams<T>;
  }, [defaultsKey, searchParams]);

  const setParams = useCallback(
    (next: Partial<{ [K in keyof T]: ListParamValue }>) => {
      const base = JSON.parse(defaultsKey) as T;
      const query = new URLSearchParams(searchParams?.toString() ?? "");
      const merged: Record<string, ListParamValue> = { ...params, ...next };
      if (!("page" in next) && "page" in base) {
        merged.page = base.page;
      }
      for (const [key, value] of Object.entries(merged)) {
        const fallback = key in base ? base[key] : undefined;
        if (value === null || value === undefined || value === "" || value === fallback) {
          query.delete(key);
        } else {
          query.set(key, String(value));
        }
      }
      const search = query.toString();
      router.replace(`${pathname ?? ""}${search ? `?${search}` : ""}`, { scroll: false });
    },
    [defaultsKey, params, pathname, router, searchParams],
  );

  return [params, setParams];
}
