import { EntryPage } from "@/components/staff/accounting/entries/entry-page";

export default async function StaffAccountingEntryPage({ params }: { params: Promise<{ entryId: string }> }) {
  const { entryId } = await params;
  return <EntryPage entryId={entryId} />;
}
