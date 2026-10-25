import type { MessageTree } from "../types";

const notices = {
  title: "Notices",
  markAll: "Mark all as read",
  unreadCount: "{count} unread notices",
  unreadDot: "Unread",
  empty: "No notices yet.",
  kind: {
    REMINDER: "Exam starting soon",
    MCQ_RESULT: "MCQ score",
    WRITTEN_RESULT: "Written result published",
    other: "Notice",
  },
  score: "{score}/{max}",
  passed: "Passed",
  failed: "Not passed",
  openExam: "Open exam page",
  markedRead: "Notice marked as read.",
} satisfies MessageTree;

export default notices;
