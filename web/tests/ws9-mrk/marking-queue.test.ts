import { describe, expect, it } from "vitest";
import {
  buildBrowserTree,
  groupByCategory,
  isTodayInDhaka,
  neighbour,
  nextToMark,
  paperStatus,
  selectedNode,
  selectionFor,
  sortRows,
} from "@/components/staff/marking/marking-queue";
import { feed, modules, participation } from "./fixtures";

const pick = (record: { titleEn: string; titleBn: string }) => record.titleBn;

describe("queue order", () => {
  it("moves to the neighbour in list order and stops at the ends", () => {
    expect(neighbour(feed, "p1", 1)?.participantId).toBe("p2");
    expect(neighbour(feed, "p1", -1)).toBeNull();
    expect(neighbour(feed, "p3", 1)).toBeNull();
  });

  it("finds the next paper still awaiting a mark, wrapping round, and skips drafts", () => {
    expect(nextToMark(feed, "p2")?.participantId).toBe("p1");
    expect(nextToMark(feed, "p1")).toBeNull();
    const more = [...feed, { ...feed[0]!, participantId: "p4", studentName: "নতুন" }];
    expect(nextToMark(more, "p1")?.participantId).toBe("p4");
    expect(nextToMark(more, "p4")?.participantId).toBe("p1");
  });
});

describe("browser tree", () => {
  it("nests category, module and exam, with the waiting count on each node", () => {
    const { tree, examModule, categoryModules } = buildBrowserTree(modules, participation, pick, "সব খাতা");
    const root = tree[0]!;
    expect(root.id).toBe("all");
    expect(root.count).toBe(3);
    const government = root.children![0]!;
    expect(government.id).toBe("cat:c-root");
    expect(government.count).toBe(3);
    const bcs = government.children!.find((node) => node.id === "cat:c-bcs")!;
    expect(bcs.count).toBe(3);
    expect(bcs.children![0]!.id).toBe("mod:m1");
    expect(bcs.children![0]!.children![0]!.id).toBe("exam:e1");
    expect(examModule.get("e1")).toBe("m1");
    expect(categoryModules.get("c-root")).toEqual(["m1", "m2"]);
  });

  it("leaves out a module that has no written exam", () => {
    const { tree } = buildBrowserTree(modules, participation.slice(0, 1), pick, "সব খাতা");
    const ids = JSON.stringify(tree);
    expect(ids).toContain("mod:m1");
    expect(ids).not.toContain("mod:m2");
  });

  it("maps a click to the URL selection, and an exam click also sets its module", () => {
    const index = buildBrowserTree(modules, participation, pick, "সব খাতা");
    expect(selectionFor("exam:e1", index)).toEqual({ cat: "", module: "m1", exam: "e1" });
    expect(selectionFor("cat:c-root", index)).toEqual({ cat: "c-root", module: "", exam: "" });
    expect(selectionFor("all", index)).toEqual({ cat: "", module: "", exam: "" });
    expect(selectedNode({ cat: "", module: "m1", exam: "e1" })).toBe("exam:e1");
    expect(selectedNode({ cat: "", module: "", exam: "" })).toBeNull();
  });
});

describe("grouping and status", () => {
  it("groups modules by their category path, in server order", () => {
    const groups = groupByCategory(modules, pick);
    expect(groups.map((group) => group.label)).toEqual(["সরকারি চাকরি › বিসিএস", "সরকারি চাকরি"]);
  });

  it("works out a paper's status the way the feed does", () => {
    expect(paperStatus({ status: "SUBMITTED", published: false, evaluatedPdf: null })).toBe("AWAITING");
    expect(paperStatus({ status: "SUBMITTED", published: false, evaluatedPdf: true })).toBe("DRAFT");
    expect(paperStatus({ status: "SUBMITTED", published: true, evaluatedPdf: true })).toBe("PUBLISHED");
    expect(paperStatus({ status: "FAILED_TO_SUBMIT", published: false, evaluatedPdf: null })).toBe("FAILED_TO_SUBMIT");
  });

  it("sorts merged rows the way one module's feed sorts them", () => {
    const names = sortRows(feed, "studentName").map((row) => row.studentName);
    expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b)));
    const oldestFirst = sortRows(feed, "submittedAt,asc").map((row) => row.participantId);
    expect(oldestFirst).toEqual(["p3", "p2", "p1"]);
  });

  it("knows the Dhaka day, not the UTC day", () => {
    const now = Date.parse("2026-10-26T12:00:00+06:00");
    expect(isTodayInDhaka("2026-10-26T05:00:00+06:00", now)).toBe(true);
    expect(isTodayInDhaka("2026-10-25T23:30:00+06:00", now)).toBe(false);
  });
});
