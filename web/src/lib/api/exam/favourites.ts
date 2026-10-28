"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { PublicExam, PublicModule } from "@/lib/api/exam/types";

export type FavouriteKind = "exam" | "package";

/** `GET /api/v1/me/favourites`: the student's favourite exams and packages (NR-01, B6). */
export interface Favourites {
  exams: PublicExam[];
  packages: PublicModule[];
}

export const favouriteKeys = {
  all: ["catalog", "favourites"] as const,
};

/** The student's favourites. Only ever asked for a signed-in student. */
export function useFavourites(enabled: boolean) {
  return useQuery({
    queryKey: favouriteKeys.all,
    staleTime: 30_000,
    enabled,
    queryFn: ({ signal }) => apiRequest<Favourites>("/api/v1/me/favourites", { signal }),
  });
}

/** The URL of one favourite: `PUT` or `DELETE` on it sets or clears the mark (NR-01, B6). */
export function favouriteUrl(kind: FavouriteKind, id: string): string {
  return `/api/v1/me/favourites/${kind === "exam" ? "exams" : "packages"}/${id}`;
}

/**
 * One favourite mark on one exam or package (plan S3.3). The mark changes at once, and goes back if the API refuses it. The
 * favourites list is asked again afterwards, so the Favourites page is right the next time it opens.
 */
export function useFavouriteToggle(kind: FavouriteKind, id: string, initial: boolean) {
  const client = useQueryClient();
  const [on, setOn] = useState(initial);
  // A fresh read from the server (a reload, or the list being asked again) replaces the local mark. The comparison runs during
  // render, so the mark is right on the same pass and no effect is needed.
  const [seen, setSeen] = useState(initial);
  if (initial !== seen) {
    setSeen(initial);
    setOn(initial);
  }

  const mutation = useMutation({
    retry: false,
    mutationFn: (next: boolean) =>
      apiRequest<void>(favouriteUrl(kind, id), { method: next ? "PUT" : "DELETE" }),
    onMutate: (next) => {
      setOn(next);
    },
    onError: (_error, next) => {
      setOn(!next);
    },
    onSettled: () => {
      void client.invalidateQueries({ queryKey: favouriteKeys.all });
    },
  });

  return {
    on,
    pending: mutation.isPending,
    failed: mutation.isError,
    toggle: () => mutation.mutate(!on),
  };
}
