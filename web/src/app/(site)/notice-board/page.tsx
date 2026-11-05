import type { Metadata } from "next";
import { NoticeBoardList } from "@/components/notice-board/notice-board-list";

export const metadata: Metadata = {
  title: "Notices",
  description: "Official notices from SYMAM LAW.",
};

export default function NoticeBoardPage() {
  return <NoticeBoardList />;
}
