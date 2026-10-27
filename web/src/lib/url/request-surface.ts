import { headers } from "next/headers";
import { isStaffHost, requestHost, runtimeHostConfig } from "@/lib/url/host-routing";
import type { Surface } from "@/lib/url/surface-context";

/** The surface the current request is for, read from the host header (the proxy sets x-forwarded-host). */
export async function requestSurface(): Promise<Surface> {
  const list = await headers();
  return isStaffHost(requestHost(list), runtimeHostConfig()) ? "staff" : "site";
}
