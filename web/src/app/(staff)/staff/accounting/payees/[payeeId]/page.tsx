import { PayeePage } from "@/components/staff/accounting/payee-page";

export default async function StaffAccountingPayeePage({ params }: { params: Promise<{ payeeId: string }> }) {
  const { payeeId } = await params;
  return <PayeePage payeeId={payeeId} />;
}
