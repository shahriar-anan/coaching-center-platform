import { describe, expect, it } from "vitest";
import { moduleAccessKey, type AccessRows } from "@/components/student/result/access-source";

describe("the source label for one package row", () => {
  const rows: AccessRows = {
    modules: [
      { moduleId: "m1", via: "MODULE" },
      { moduleId: "m1", via: "ROSTER" },
    ],
    exams: [{ moduleId: "m1", via: "EXAM" }],
  };

  it("uses the best module-wide source: roster before module purchase", () => {
    expect(moduleAccessKey(rows, "m1")).toBe("result.access.roster");
  });

  it("a module-wide source beats an exam-level one", () => {
    expect(moduleAccessKey({ modules: [{ moduleId: "m2", via: "MANUAL" }], exams: [{ moduleId: "m2", via: "EXAM" }] }, "m2")).toBe(
      "result.access.manual",
    );
  });

  it("an exam-level grant alone gives the exam label", () => {
    expect(moduleAccessKey({ modules: [], exams: [{ moduleId: "m3", via: "EXAM" }] }, "m3")).toBe("result.access.exam");
  });

  it("no label when the access rows are not loaded, the module has none, or its source is unknown", () => {
    expect(moduleAccessKey(undefined, "m1")).toBeNull();
    expect(moduleAccessKey({ modules: [], exams: [] }, "m1")).toBeNull();
    expect(moduleAccessKey({ modules: [{ moduleId: "m1", via: "SOMETHING_NEW" }], exams: [] }, "m1")).toBeNull();
  });

  it("a known exam grant still labels a package whose only module-wide source is unknown", () => {
    const mixed: AccessRows = {
      modules: [{ moduleId: "m1", via: "SOMETHING_NEW" }],
      exams: [{ moduleId: "m1", via: "EXAM" }],
    };
    expect(moduleAccessKey(mixed, "m1")).toBe("result.access.exam");
  });
});
