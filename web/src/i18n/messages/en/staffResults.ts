import type { MessageTree } from "../types";

/** Staff results report, publish of written results, and a student's exam history (WS9 OPS). */
const staffResults = {
  title: "Exam results",
  openLeaderboard: "Merit list",
  summary: {
    participants: "Participants",
    submitted: "Submitted",
    failed: "Not submitted",
    average: "Average",
    highest: "Highest",
    lowest: "Lowest",
    passed: "Passed",
    passRate: "Pass rate",
  },
  filters: {
    kind: "Type",
    kindAll: "All types",
    status: "Status",
    statusAll: "All statuses",
    search: "Find a student",
    searchPlaceholder: "Name or student code",
    sort: "Sort",
    reset: "Reset filters",
  },
  kind: {
    LIVE: "Live",
    RETAKE: "Practice",
  },
  status: {
    IN_PROGRESS: "Sitting",
    SUBMITTED: "Submitted",
    FAILED_TO_SUBMIT: "Not submitted",
  },
  sort: {
    submittedNewest: "Submitted, newest first",
    submittedOldest: "Submitted, oldest first",
    scoreHigh: "Score, highest first",
    scoreLow: "Score, lowest first",
    name: "Name",
  },
  columns: {
    student: "Student",
    kind: "Type",
    status: "Status",
    score: "Score",
    maximum: "Full marks",
    result: "Result",
    started: "Started",
    submitted: "Submitted",
    published: "Published",
  },
  result: {
    passed: "Passed",
    failed: "Not passed",
    none: "—",
  },
  published: {
    yes: "Published",
    no: "Not published",
    notWritten: "Not applicable",
  },
  rowMenu: {
    label: "Actions for {name}",
    openStudent: "Open the student",
    openLeaderboard: "Open the merit list",
    openResults: "Open the results",
  },
  publish: {
    button: "Publish all drafts ({count})",
    none: "No drafts to publish",
    confirmTitle: "Publish results for {exam}?",
    confirmBody: "{count} written results will be published, and each student will get a notice. Results cannot be unpublished from this page.",
    confirm: "Publish results",
    done: "{count} results published.",
    failed: "The results could not be published. Try again.",
  },
  empty: {
    title: "No results yet",
    body: "Results appear here once students submit their sittings.",
    noMatch: "No results match these filters",
    noMatchBody: "Change the filters or the search and look again.",
  },
  history: {
    title: "Exam history",
    empty: "This student has not sat any exam yet.",
    columns: {
      exam: "Exam",
      module: "Module",
      kind: "Type",
      status: "Status",
      score: "Score",
      result: "Result",
      submitted: "Submitted",
      published: "Published",
    },
    readOnly: "Newest first. Each row opens that exam's results report.",
  },
  percent: "{value}%",
} satisfies MessageTree;

export default staffResults;
