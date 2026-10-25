import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MyModulesPage } from "@/components/student/catalog/my-modules-page";
import { moduleAccessKey, type AccessRows } from "@/components/student/result/access-source";
import bn from "@/i18n/messages/bn/result";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const LABELS = Object.values(bn.access);

const makeModule = (id: string, titleBn: string) => ({
  id,
  categoryId: "c1",
  titleEn: `Module ${id}`,
  titleBn,
  descriptionEn: null,
  descriptionBn: null,
  status: "ACTIVE",
  position: 1,
  price: null,
});

const modules = [
  makeModule("m1", "মডিউল এক"),
  makeModule("m2", "মডিউল দুই"),
  makeModule("m3", "মডিউল তিন"),
  makeModule("m4", "মডিউল চার"),
];

/** m1 by roster, m2 by a module purchase, m3 by a source this page does not know, m4 by one exam purchase. */
const access = {
  modules: [
    { moduleId: "m1", via: "ROSTER", orderId: null },
    { moduleId: "m2", via: "MODULE", orderId: "o1" },
    { moduleId: "m3", via: "NEW_SOURCE", orderId: null },
  ],
  exams: [{ examId: "e4", moduleId: "m4", via: "EXAM", orderId: "o2" }],
};

const page = { content: [], page: 0, totalPages: 0 };

describe("the source label for one module row (my modules)", () => {
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

  it("a known exam grant still labels a module whose only module-wide source is unknown", () => {
    const mixed: AccessRows = {
      modules: [{ moduleId: "m1", via: "SOMETHING_NEW" }],
      exams: [{ moduleId: "m1", via: "EXAM" }],
    };
    expect(moduleAccessKey(mixed, "m1")).toBe("result.access.exam");
  });
});

describe("my modules shows where each module came from", () => {
  it("labels roster, module purchase and exam purchase in Bangla; an unknown source has no label", async () => {
    mockApi({
      "GET /api/v1/live-exam-modules": { content: modules, page: 0, totalPages: 1 },
      "GET /api/v1/me/exam-routine": page,
      "GET /api/v1/me/live-exam-access": access,
    });
    renderWithProviders(<MyModulesPage />, { user: fixtures.student, locale: "bn" });

    expect(await screen.findByText(/মডিউল এক/)).toBeInTheDocument();
    expect(screen.getByText(bn.access.roster)).toBeInTheDocument();
    expect(screen.getByText(bn.access.module)).toBeInTheDocument();
    expect(screen.getByText(bn.access.exam)).toBeInTheDocument();
    expect(screen.queryByText(bn.access.manual)).not.toBeInTheDocument();

    const unknownRow = screen.getByText(/মডিউল তিন/).closest("li");
    expect(unknownRow).not.toBeNull();
    for (const label of LABELS) {
      expect(within(unknownRow as HTMLElement).queryByText(label)).not.toBeInTheDocument();
    }
  });

  it("the page still lists the modules, with no source label, when the access endpoint is not there", async () => {
    mockApi({
      "GET /api/v1/live-exam-modules": { content: modules, page: 0, totalPages: 1 },
      "GET /api/v1/me/exam-routine": page,
    });
    renderWithProviders(<MyModulesPage />, { user: fixtures.student, locale: "bn" });

    expect(await screen.findByText(/মডিউল দুই/)).toBeInTheDocument();
    for (const label of LABELS) {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    }
  });
});
