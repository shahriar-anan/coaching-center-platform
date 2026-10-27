import type { Metadata } from "next";
import { ComingSoonPage } from "@/app/(site)/_components/coming-soon-page";

export const metadata: Metadata = { robots: { index: false } };

export default function MediaPage() {
  return <ComingSoonPage page="media" />;
}
