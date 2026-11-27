import { redirect } from "next/navigation";
import { ACC_GROUP_IDS } from "@/lib/api/accounting/ids";

/** The old address (accounting-plan 4.6): the Offline instructors group now lives in Accounting. */
export default function StaffOfflineInstructorsRedirect() {
  redirect(`/staff/accounting/groups/${ACC_GROUP_IDS.OFFLINE_INSTRUCTORS}`);
}
