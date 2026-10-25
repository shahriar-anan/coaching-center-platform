import { ExamPage } from "@/components/student/catalog/exam-page";

export default async function Exam({ params }: { params: Promise<{ examId: string }> }) {
  const { examId } = await params;
  return <ExamPage examId={examId} />;
}
