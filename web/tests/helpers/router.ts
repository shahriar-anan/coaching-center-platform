import { vi } from "vitest";

/**
 * A router the tests can read and reset. `tests/setup.ts` mocks `next/navigation`
 * with it, so any component renders without an App Router.
 */
export const routerState = {
  pathname: "/",
  search: "",
  push: vi.fn(),
  replace: vi.fn(),
  back: vi.fn(),
  refresh: vi.fn(),
  prefetch: vi.fn(),
};

export function resetRouter(route = "/") {
  const [pathname, search = ""] = route.split("?");
  routerState.pathname = pathname || "/";
  routerState.search = search;
  routerState.push.mockReset();
  routerState.replace.mockReset();
  routerState.back.mockReset();
  routerState.refresh.mockReset();
}
