import { describe, expect, it } from "vitest";
import {
  buildBrowserTree,
  failureKind,
  groupByCategory,
  idParam,
  isTodayInDhaka,
  neighbour,
  nextToMark,
  paperStatus,
  selectedNode,
  selectionFor,
  selectionOf,
  uuidParam,
  withQuery,
} from "@/components/staff/marking/marking-queue";
import { ApiError } from "@/lib/api/errors";
import { feed, MODULE_ID, participation } from "./fixtures";

const pick = (record: { titleEn: string; titleBn: string }) => record.titleBn;
const ALL = "সব খাতা";
const UNCATEGORISED = "শ্রেণিবিহীন";

describe("queue order", () => {
  it("moves to the neighbour in list order and stops at the ends", () => {
    expect(neighbour(feed, "p1", 1)?.participantId).toBe("p2");
    expect(neighbour(feed, "p1", -1)).toBeNull();
    expect(neighbour(feed, "p3", 1)?.participantId).toBe("p4");
    expect(neighbour(feed, "p4", 1)).toBeNull();
  });

  it("finds the next paper still awaiting a mark, wrapping round, and skips drafts", () => {
    // p3 is published, so the next paper waiting after p2 is p4.
    expect(nextToMark(feed, "p2")?.participantId).toBe("p4");
    expect(nextToMark(feed, "p4")?.participantId).toBe("p1");
    expect(nextToMark(feed.slice(0, 3), "p1")).toBeNull();
    const more = [...feed, { ...feed[0]!, participantId: "p5", studentName: "নতুন" }];
    expect(nextToMark(more, "p4")?.participantId).toBe("p5");
    expect(nextToMark(more, "p5")?.participantId).toBe("p1");
  });
});

describe("browser tree", () => {
  it("nests category and exam, with the waiting count summed up the chain", () => {
    const { tree, examCategory } = buildBrowserTree(participation, pick, ALL, UNCATEGORISED);
    const root = tree[0]!;
    expect(root.id).toBe("all");
    expect(root.count).toBe(4);
    const government = root.children!.find((node) => node.id === "cat:c-root")!;
    expect(government.count).toBe(3);
    const bcs = government.children!.find((node) => node.id === "cat:c-bcs")!;
    expect(bcs.count).toBe(3);
    expect(bcs.children!.map((node) => node.id)).toEqual(["exam:e1"]);
    expect(government.children!.map((node) => node.id)).toEqual(["cat:c-bcs", "exam:e2"]);
    expect(examCategory.get("e1")).toBe("c-bcs");
    expect(examCategory.get("e2")).toBe("c-root");
  });

  it("files an exam with no category under a root Uncategorised node", () => {
    const { tree, examCategory } = buildBrowserTree(participation, pick, ALL, UNCATEGORISED);
    const uncategorised = tree[0]!.children!.find((node) => node.id === "cat:none")!;
    expect(uncategorised.label).toBe(UNCATEGORISED);
    expect(uncategorised.count).toBe(1);
    expect(uncategorised.children!.map((node) => node.id)).toEqual(["exam:e3"]);
    expect(examCategory.get("e3")).toBe("none");
  });

  it("lists every written exam under a category, its sub-categories included", () => {
    const { categoryExams } = buildBrowserTree(participation, pick, ALL, UNCATEGORISED);
    expect(categoryExams.get("c-root")).toEqual(["e1", "e2"]);
    expect(categoryExams.get("c-bcs")).toEqual(["e1"]);
    expect(categoryExams.get("none")).toEqual(["e3"]);
  });

  it("leaves out a row that is not a written exam", () => {
    const mcq = { ...participation[0]!, examId: "mcq", examType: "MCQ" as const, awaitingMarking: null };
    const { tree } = buildBrowserTree([mcq], pick, ALL, UNCATEGORISED);
    expect(JSON.stringify(tree)).not.toContain("exam:mcq");
  });

  it("maps a click to the URL selection; a category click and an exam click set different values", () => {
    expect(selectionFor("exam:e3")).toEqual({ cat: "", exam: "e3" });
    expect(selectionFor("cat:c-root")).toEqual({ cat: "c-root", exam: "" });
    expect(selectionFor("all")).toEqual({ cat: "", exam: "" });
    expect(selectionFor("cat:none")).toBeNull();
    expect(selectedNode({ cat: "", exam: "e1" })).toBe("exam:e1");
    expect(selectedNode({ cat: "c-root", exam: "" })).toBe("cat:c-root");
    expect(selectedNode({ cat: "", exam: "" })).toBeNull();
  });

  it("reads an uncategorised category in the URL as no filter at all", () => {
    expect(selectionOf("none", "")).toEqual({ cat: "", exam: "" });
    expect(selectionOf("c-bcs", "e1")).toEqual({ cat: "c-bcs", exam: "e1" });
    // A broken link's `undefined` or `null` is no id, so it never reaches the feed.
    expect(selectionOf("undefined", "null")).toEqual({ cat: "", exam: "" });
  });
});

describe("grouping and status", () => {
  it("groups rows by their category path, in list order; a row with no path groups as uncategorised", () => {
    const groups = groupByCategory(participation, pick, UNCATEGORISED);
    expect(groups.map((group) => group.label)).toEqual(["সরকারি চাকরি › বিসিএস", "সরকারি চাকরি", UNCATEGORISED]);
    expect(groups[0]!.items.map((row) => row.examId)).toEqual(["e1"]);
  });

  it("works out a paper's status the way the feed does", () => {
    expect(paperStatus({ status: "SUBMITTED", published: false, evaluatedPdf: null })).toBe("AWAITING");
    expect(paperStatus({ status: "SUBMITTED", published: false, evaluatedPdf: true })).toBe("DRAFT");
    expect(paperStatus({ status: "SUBMITTED", published: true, evaluatedPdf: true })).toBe("PUBLISHED");
    expect(paperStatus({ status: "FAILED_TO_SUBMIT", published: false, evaluatedPdf: null })).toBe("FAILED_TO_SUBMIT");
  });

  it("knows the Dhaka day, not the UTC day", () => {
    const now = Date.parse("2026-10-26T12:00:00+06:00");
    expect(isTodayInDhaka("2026-10-26T05:00:00+06:00", now)).toBe(true);
    expect(isTodayInDhaka("2026-10-25T23:30:00+06:00", now)).toBe(false);
  });
});

describe("links and failures", () => {
  it("drops a value that is not an id, so no `undefined` or `null` gets into a URL", () => {
    expect(uuidParam("undefined")).toBe("");
    expect(uuidParam("null")).toBe("");
    expect(uuidParam(null)).toBe("");
    expect(uuidParam("m1")).toBe("");
    expect(uuidParam(MODULE_ID)).toBe(MODULE_ID);
    expect(idParam("undefined")).toBe("");
    expect(idParam("null")).toBe("");
    expect(idParam("e1")).toBe("e1");
    expect(idParam(null)).toBe("");
  });

  it("builds a query from the values that are set only", () => {
    expect(withQuery("/staff/submissions", { exam: "e1", cat: "", status: undefined, q: null })).toBe(
      "/staff/submissions?exam=e1",
    );
    expect(withQuery("/staff/submissions", { exam: "", cat: "" })).toBe("/staff/submissions");
  });

  it("names a 4xx and a 5xx failure, and leaves a network fault unnamed", () => {
    expect(failureKind(new ApiError({ code: "VALIDATION_FAILED", message: "x", requestId: "r" } as never, 400))).toBe("request");
    expect(failureKind(new ApiError({ code: "INTERNAL_ERROR", message: "x", requestId: "r" } as never, 500))).toBe("server");
    expect(failureKind(new TypeError("Failed to fetch"))).toBeNull();
  });
});
