import { Suspense } from "react";
import { FavouritesPage } from "@/components/student/catalog/favourites-page";
import { RequireStudent } from "@/lib/auth/guards";

/** The guard reads the URL (?next=), so it sits inside a Suspense boundary for the static build. */
export default function Favourites() {
  return (
    <Suspense fallback={null}>
      <RequireStudent>
        <FavouritesPage />
      </RequireStudent>
    </Suspense>
  );
}
