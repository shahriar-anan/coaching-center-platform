import type { AdmissionForm, AdmissionState, AdmissionView } from "@/lib/api/exam/offline-admission";

/** A registration form the student has finished. Every required field is filled in. */
export const completeForm: AdmissionForm = {
  fullName: "Rahim Uddin",
  fatherName: "Karim Uddin",
  motherName: "Rahima Begum",
  dateOfBirth: "2001-03-15",
  email: "rahim@example.com",
  gender: "MALE",
  university: "Rajshahi University",
  semesterBatch: "3rd semester, batch 2025",
  nid: "1234567890123",
  mobile: "01711000012",
  emergencyMobile: "01811000013",
  hallMess: "Shah Mokhdum Hall, room 204",
  presentAddress: "House 4, Road 2, Motihar",
  permanentAddress: "Village Kalikapur, Godagari, Rajshahi",
};

export function admissionView(
  state: AdmissionState,
  overrides: Partial<AdmissionView> = {},
  form: AdmissionForm = {},
): AdmissionView {
  return {
    id: "adm-1",
    state,
    changesReason: null,
    submittedAt: null,
    submitCount: 0,
    hasPhoto: false,
    form,
    ...overrides,
  };
}
