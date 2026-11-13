import type { AdmissionFormField } from "@/lib/api/exam/offline-admission";

/*
 * The registration form's fields, in the order of the paper form (SYMAM LAW Registration Form). The form screen and the
 * print sheet both read this list, so they cannot drift apart. Every field is required by the API (plan D6).
 */

/** The personal information block. Gender is a pair of buttons, not a text box, so the form handles it separately. */
export const PERSONAL_FIELDS: AdmissionFormField[] = [
  "fullName",
  "fatherName",
  "motherName",
  "dateOfBirth",
  "email",
  "gender",
  "university",
  "semesterBatch",
  "nid",
  "mobile",
  "emergencyMobile",
  "hallMess",
  "thana",
  "district",
];

/** The address block, which takes several lines. */
export const ADDRESS_FIELDS: AdmissionFormField[] = ["presentAddress", "permanentAddress"];

/** The fields whose values are digits: a number pad for them. */
export const NUMERIC_FIELDS: AdmissionFormField[] = ["nid", "mobile", "emergencyMobile"];

export const ALL_FIELDS: AdmissionFormField[] = [...PERSONAL_FIELDS, ...ADDRESS_FIELDS];
