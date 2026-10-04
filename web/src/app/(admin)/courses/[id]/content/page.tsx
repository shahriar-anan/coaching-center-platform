"use client";

import { useParams } from "next/navigation";
import { CourseContentPage } from "@/components/course-content-page";

export default function CourseContentRoute() {
  const params = useParams();
  const id = params.id as string;
  return <CourseContentPage courseId={id} />;
}
