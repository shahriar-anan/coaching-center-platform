import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { requestHost, routeForHost, runtimeHostConfig } from "@/lib/url/host-routing";

/**
 * Host routing for the two surfaces (see docs/exam-site/plans/ui-delivery/ws0-foundation.md, section 9).
 * With one host this does nothing. Session restore uses the API refresh cookie
 * from the browser client; this proxy cannot read that HttpOnly cookie.
 */
export function proxy(request: NextRequest) {
  const decision = routeForHost({
    host: requestHost(request.headers),
    pathname: request.nextUrl.pathname,
    search: request.nextUrl.search,
    config: runtimeHostConfig(),
  });
  if (decision.action === "redirect") {
    return NextResponse.redirect(decision.location);
  }
  return NextResponse.next();
}

export const config = {
  // Skip Next internals, the API and files with an extension (images, fonts, PDFs).
  matcher: ["/((?!api/|_next/|favicon\\.ico$|.*\\..*).*)"],
};
