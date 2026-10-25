/** "অ্যাক্সেস আছে" with the way access was given. Stub until P-2 lands. */
export function AccessBadge({ via }: { via: "MODULE" | "EXAM" | "ROSTER" | "MANUAL" }) {
  return <span data-via={via}>অ্যাক্সেস আছে</span>;
}
