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
}

/** `GET /api/v1/public/live-exam-categories`: the one category tree. */
export interface CategoryNode extends Titled {
  id: string;
  parentId: string | null;
  position: number;
  children: CategoryNode[];
  modules: PublicModule[];
}

/** `GET /api/v1/public/live-exams/{id}` and the module exam list. */
export interface PublicExam extends Titled {
  id: string;
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
}

/** Error codes the exam pages map to copy. The API may send others. */
export type ApiErrorCode =
  | "VALIDATION_FAILED"
  | "RESOURCE_NOT_FOUND"
  | "UNAUTHENTICATED"
  | "ACCESS_DENIED"
  | "INVALID_CREDENTIALS"
  | "LOGIN_LOCKED"
  | "INVALID_CODE"
  | "CODE_EXPIRED"
  | "CODE_ALREADY_USED"
  | "CODE_ATTEMPTS_EXCEEDED"
  | "CURRENT_PASSWORD_INVALID"
  | "PASSWORD_NOT_ACCEPTED"
  | "DUPLICATE_PHONE"
  | "DUPLICATE_EMAIL"
  | "EMAIL_RATE_LIMITED"
  | "FILE_TOO_LARGE"
  | "PRICE_REQUIRED"
  | "PRICE_NOT_ALLOWED"
  | "CATEGORY_NOT_EMPTY"
  | "CATEGORY_ARCHIVED"
  | "CATEGORY_CYCLE"
  | "EXAM_ACCESS_REQUIRED"
  | "EXAM_NOT_FOR_SALE"
  | "ALREADY_PURCHASED"
  | "FINAL_SAVE_REQUIRED"
  | "MODULE_ARCHIVED"
  | "PASS_PERCENT_REQUIRED"
  | "QUESTIONS_REQUIRED"
  | "WRITTEN_PAPER_REQUIRED"
  | "EXAM_WINDOW_CONFLICT"
  | "EXAM_WINDOW_ENDED"
  | "CONTENT_LOCKED"
  | "EXAM_HAS_PARTICIPANTS"
  | "EXAM_HAS_PURCHASES"
  | "EXAM_TYPE_MISMATCH"
  | "SITTING_NOT_OPEN"
  | "RETAKE_NOT_ALLOWED"
  | "DEADLINE_PASSED"
  | "MARKS_OUT_OF_RANGE"
  | "RESULTS_NOT_READY"
  | "RESULTS_NOT_PUBLISHED"
  | "INSTRUCTOR_ALREADY_ASSIGNED"
  | "EVALUATION_NOT_ALLOWED"
  | "PAYMENT_GATEWAY_UNAVAILABLE"
  | "PHONE_REQUIRED"
  | "PHONE_ALREADY_SET"
  | "GOOGLE_SIGN_IN_UNAVAILABLE"
  | "MASTER_ADMIN_PROTECTED"
  | "ACTIVATION_NOT_PENDING";

/** The `access` object on public module and exam responses (plan section 8). */
export type AccessState = "NONE" | "MODULE" | "EXAM" | "ROSTER" | "MANUAL";
export type BlockedReason = "PHONE_REQUIRED" | "PRICE_REQUIRED" | "ALREADY_HAS_ACCESS" | "CHECKOUT_OFF";
export interface AccessInfo {
  state: AccessState;
  buyable: boolean;
  blockedReason: BlockedReason | null;
}
