import { redirect } from "next/navigation";
import { ACC_GROUP_IDS } from "@/lib/api/accounting/ids";

/** The old Instructor payments address: the entries of the Offline instructors group (accounting-plan 4.6). */
export default function StaffOfflineInstructorPaymentsRedirect() {
  redirect(`/staff/accounting/entries?groupId=${ACC_GROUP_IDS.OFFLINE_INSTRUCTORS}`);
}
