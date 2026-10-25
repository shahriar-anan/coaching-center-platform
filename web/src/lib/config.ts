/**
 * Hosts and URLs for the two surfaces of this app: the student site and the
 * admin panel. On staging they share one host (staff under `/staff`); at launch
 * they get separate origins. Every link or redirect between the two surfaces
 * goes through `siteHref` and `staffHref`, so only environment values change.
 *
 * The `NEXT_PUBLIC_*` values are inlined at build time, so the browser bundle
 * is built per environment. `proxy.ts` also reads `SITE_URL` and `STAFF_URL`
 * at runtime (see `hostConfig`).
 */

export type HostConfig = {
  siteUrl: string;
  staffUrl: string;
  splitHosts: boolean;
};

function trimUrl(value: string | undefined): string {
  return (value ?? "").trim().replace(/\/+$/, "");
}

function originOf(url: string): string {
  if (!url) {
    return "";
  }
  try {
    return new URL(url).origin;
  } catch {
    return "";
  }
}

export function hostConfig(env: {
  siteUrl?: string;
  staffUrl?: string;
}): HostConfig {
  const siteUrl = trimUrl(env.siteUrl);
  const staffUrl = trimUrl(env.staffUrl) || siteUrl;
  const splitHosts =
    Boolean(siteUrl && staffUrl) && originOf(siteUrl) !== originOf(staffUrl);
  return { siteUrl, staffUrl, splitHosts };
}

/** Empty means same origin: the browser calls `/api/...` on its own host. */
export const apiBaseUrl = trimUrl(process.env.NEXT_PUBLIC_API_BASE_URL);

export function apiUrl(path: string): string {
  return `${apiBaseUrl}${path.startsWith("/") ? path : `/${path}`}`;
}

const hosts = hostConfig({
  siteUrl: process.env.NEXT_PUBLIC_SITE_URL,
  staffUrl: process.env.NEXT_PUBLIC_STAFF_URL,
});

export const siteUrl = hosts.siteUrl;
export const staffUrl = hosts.staffUrl;
export const splitHosts = hosts.splitHosts;

export const googleEnabled = process.env.NEXT_PUBLIC_GOOGLE_ENABLED === "true";

/** One payment for several exams (plan decision D1). Off until the API has bundle orders. */
export const bundleOrders = process.env.NEXT_PUBLIC_BUNDLE_ORDERS === "true";

function currentOrigin(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  return window.location.origin;
}

function normalizePath(path: string): string {
  return path.startsWith("/") ? path : `/${path}`;
}

/**
 * Builds a link to `path` on the surface whose origin is `target`: a relative
 * path when the hosts are not split or the visitor is already there, otherwise
 * the absolute URL.
 */
export function hrefOn(
  config: HostConfig,
  target: "site" | "staff",
  path: string,
  origin: string | null = currentOrigin(),
): string {
  const relative = normalizePath(path);
  const base = target === "site" ? config.siteUrl : config.staffUrl;
  if (!config.splitHosts || !base) {
    return relative;
  }
  if (origin && origin === originOf(base)) {
    return relative;
  }
  return `${base}${relative}`;
}

export function siteHref(path: string): string {
  return hrefOn(hosts, "site", path);
}

export function staffHref(path: string): string {
  return hrefOn(hosts, "staff", path);
}

/** True when the visitor is on the admin host of a split setup. */
export function onStaffHost(origin: string | null = currentOrigin()): boolean {
  return Boolean(
    hosts.splitHosts && origin && origin === originOf(hosts.staffUrl),
  );
}

/** True when the visitor is on the student host of a split setup. */
export function onSiteHost(origin: string | null = currentOrigin()): boolean {
  return Boolean(
    hosts.splitHosts && origin && origin === originOf(hosts.siteUrl),
  );
}

export const studentHome = siteHref("/");
export const staffHome = staffHref("/staff");
export const studentLogin = siteHref("/login");
export const staffLogin = staffHref("/login");
