import { VoucherPage } from "@/components/staff/accounting/print/voucher";

export default async function StaffAccountingVoucherPage({ params }: { params: Promise<{ entryId: string }> }) {
  const { entryId } = await params;
  return <VoucherPage entryId={entryId} />;
}
