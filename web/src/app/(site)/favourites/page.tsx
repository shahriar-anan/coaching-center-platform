import { redirect } from "next/navigation";

/** The Wish List moved from /favourites to /wishlist. Old links land on the new address. */
export default function Page() {
  redirect("/wishlist");
}
