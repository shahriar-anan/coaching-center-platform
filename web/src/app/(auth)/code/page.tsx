import type { Metadata } from "next";
import { CodeCard } from "@/app/(auth)/code/code-card";

/** The page behind a mail's "Copy code" button. It holds a one-time code, so it is never indexed. */
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function CodePage() {
  return <CodeCard />;
}
