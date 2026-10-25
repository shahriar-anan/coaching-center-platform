import type { MessageTree } from "../types";

const notices = {
  title: "Notices",
  markAll: "Mark all as read",
  unreadCount: "{count} unread notices",
  unreadDot: "Unread",
  empty: "No notices yet.",
  emptyBody: "Notices about your exams and results will show here. Pick a door to get started.",
  kind: {
    EXAM_REMINDER: "Exam coming up",
    EXAM_STARTING_SOON: "Exam starts within the hour",
    EXAM_OPEN: "Exam is open now",
    PURCHASE_GRANTED: "Your exam is unlocked",
    EXAM_PUBLISHED: "New exam published",
    RETAKE_AVAILABLE: "Practice attempt available",
    ANNOUNCEMENT: "Announcement from the centre",
    MCQ_SCORE: "MCQ score",
    WRITTEN_RESULT: "Written result published",
    other: "Notice",
  },
  score: "{score}/{max}",
  passed: "Passed",
  failed: "Not passed",
  readMore: "Read more",
  readLess: "Show less",
  openExam: "Open exam page",
  markedRead: "Notice marked as read.",
} satisfies MessageTree;

export default notices;
