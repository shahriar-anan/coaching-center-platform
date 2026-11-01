import { describe, expect, it } from "vitest";
import { packagesUrl, PACKAGE_PAGE, type PackageFilter } from "@/lib/api/exam/catalog";
import {
  PACKAGE_SEARCH_MAX,
  flattenCategories,
  parsePackageFilter,
  withPackageFilter,
} from "@/components/student/catalog/packages-filter";

const CATEGORY = "4b1f6c2e-9a3d-4e7b-8c5a-1d2e3f4a5b6c";
const NONE: PackageFilter = { categoryId: null, examType: null, q: "" };

describe("parsePackageFilter", () => {
  it("reads the category, type and search text from the URL", () => {
    const params = new URLSearchParams(`category=${CATEGORY}&type=MCQ&q=  bar exam  `);
    expect(parsePackageFilter(params)).toEqual({ categoryId: CATEGORY, examType: "MCQ", q: "bar exam" });
  });

  it("treats a bad category id, an unknown type and a missing search as no filter", () => {
    const params = new URLSearchParams("category=not-a-uuid&type=PDF");
    expect(parsePackageFilter(params)).toEqual(NONE);
    expect(parsePackageFilter(new URLSearchParams(""))).toEqual(NONE);
  });

  it("caps the search text", () => {
    const params = new URLSearchParams({ q: "x".repeat(PACKAGE_SEARCH_MAX + 20) });
    expect(parsePackageFilter(params).q).toHaveLength(PACKAGE_SEARCH_MAX);
  });
});

describe("withPackageFilter", () => {
  it("writes the filters and keeps other query values", () => {
    const params = new URLSearchParams("utm=1&q=old");
    const next = withPackageFilter(params, { categoryId: CATEGORY, examType: "WRITTEN", q: " new " });
    expect(next.get("utm")).toBe("1");
    expect(next.get("category")).toBe(CATEGORY);
    expect(next.get("type")).toBe("WRITTEN");
    expect(next.get("q")).toBe("new");
  });

  it("removes an empty filter from the URL", () => {
    const params = new URLSearchParams(`category=${CATEGORY}&type=MCQ&q=abc`);
    const next = withPackageFilter(params, NONE);
    expect(next.toString()).toBe("");
  });

  it("round-trips through parsePackageFilter", () => {
    const filter: PackageFilter = { categoryId: CATEGORY, examType: "MCQ", q: "law" };
    expect(parsePackageFilter(withPackageFilter(new URLSearchParams(), filter))).toEqual(filter);
  });
});

describe("packagesUrl", () => {
  it("sends each set filter with the page and the page size", () => {
    const url = new URL(packagesUrl({ categoryId: CATEGORY, examType: "MCQ", q: " law " }, 2), "http://localhost");
    expect(url.pathname).toBe("/api/v1/public/live-exam-modules");
    expect(url.searchParams.get("categoryId")).toBe(CATEGORY);
    expect(url.searchParams.get("branch")).toBe("true");
    expect(url.searchParams.get("examType")).toBe("MCQ");
    expect(url.searchParams.get("q")).toBe("law");
    expect(url.searchParams.get("page")).toBe("2");
    expect(url.searchParams.get("size")).toBe(String(PACKAGE_PAGE));
  });

  it("leaves out the filters that are not set", () => {
    const url = new URL(packagesUrl(NONE, 0), "http://localhost");
    expect([...url.searchParams.keys()].sort()).toEqual(["page", "size"]);
  });
});

describe("flattenCategories", () => {
  const leaf = (id: string, position: number, children: unknown[] = []) =>
    ({ id, position, children, parentId: null, titleEn: id, titleBn: id, modules: [] }) as never;

  it("orders siblings by position and gives each row its depth", () => {
    const tree = [
      leaf("b", 2, [leaf("b2", 1)]),
      leaf("a", 1),
    ];
    const rows = flattenCategories(tree).map((row) => [row.node.id, row.depth]);
    expect(rows).toEqual([
      ["a", 0],
      ["b", 0],
      ["b2", 1],
    ]);
  });
});
