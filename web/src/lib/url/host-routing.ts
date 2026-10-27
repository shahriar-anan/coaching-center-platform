import { hostConfig, type HostConfig } from "@/lib/config";

/** Paths the admin panel serves itself. Everything else belongs to the student site. */
const ADMIN_PATHS = ["/staff", "/login", "/forgot-password", "/reset-password", "/activate"];

function matches(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

export type RouteDecision =
  | { action: "next" }
  | { action: "redirect"; location: string };

/** The host a request arrived on, from the forwarded header when Nginx sets it. */
export function requestHost(headers: { get(name: string): string | null }): string {
  const forwarded = headers.get("x-forwarded-host");
  return (forwarded ?? headers.get("host") ?? "").split(",")[0].trim().toLowerCase();
}

function hostOf(url: string): string {
  try {
    return new URL(url).host.toLowerCase();
  } catch {
    return "";
  }
}

/** True when the request arrived on the admin host of a split setup. */
export function isStaffHost(host: string, config: HostConfig): boolean {
  return config.splitHosts && host === hostOf(config.staffUrl);
}

/**
 * Decides what to do with a request when the two surfaces have separate hosts.
 * With one host (or no configuration) every request is served where it is.
 */
export function routeForHost(input: {
  host: string;
  pathname: string;
  search: string;
  config: HostConfig;
  protocol?: string;
}): RouteDecision {
  const { config, pathname, search } = input;
  if (!config.splitHosts) {
    return { action: "next" };
  }
  const onStaffHost = input.host === hostOf(config.staffUrl);
  const onSiteHost = input.host === hostOf(config.siteUrl);

  if (onStaffHost) {
    if (pathname === "/") {
      return { action: "redirect", location: `${config.staffUrl}/staff` };
    }
    if (ADMIN_PATHS.some((p) => matches(pathname, p))) {
      return { action: "next" };
    }
    return { action: "redirect", location: `${config.siteUrl}${pathname}${search}` };
  }

  if (onSiteHost && matches(pathname, "/staff")) {
    return { action: "redirect", location: `${config.staffUrl}${pathname}${search}` };
  }

  return { action: "next" };
}

/** Reads the host settings at request time, so one build can move between hosts. */
export function runtimeHostConfig(env: NodeJS.ProcessEnv = process.env): HostConfig {
  return hostConfig({
    siteUrl: env.SITE_URL ?? env.NEXT_PUBLIC_SITE_URL,
    staffUrl: env.STAFF_URL ?? env.NEXT_PUBLIC_STAFF_URL,
  });
}
