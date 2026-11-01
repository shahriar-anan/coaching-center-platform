import type { Role } from "@/lib/api/exam/types";

/** Who may open the Reviews page: the Master Admin and System Admins, who moderate, and teachers, who read their own exams. */
export function canOpenReviews(role: Role | null | undefined): boolean {
  return role === "MASTER_ADMIN" || role === "SYSTEM_ADMIN" || role === "INSTRUCTOR";
}

/** Who may approve, hide and feature reviews: the Master Admin and System Admins only. */
export function canModerateReviews(role: Role | null | undefined): boolean {
  return role === "MASTER_ADMIN" || role === "SYSTEM_ADMIN";
}
