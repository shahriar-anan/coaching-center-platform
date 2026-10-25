import { DoorPage } from "@/components/student/catalog/door-page";

export default async function McqCategory({ params }: { params: Promise<{ categoryId: string }> }) {
  const { categoryId } = await params;
  return <DoorPage door="mcq" categoryId={categoryId} />;
}
