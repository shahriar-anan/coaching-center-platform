import { redirect } from "next/navigation";

/** The old address of one offline instructor (accounting-plan 4.6). The payee has the same id. */
export default async function StaffOfflineInstructorRedirect({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  redirect(`/staff/accounting/payees/${id}`);
}
