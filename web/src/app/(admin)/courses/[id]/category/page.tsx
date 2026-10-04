"use client";

import { useParams } from "next/navigation";
import { CourseOverviewPage } from "@/components/course-overview-page";

export default function CourseCategoryRoute() {
  const params = useParams();
  const id = params.id as string;
  return <CourseOverviewPage courseId={id} />;
}
