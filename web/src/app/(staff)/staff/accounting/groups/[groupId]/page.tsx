import { GroupPage } from "@/components/staff/accounting/group-page";

export default async function StaffAccountingGroupPage({ params }: { params: Promise<{ groupId: string }> }) {
  const { groupId } = await params;
  return <GroupPage groupId={groupId} />;
}
