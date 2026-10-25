import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";
import { routerState } from "./helpers/router";

if (typeof URL.createObjectURL !== "function") {
  URL.createObjectURL = () => "blob:test";
}
if (typeof URL.revokeObjectURL !== "function") {
  URL.revokeObjectURL = () => undefined;
}

/** Every component sees the same router; `tests/helpers/router.ts` holds its state. */
vi.mock("next/navigation", () => ({
  useRouter: () => routerState,
  usePathname: () => routerState.pathname,
  useSearchParams: () => new URLSearchParams(routerState.search),
  useParams: () => ({}),
  redirect: vi.fn(),
  notFound: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
