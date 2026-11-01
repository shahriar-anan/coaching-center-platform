import { ExamReviewsPage } from "@/components/student/reviews/exam-reviews-page";

export default async function ExamReviews({ params }: { params: Promise<{ examId: string }> }) {
  const { examId } = await params;
  return <ExamReviewsPage examId={examId} />;
}
