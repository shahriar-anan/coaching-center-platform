/** Shared data for the target and student picker tests: a category tree, paged lists, and rows of packages and exams. */

export const CATEGORY_ID = "cccccccc-0000-4000-8000-000000000001";
export const SUB_CATEGORY_ID = "cccccccc-0000-4000-8000-000000000002";

export const categoryTree = [
  {
    id: CATEGORY_ID,
    parentId: null,
    titleEn: "BCS",
    titleBn: "বিসিএস",
    position: 1,
    archived: false,
    children: [
      {
        id: SUB_CATEGORY_ID,
        parentId: CATEGORY_ID,
        titleEn: "Preliminary",
        titleBn: "প্রিলিমিনারি",
        position: 1,
        archived: false,
        children: [],
        modules: [],
      },
    ],
    modules: [],
  },
];

/** The slice of a Spring page that `url` asks for, built from every row in `rows`. */
export function pageOf<T>(url: URL, rows: T[]) {
  const page = Number(url.searchParams.get("page") ?? "0");
  const size = Number(url.searchParams.get("size") ?? "20");
  return {
    content: rows.slice(page * size, page * size + size),
    page,
    size,
    totalElements: rows.length,
    totalPages: Math.ceil(rows.length / size),
  };
}

/** An exam as the staff exam list returns it. `i` numbers the title, so "Mock exam 3" is row 3. */
export function examRow(i: number, overrides: Record<string, unknown> = {}) {
  return {
    id: `e${i}`,
    titleEn: `Mock exam ${i}`,
    titleBn: `মক এক্সাম ${i}`,
    examType: i % 2 === 0 ? "MCQ" : "WRITTEN",
    status: "PUBLISHED",
    categoryId: CATEGORY_ID,
    ...overrides,
  };
}

/** A package as the staff package list returns it, with the exam counts the picker shows. */
export function packageRow(i: number, overrides: Record<string, unknown> = {}) {
  return {
    id: `p${i}`,
    titleEn: `Package ${i}`,
    titleBn: `প্যাকেজ ${i}`,
    status: "PUBLISHED",
    categoryId: CATEGORY_ID,
    mcqCount: 3,
    writtenCount: 2,
    ...overrides,
  };
}

export const bigExamList = Array.from({ length: 120 }, (_, i) => examRow(i + 1));
