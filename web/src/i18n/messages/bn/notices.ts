import type { MessageTree } from "../types";

const notices = {
  title: "নোটিশ",
  markAll: "সবগুলো পড়া হয়েছে",
  unreadCount: "{count}টি অপঠিত নোটিশ",
  unreadDot: "অপঠিত",
  empty: "এখনো কোনো নোটিশ নেই।",
  kind: {
    REMINDER: "পরীক্ষা শুরু হতে যাচ্ছে",
    MCQ_RESULT: "এমসিকিউ স্কোর",
    WRITTEN_RESULT: "রিটেন ফল প্রকাশিত",
    other: "নোটিশ",
  },
  score: "{score}/{max}",
  passed: "উত্তীর্ণ",
  failed: "অনুত্তীর্ণ",
  openExam: "পরীক্ষার পাতা খুলুন",
  markedRead: "নোটিশটি পড়া হয়েছে।",
} satisfies MessageTree;

export default notices;
