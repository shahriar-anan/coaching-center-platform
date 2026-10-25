import { DoorPage } from "@/components/student/catalog/door-page";

export default async function WrittenCategory({ params }: { params: Promise<{ categoryId: string }> }) {
  const { categoryId } = await params;
  return <DoorPage door="written" categoryId={categoryId} />;
}
