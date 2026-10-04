"use client";

import { useRouter } from "next/navigation";
import { CourseDetailsForm } from "@/components/course-details-form";
import { CourseShell } from "@/components/course-shell";

export default function NewCoursePage() {
  const router = useRouter();
  return (
    <CourseShell course={null} step="details">
      <CourseDetailsForm
        course={null}
        onSaved={(course) => router.push(`/courses/${course.id}`)}
      />
    </CourseShell>
  );
}
