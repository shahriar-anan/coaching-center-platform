import type { MessageTree } from "../types";

/** Staff results report, publish of written results, and a student's exam history (WS9 OPS). */
const staffResults = {
  title: "পরীক্ষার ফলাফল",
  openLeaderboard: "মেধাতালিকা",
  summary: {
    participants: "অংশগ্রহণকারী",
    submitted: "জমা দেওয়া হয়েছে",
    failed: "জমা হয়নি",
    average: "গড় নম্বর",
    highest: "সর্বোচ্চ",
    lowest: "সর্বনিম্ন",
    passed: "উত্তীর্ণ",
    passRate: "উত্তীর্ণের হার",
  },
  filters: {
    kind: "ধরন",
    kindAll: "সব ধরন",
    status: "অবস্থা",
    statusAll: "সব অবস্থা",
    search: "শিক্ষার্থী খুঁজুন",
    searchPlaceholder: "নাম বা শিক্ষার্থী কোড",
    sort: "সাজান",
    reset: "ফিল্টার মুছুন",
  },
  kind: {
    LIVE: "মূল পরীক্ষা",
    RETAKE: "অনুশীলন",
  },
  status: {
    IN_PROGRESS: "চলছে",
    SUBMITTED: "জমা দেওয়া হয়েছে",
    FAILED_TO_SUBMIT: "জমা হয়নি",
  },
  sort: {
    submittedNewest: "জমার সময়, নতুন আগে",
    submittedOldest: "জমার সময়, পুরনো আগে",
    scoreHigh: "নম্বর, বেশি আগে",
    scoreLow: "নম্বর, কম আগে",
    name: "নাম",
  },
  columns: {
    student: "শিক্ষার্থী",
    kind: "ধরন",
    status: "অবস্থা",
    score: "প্রাপ্ত নম্বর",
    maximum: "পূর্ণমান",
    result: "ফলাফল",
    started: "শুরু",
    submitted: "জমা",
    published: "প্রকাশ",
  },
  result: {
    passed: "উত্তীর্ণ",
    failed: "অনুত্তীর্ণ",
    none: "—",
  },
  published: {
    yes: "প্রকাশিত",
    no: "প্রকাশ বাকি",
    notWritten: "প্রযোজ্য নয়",
  },
  rowMenu: {
    label: "{name}-এর কাজ",
    openStudent: "শিক্ষার্থীর পাতা খুলুন",
    openLeaderboard: "মেধাতালিকা খুলুন",
    openResults: "ফলাফল খুলুন",
  },
  publish: {
    button: "সব খসড়া প্রকাশ করুন ({count}টি)",
    none: "প্রকাশের জন্য কোনো খসড়া নেই",
    confirmTitle: "{exam} এর ফলাফল প্রকাশ করবেন?",
    confirmBody: "{count}টি রিটেন খাতার ফলাফল প্রকাশ হবে, আর প্রত্যেক শিক্ষার্থী একটি নোটিশ পাবেন। এই পাতা থেকে প্রকাশ বাতিল করা যাবে না।",
    confirm: "ফলাফল প্রকাশ করুন",
    done: "{count}টি ফলাফল প্রকাশ হয়েছে।",
    failed: "ফলাফল প্রকাশ করা যায়নি। আবার চেষ্টা করুন।",
  },
  empty: {
    title: "এখনো কোনো ফলাফল নেই",
    body: "শিক্ষার্থীরা খাতা জমা দিলে ফলাফল এখানে দেখা যাবে।",
    noMatch: "এই ফিল্টারে কোনো ফলাফল মেলেনি",
    noMatchBody: "ফিল্টার বা খোঁজ বদলে আবার দেখুন।",
  },
  history: {
    title: "পরীক্ষার ইতিহাস",
    empty: "এই শিক্ষার্থী এখনো কোনো পরীক্ষা দেননি।",
    columns: {
      exam: "পরীক্ষা",
      module: "মডিউল",
      kind: "ধরন",
      status: "অবস্থা",
      score: "নম্বর",
      result: "ফলাফল",
      submitted: "জমা",
      published: "প্রকাশ",
    },
    readOnly: "সবচেয়ে নতুন আগে। প্রতিটি সারি সেই পরীক্ষার ফলাফল পাতা খোলে।",
  },
  percent: "{value}%",
} satisfies MessageTree;

export default staffResults;
