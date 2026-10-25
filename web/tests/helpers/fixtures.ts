import type { CategoryNode, Me, PublicExam, PublicModule } from "@/lib/api/exam/types";

/** Shared fixtures, so every workstream's tests see the same people and catalog. */

const base = {
  phone: "01711000001",
  email: "user@example.com",
  emailVerifiedAt: null,
  status: "ACTIVE" as const,
  studentCode: null,
};

export const fixtures = {
  student: {
    ...base,
    id: "11111111-1111-1111-1111-111111111111",
    role: "STUDENT",
    fullName: "রহিম উদ্দিন",
    studentCode: "STU-0001",
  } satisfies Me,

  masterAdmin: {
    ...base,
    id: "22222222-2222-2222-2222-222222222222",
    role: "MASTER_ADMIN",
    fullName: "মাস্টার অ্যাডমিন",
    email: "master@example.com",
  } satisfies Me,

  systemAdmin: {
    ...base,
    id: "33333333-3333-3333-3333-333333333333",
    role: "SYSTEM_ADMIN",
    fullName: "সিস্টেম অ্যাডমিন",
    email: "sysadmin@example.com",
  } satisfies Me,

  instructor: {
    ...base,
    id: "44444444-4444-4444-4444-444444444444",
    role: "INSTRUCTOR",
    fullName: "নাসরিন সুলতানা",
    email: "teacher@example.com",
  } satisfies Me,

  module: {
    id: "aaaaaaaa-0000-0000-0000-000000000001",
    categoryId: "cccccccc-0000-0000-0000-000000000001",
    titleEn: "BCS Preliminary",
    titleBn: "বিসিএস প্রিলিমিনারি",
    descriptionEn: "Full course for the preliminary round.",
    descriptionBn: "প্রিলিমিনারি রাউন্ডের পূর্ণাঙ্গ প্রস্তুতি।",
    position: 1,
    price: 2500,
    mcqCount: 4,
    writtenCount: 1,
  } satisfies PublicModule,

  categoryTree: [
    {
      id: "cccccccc-0000-0000-0000-000000000001",
      parentId: null,
      titleEn: "BCS",
      titleBn: "বিসিএস",
      position: 1,
      children: [],
      modules: [
        {
          id: "aaaaaaaa-0000-0000-0000-000000000001",
          categoryId: "cccccccc-0000-0000-0000-000000000001",
          titleEn: "BCS Preliminary",
          titleBn: "বিসিএস প্রিলিমিনারি",
          descriptionEn: null,
          descriptionBn: null,
          position: 1,
          price: 2500,
          mcqCount: 4,
          writtenCount: 1,
        },
      ],
    },
  ] satisfies CategoryNode[],

  mcqExam: {
    id: "eeeeeeee-0000-0000-0000-000000000001",
    moduleId: "aaaaaaaa-0000-0000-0000-000000000001",
    examType: "MCQ",
    titleEn: "CRPC Mock 1",
    titleBn: "সিআরপিসি মডেল টেস্ট ১",
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
    rulesBn: null,
    paperLanguage: "BN",
    liveAt: "2026-11-12T06:00:00.000Z",
    endAt: "2026-11-12T06:40:00.000Z",
    durationMinutes: 40,
    price: null,
  } satisfies PublicExam,

  writtenExam: {
    id: "eeeeeeee-0000-0000-0000-000000000002",
    moduleId: "aaaaaaaa-0000-0000-0000-000000000001",
    examType: "WRITTEN",
    titleEn: "Written Mock 1",
    titleBn: "রিটেন মডেল টেস্ট ১",
    descriptionEn: null,
    descriptionBn: null,
    rulesEn: null,
    rulesBn: null,
    paperLanguage: "EN",
    liveAt: null,
    endAt: null,
    durationMinutes: 120,
    price: null,
  } satisfies PublicExam,
};
