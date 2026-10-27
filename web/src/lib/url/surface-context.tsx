"use client";

import { createContext, useContext, type ReactNode } from "react";

export type Surface = "site" | "staff";

const SurfaceContext = createContext<Surface>("site");

/** Which of the two hosts served the page. The auth layout sets it from the request host. */
export function SurfaceProvider({ surface, children }: { surface: Surface; children: ReactNode }) {
  return <SurfaceContext.Provider value={surface}>{children}</SurfaceContext.Provider>;
}

/** "staff" on the admin host of a split setup, "site" everywhere else (and in tests without a provider). */
export function useSurface(): Surface {
  return useContext(SurfaceContext);
}
