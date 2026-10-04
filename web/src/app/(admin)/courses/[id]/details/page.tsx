"use client";

import { useParams } from "next/navigation";
import { CourseRecordPage } from "@/components/course-record-page";

export default function CourseDetailsRoute() {
  const params = useParams();
  const id = params.id as string;
  return <CourseRecordPage courseId={id} />;
}
