/** Types aligned with the running API OpenAPI at /v3/api-docs. */

export type Role = "MASTER_ADMIN" | "SYSTEM_ADMIN" | "STUDENT";

export type AccountStatus = "PENDING_ACTIVATION" | "ACTIVE" | "INACTIVE";

export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
  timestamp: string;
  requestId: string;
}

export interface TokenResponse {
  accessToken: string;
  expiresInSeconds: number;
  refreshToken?: string | null;
}

export interface MeView {
  id: string;
  phone: string;
  email: string;
  emailVerifiedAt: string | null;
  role: Role;
  status: AccountStatus;
  fullName: string;
  studentCode: string | null;
}

export interface AccountView {
  id: string;
  fullName: string;
  phone: string;
  email: string;
  role: Role;
  status: AccountStatus;
  studentCode: string | null;
}

export interface CreatedAccount {
  id: string;
  fullName: string;
  phone: string;
  email: string;
  role: Role;
  status: AccountStatus;
  activationCode: string;
  studentCode: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ActivationCodeResponse {
  activationCode: string;
}

export interface CreateAccountRequest {
  fullName: string;
  phone: string;
  email: string;
}

export type ContentLanguage = "BN" | "EN";
export type DeliveryMode = "ONLINE" | "OFFLINE" | "HYBRID";
export type AccessType = "FREEMIUM" | "FULL_FREE";
export type CourseStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export interface CourseView {
  id: string;
  titleEn: string;
  titleBn: string;
  contentLanguage: ContentLanguage;
  description: string;
  price: number | null;
  deliveryMode: DeliveryMode;
  accessType: AccessType;
  status: CourseStatus;
  closed: boolean;
  openingNumber: number;
  coverFileId: string | null;
  categoryId?: string | null;
  categoryPosition?: number | null;
}

export interface CategoryView {
  id: string;
  parentId: string | null;
  titleEn: string;
  titleBn: string;
  position: number;
}

export interface CategoryTreeNode {
  kind: "CATEGORY" | "COURSE";
  id: string;
  parentId: string | null;
  titleEn: string;
  titleBn: string;
  position: number;
  children?: CategoryTreeNode[] | null;
}

export interface CourseNodeView {
  id: string;
  parentId: string | null;
  nodeType: "SECTION" | "LECTURE";
  title: string;
  position: number;
  free: boolean;
  videoProvider: string | null;
  videoRef: string | null;
  notes: string | null;
}

export interface MaterialView {
  id: string;
  courseId: string;
  lectureNodeId: string | null;
  originalFileName: string;
  byteSize: number;
  contentType: string;
  free: boolean;
}

export interface BookView {
  id: string;
  titleEn: string;
  titleBn: string;
  author: string;
  description: string;
  price: number | null;
  available: boolean;
  coverFileId: string | null;
  categoryId?: string | null;
}

export interface BookCategoryView {
  id: string;
  titleEn: string;
  titleBn: string;
  position: number;
}

export interface PaymentView {
  id: string;
  studentId: string;
  payableType: string;
  payableId: string;
  amount: number;
  method: string;
  transactionRef: string;
  status: string;
  studentName?: string | null;
  studentPhone?: string | null;
  titleEn?: string | null;
  titleBn?: string | null;
  createdAt?: string | null;
}

export interface VerifyPaymentResult {
  paymentId: string;
  paymentStatus: string;
  enrollmentStatus: string | null;
  orderStatus: string | null;
}

export interface AdminBookOrderView {
  orderId: string;
  studentId: string;
  bookId: string;
  status: string;
  fulfillment: string;
  paymentMethod: string;
  phone: string;
  address: string | null;
  area: string | null;
}
