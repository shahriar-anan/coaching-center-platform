import { clsx, type ClassValue } from "clsx";

/** Joins class names; use it instead of template strings for conditional classes. */
export function cn(...inputs: ClassValue[]): string {
  return clsx(inputs);
}
