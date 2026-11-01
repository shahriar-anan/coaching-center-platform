import type { MessageTree } from "../types";

/** The staff Reviews page: the queue of reviews of exams and packages, its tabs and filters, and the moderation actions (admin reviews plan, section 5). */
const staffReviews = {
  title: "Reviews",
  readOnly: "You can read the reviews of your own exams. Only the Master Admin and System Admins approve, hide or feature them.",
  summary: {
    waiting: "Waiting",
    published: "Published",
    hidden: "Hidden",
    featured: "Featured",
  },
  tabs: {
    label: "Review status",
    waiting: "Waiting",
    published: "Published",
    hidden: "Hidden",
    all: "All",
  },
  filters: {
    label: "Filter reviews",
    kind: "Type",
    kindAll: "All",
    kindExam: "Exam",
    kindPackage: "Package",
    target: "Exam or package",
    targetAny: "Any exam or package",
    targetUnknown: "Selected exam or package",
    targetClear: "Clear",
    rating: "Stars",
    ratingAny: "Any stars",
    ratingOne: "1 star",
    ratingMany: "{count} stars",
    featured: "Featured only",
    search: "Student",
    searchPlaceholder: "Name, student code or phone",
    sort: "Sort",
    sortNewest: "Newest first",
    sortOldest: "Oldest first",
    sortLowest: "Lowest stars first",
    sortHighest: "Highest stars first",
    reset: "Clear filters",
  },
  target: {
    title: "Choose an exam or a package",
    body: "Only the reviews of the exam or package you choose are shown.",
    apply: "Show these reviews",
    cancel: "Cancel",
  },
  row: {
    stars: "{count} out of 5 stars",
    starsOnly: "Stars only. No comment.",
    expand: "Show more",
    collapse: "Show less",
    unnamed: "Student",
    removedTarget: "Removed",
    featured: "Featured",
    hiddenReason: "Reason: {reason}",
  },
  kind: {
    exam: "Exam",
    package: "Package",
  },
  status: {
    PENDING: "Waiting",
    APPROVED: "Published",
    HIDDEN: "Hidden",
  },
  actions: {
    approve: "Approve",
    approveAndFeature: "Approve and feature",
    hide: "Hide",
    feature: "Feature",
    unfeature: "Unfeature",
  },
  hide: {
    title: "Hide this review?",
    body: "The student sees that the review is not published, with the reason you write here.",
    label: "Reason",
    required: "Write a reason. The student sees it.",
    confirm: "Hide review",
  },
  toast: {
    approved: "Review published.",
    approvedFeatured: "Review published and featured on the landing page.",
    approvedNotFeatured: "Review published, but it could not be featured. Press Feature to try again.",
    hidden: "Review hidden.",
    featured: "Review featured on the landing page.",
    unfeatured: "Review taken off the landing page.",
    failed: "That could not be done. Try again.",
  },
  empty: {
    waiting: {
      title: "Nothing is waiting",
      body: "New comments appear here for approval. Ratings without a comment are published at once.",
    },
    published: {
      title: "No published reviews yet",
      body: "Approved reviews appear here.",
    },
    hidden: {
      title: "No hidden reviews",
      body: "Reviews you hide appear here, with the reason.",
    },
    all: {
      title: "No reviews yet",
      body: "Reviews appear here once students rate an exam or a package they have sat or hold.",
    },
    filtered: {
      title: "No reviews match these filters",
      body: "Try another tab or clear the filters.",
    },
  },
  loadMore: "Load more",
  menu: "Reviews",
} satisfies MessageTree;

export default staffReviews;
