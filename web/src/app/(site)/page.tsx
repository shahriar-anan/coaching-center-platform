import type { Metadata } from "next";
import { HomePage } from "@/components/student/home/home-page";
import { siteUrl } from "@/lib/config";

const description = "বিজেএস ও বার কাউন্সিল পরীক্ষার পূর্ণ প্রস্তুতি। লাইভ এমসিকিউ ও রিটেন এক্সাম, ফলাফল ও মেধাতালিকা।";

export const metadata: Metadata = {
  // The Open Graph image is an absolute URL when the site address is configured.
  metadataBase: siteUrl ? new URL(siteUrl) : undefined,
  title: { absolute: "SYMAM LAW · এক্সাম পোর্টাল" },
  description,
  openGraph: {
    title: "SYMAM LAW · এক্সাম পোর্টাল",
    description,
    images: [{ url: "/brand/og.jpg", width: 1131, height: 636, alt: "SYMAM LAW" }],
  },
};

export default function Home() {
  return <HomePage />;
}
