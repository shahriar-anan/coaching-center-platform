import type { Metadata } from "next";
import { DashboardPage } from "@/components/student/home/home-page";

export const metadata: Metadata = { title: "Dashboard" };

/** The student's dashboard (16 Nov). Login lands here; "/" is the landing page. */
export default function Dashboard() {
  return <DashboardPage />;
}
