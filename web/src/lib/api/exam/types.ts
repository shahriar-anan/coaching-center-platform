/**
 * Response types shared by more than one exam workstream. Types used by one
 * workstream only live in that workstream's hooks file.
 */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type Role = "MASTER_ADMIN" | "SYSTEM_ADMIN" | "INSTRUCTOR" | "STUDENT";

export type StaffRole = Exclude<Role, "STUDENT">;

export type AccountStatus = "PENDING_ACTIVATION" | "ACTIVE" | "INACTIVE";

/** `GET /api/v1/me` */
export interface Me {
  id: string;
  phone: string | null;
  email: string | null;
  emailVerifiedAt: string | null;
  role: Role;
  status: AccountStatus;
  fullName: string;
  studentCode: string | null;
  /** True after a Google sign-up until the student adds a phone. */
  phoneRequired?: boolean;
}

export type ExamType = "MCQ" | "WRITTEN";

/** Selects the question typeface only. */
export type PaperLanguage = "BN" | "EN";

export type CatalogStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

/** The nine student statuses the API derives from the window and the student's own sitting. */
export type MyStatus =
  | "NOT_ANNOUNCED"
  | "UPCOMING"
  | "OPEN"
  | "IN_PROGRESS"
  | "SUBMITTED"
  | "AWAITING_RESULT"
  | "RESULT_READY"
  | "FAILED_TO_SUBMIT"
  | "MISSED";

/** Order status as stored, and as the staff payments list returns it. */
export type OrderStatus = "INITIATED" | "SUCCEEDED" | "FAILED" | "CANCELLED";

/** Payment status as students see it: `/live-exam-orders/status` and `/me/live-exam-orders`. */
export type PaymentStatus = "PENDING" | "PAID" | "FAILED" | "CANCELLED";

export type OrderKind = "MODULE" | "EXAM";

/** A title pair as the API sends it. */
export interface Titled {
  titleEn: string;
  titleBn: string;
}

/** `GET /api/v1/public/live-exam-modules`, and the modules inside the category tree. */
export interface PublicModule extends Titled {
  id: string;
  /** The student's access, present when signed in (plan section 8, A1). */
  access?: AccessInfo | null;
  categoryId: string;
  descriptionEn: string | null;
  descriptionBn: string | null;
  position: number;
  /** `null` when no price is set: say so in a sentence, never show zero. */
  price: number | null;
  /** Exam counts per type, when the API sends them (WS1). */
  mcqCount?: number;
  writtenCount?: number;
  /** The package cover: an API path, or null when no image has been uploaded. */
  coverUrl?: string | null;
  /** The price the order charges, with the list price and any discount (NR-01, S2). */
  pricing?: PriceView | null;
  popular?: boolean;
  /** Signed-in students only: whether the package is on their favourites (NR-01, S3). */
  favourite?: boolean | null;
}

/** The price block on an exam or package: the list price, the discount, and the price the order charges (NR-01). */
export interface PriceView {
  price: number | null;
  discountPercent: number | null;
  /** ISO UTC, or null for a discount with no end date. */
  discountEndsAt: string | null;
  /** The charged amount: the list price less any active discount. Null when there is no price. */
  effectivePrice: number | null;
  free: boolean;
  /** True for an exam with no price of its own, which is bought only through a priced package. */
  moduleOnly: boolean;
}

/** A package an exam is in, as the exam read lists it (NR-01). */
export interface PackageRef extends Titled {
  id: string;
}

/** One row of the category exam list, from `GET /api/v1/public/live-exam-categories/{id}/exams` (NR-01, S2.1). */
export interface CategoryExam extends Titled {
  id: string;
  categoryId: string;
  examType: ExamType;
  /** LIVE or PRACTICE. */
  mode: string;
  /** UNANNOUNCED, UPCOMING, LIVE, ENDED or PRACTICE. */
  phase: string;
  liveAt: string | null;
  endAt: string | null;
  popular: boolean;
  pricing: PriceView | null;
  packages: PackageRef[];
  favourite?: boolean | null;
}

/** `GET /api/v1/public/live-exam-categories`: the one category tree. */
export interface CategoryNode extends Titled {
  id: string;
  parentId: string | null;
  position: number;
  children: CategoryNode[];
  modules: PublicModule[];
  /** The category's own exams, published only (Phase 4, A4). Not the subcategories' exams. */
  exams?: CategoryExam[];
}

/** `GET /api/v1/public/live-exams/{id}` and the module exam list. */
export interface PublicExam extends Titled {
  id: string;
  /** The exam's own cover path, or null (Phase 4, E1). */
  coverUrl?: string | null;
  /** The student's access, present when signed in (plan section 8, A1). */
  access?: AccessInfo | null;
  moduleId: string;
  examType: ExamType;
  descriptionEn: string | null;
  descriptionBn: string | null;
  rulesEn: string | null;
  rulesBn: string | null;
  paperLanguage: PaperLanguage;
  /** ISO UTC, or `null` when the date is not set yet. */
  liveAt: string | null;
  endAt: string | null;
  durationMinutes: number;
  price: number | null;
  /** The price the order charges, with the list price and any discount (NR-01, S2). */
  pricing?: PriceView | null;
  /** Every package the exam is in (NR-01, S2.4). */
  packages?: PackageRef[];
  popular?: boolean;
  /** Signed-in students only (NR-01, S3). */
  favourite?: boolean | null;
}

export interface StudentQuestionOption {
  id: string;
  text: string;
  position: number;
  /** Only after the deadline. */
  correct?: boolean | null;
}

export interface StudentQuestion {
  id: string;
  stem: string;
  marks: number;
  explanation?: string | null;
  position: number;
  options: StudentQuestionOption[];
}

/** `GET /api/v1/live-exams/{id}` for a signed-in student. */
export interface StudentExam extends Omit<PublicExam, "price"> {
  passPercent: number | null;
  maximum: number;
  status: CatalogStatus;
  finallySaved: boolean;
  price: number | null;
  questionPdf: boolean | null;
  questions: StudentQuestion[] | null;
  myStatus: MyStatus;
  retakeAvailable: boolean | null;
  /** The student's own sitting: deadline and kind. Null when there is none. */
  sitting?: { kind: "LIVE" | "RETAKE"; startedAt: string; deadlineAt: string; submittedAt: string | null } | null;
}

/** The `access` object on public module and exam responses (plan section 8). */
export type AccessState = "NONE" | "MODULE" | "EXAM" | "ROSTER" | "MANUAL" | "FREE" | "BUNDLE";
export type BlockedReason = "PHONE_REQUIRED" | "PRICE_REQUIRED" | "ALREADY_HAS_ACCESS" | "CHECKOUT_OFF";
export interface AccessInfo {
  state: AccessState;
  buyable: boolean;
  blockedReason: BlockedReason | null;
  /** When the access the student holds ends. Once it has run out, `state` is NONE and this is the end of that grant (NR-01, S0.3 and F3). */
  expiresAt?: string | null;
}
