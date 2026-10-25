import { ModulePage } from "@/components/student/catalog/module-page";

export default async function McqModule({ params }: { params: Promise<{ moduleId: string }> }) {
  const { moduleId } = await params;
  return <ModulePage door="mcq" moduleId={moduleId} />;
}
