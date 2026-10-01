import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/**
 * Optimistic routing only. Session restore uses the API refresh cookie from the
 * browser client; this proxy cannot read that HttpOnly cookie on the API origin.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  if (pathname === "/") {
    return NextResponse.redirect(new URL("/students", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/"],
};
