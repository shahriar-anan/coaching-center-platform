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
