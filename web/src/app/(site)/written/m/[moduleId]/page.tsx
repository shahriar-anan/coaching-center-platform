import { ModulePage } from "@/components/student/catalog/module-page";

export default async function WrittenModule({ params }: { params: Promise<{ moduleId: string }> }) {
  const { moduleId } = await params;
  return <ModulePage door="written" moduleId={moduleId} />;
}
