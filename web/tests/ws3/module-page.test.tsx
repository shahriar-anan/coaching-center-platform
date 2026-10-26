import { screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ModulePage } from "@/components/student/catalog/module-page";
import { DoorPage } from "@/components/student/catalog/door-page";
import { renderWithQuery } from "./helpers";

const state = vi.hoisted(() => ({
  session: { status: "anonymous" } as Record<string, unknown>,
}));

const apiRequest = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api/client", () => ({
  apiRequest: (...args: unknown[]) => apiRequest(...args),
}));

vi.mock("@/lib/auth/session-context", () => ({
  useSession: () => ({ session: state.session, login: vi.fn() }),
}));

const moduleRow = {
  id: "m1",
  categoryId: "c1",
  titleEn: "BJS 19",
  titleBn: "১৯তম বিজেএস",
  descriptionEn: "Judicial service\nsecond line",
  descriptionBn: null,
  position: 0,
  price: 2500,
  mcqCount: 1,
  writtenCount: 1,
};

const exams = {
  content: [
    {
      id: "e-mcq",
      moduleId: "m1",
      examType: "MCQ",
      titleEn: "CrPC MCQ",
      titleBn: "সিআরপিসি",
      descriptionEn: null,
      descriptionBn: null,
      rulesEn: null,
      rulesBn: null,
      paperLanguage: "BN",
      liveAt: null,
      endAt: null,
      durationMinutes: 40,
      price: null,
    },
    {
      id: "e-written",
      moduleId: "m1",
      examType: "WRITTEN",
      titleEn: "Evidence written",
      titleBn: "ইভিডেন্স",
      descriptionEn: null,
      descriptionBn: null,
      rulesEn: null,
      rulesBn: null,
      paperLanguage: "BN",
      liveAt: null,
      endAt: null,
      durationMinutes: 60,
      price: null,
    },
  ],
  page: 0,
  size: 100,
  totalElements: 2,
  totalPages: 1,
};

beforeEach(() => {
  state.session = { status: "anonymous" };
  apiRequest.mockReset();
  apiRequest.mockImplementation(async (path: string) => {
    if (path.startsWith("/api/v1/public/live-exam-categories")) {
      return [];
    }
    if (path.startsWith("/api/v1/public/live-exam-modules/m1/exams")) {
      return exams;
    }
    if (path.startsWith("/api/v1/public/live-exam-modules/m1")) {
      return moduleRow;
    }
    return [];
  });
});

it("shows the coming-soon card on a door with no categories", async () => {
  renderWithQuery(<DoorPage door="mcq" />);
  expect(await screen.findByText("Coming soon")).toBeInTheDocument();
});

it("lists only the exams of the door's type on a module page", async () => {
  renderWithQuery(<ModulePage door="written" moduleId="m1" />);

  expect(await screen.findByRole("link", { name: /Evidence written/ })).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: /CrPC MCQ/ })).not.toBeInTheDocument();
});

it("offers the whole-module purchase to a signed-out visitor as a login link", async () => {
  renderWithQuery(<ModulePage door="mcq" moduleId="m1" />);

  const buy = await screen.findByRole("link", { name: "Buy whole module" });
  expect(buy.getAttribute("href")).toMatch(/^\/login\?next=/);
  expect(screen.getByText("Unlocks every exam in this module, including ones added later. Both MCQ and written.")).toBeInTheDocument();
});

it("shows the price-not-set sentence and no button when a module has no price", async () => {
  apiRequest.mockImplementation(async (path: string) => {
    if (path.startsWith("/api/v1/public/live-exam-categories")) return [];
    if (path.startsWith("/api/v1/public/live-exam-modules/m1/exams")) return exams;
    return { ...moduleRow, price: null };
  });

  renderWithQuery(<ModulePage door="mcq" moduleId="m1" />);

  // The module bar and the exam rows both say it.
  expect((await screen.findAllByText("Price not set yet.")).length).toBeGreaterThan(0);
  expect(screen.queryByRole("link", { name: "Buy whole module" })).not.toBeInTheDocument();
});
