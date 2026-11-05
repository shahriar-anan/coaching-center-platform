import type { Metadata } from "next";
import { NoticeBoardDetail } from "@/components/notice-board/notice-board-detail";

export const metadata: Metadata = {
  title: "Notice",
  description: "Official notice from SYMAM LAW.",
};

export default async function NoticeBoardItemPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <NoticeBoardDetail id={id} />;
}
