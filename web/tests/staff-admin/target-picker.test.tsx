import { useState } from "react";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TargetPicker, type PickedTarget } from "@/components/staff/target-picker";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";
import { SUB_CATEGORY_ID, bigExamList, categoryTree, examRow, pageOf, packageRow } from "./picker-data";
import "./copy-setup";

/** The accessible name of an exam row starts with its title; the category and type follow it with no space. */
const examName = (n: number) => new RegExp(`^Mock exam ${n}(?!\\d)`);

const EXAMS = "GET /api/v1/live-exams";
const PACKAGES = "GET /api/v1/live-exam-modules";
const CATEGORIES = "GET /api/v1/live-exam-categories";

/** The request strings the mock saw for one path, such as "?page=0&size=10&q=Bar". */
function searches(calls: { method: string; path: string; search: string }[], path: string) {
  return calls.filter((call) => call.method === "GET" && call.path === path).map((call) => call.search);
}

/** A picker that keeps its value, as a screen does. `onChange` is a spy so a test can read every choice. */
function Harness({
  kind,
  status,
  hide,
  initial = null,
  onChange = () => undefined,
  onKindChange,
}: {
  kind: "package" | "exam" | "either";
  status?: string;
  hide?: string[];
  initial?: PickedTarget | null;
  onChange?: (next: PickedTarget | null) => void;
  onKindChange?: (kind: "package" | "exam") => void;
}) {
  const [value, setValue] = useState<PickedTarget | null>(initial);
  return (
    <TargetPicker
      kind={kind}
      status={status}
      hide={hide}
      value={value}
      label="Enrol into"
      onKindChange={onKindChange}
      onChange={(next) => {
        onChange(next);
        setValue(next);
      }}
    />
  );
}

describe("TargetPicker: search and filters", () => {
  it("sends the search text as q once typing pauses, never the partial words", async () => {
    const api = mockApi({ [EXAMS]: (req: { url: URL }) => pageOf(req.url, bigExamList), [CATEGORIES]: categoryTree });
    renderWithProviders(<Harness kind="exam" />, { user: fixtures.masterAdmin, locale: "en" });

    await userEvent.type(screen.getByLabelText("Search by title"), "Bar");

    await waitFor(() => expect(searches(api.calls, "/api/v1/live-exams").some((s) => s.includes("q=Bar"))).toBe(true));
    expect(searches(api.calls, "/api/v1/live-exams").some((s) => /[?&]q=B(&|$)|[?&]q=Ba(&|$)/.test(s))).toBe(false);
  });

  it("sends the category as categoryId", async () => {
    const api = mockApi({ [EXAMS]: (req: { url: URL }) => pageOf(req.url, bigExamList), [CATEGORIES]: categoryTree });
    renderWithProviders(<Harness kind="exam" />, { user: fixtures.masterAdmin, locale: "en" });

    await screen.findByRole("option", { name: /Preliminary/ });
    await userEvent.selectOptions(screen.getByLabelText("Category"), SUB_CATEGORY_ID);

    await waitFor(() => expect(searches(api.calls, "/api/v1/live-exams").some((s) => s.includes(`categoryId=${SUB_CATEGORY_ID}`))).toBe(true));
  });

  it("sends the exam type as type, and a package list never sends a type", async () => {
    const api = mockApi({
      [EXAMS]: (req: { url: URL }) => pageOf(req.url, bigExamList),
      [PACKAGES]: (req: { url: URL }) => pageOf(req.url, [packageRow(1)]),
      [CATEGORIES]: categoryTree,
    });
    const { unmount } = renderWithProviders(<Harness kind="exam" />, { user: fixtures.masterAdmin, locale: "en" });

    await userEvent.selectOptions(screen.getByLabelText("Type"), "WRITTEN");
    await waitFor(() => expect(searches(api.calls, "/api/v1/live-exams").some((s) => s.includes("type=WRITTEN"))).toBe(true));
    unmount();

    renderWithProviders(<Harness kind="package" />, { user: fixtures.masterAdmin, locale: "en" });
    expect(screen.queryByLabelText("Type")).not.toBeInTheDocument();
    await screen.findByRole("radio", { name: /Package 1/ });
    expect(searches(api.calls, "/api/v1/live-exam-modules").every((s) => !s.includes("type="))).toBe(true);
  });

  it("sends the status filter the screen asks for", async () => {
    const api = mockApi({ [EXAMS]: (req: { url: URL }) => pageOf(req.url, bigExamList), [CATEGORIES]: categoryTree });
    renderWithProviders(<Harness kind="exam" status="PUBLISHED" />, { user: fixtures.masterAdmin, locale: "en" });

    await screen.findByRole("radio", { name: examName(1) });
    expect(searches(api.calls, "/api/v1/live-exams").every((s) => s.includes("status=PUBLISHED"))).toBe(true);
  });

  it("removes the statuses it is told to hide, on the client", async () => {
    mockApi({
      [EXAMS]: (req: { url: URL }) => pageOf(req.url, [examRow(1), examRow(2, { status: "DRAFT" }), examRow(3)]),
      [CATEGORIES]: categoryTree,
    });
    renderWithProviders(<Harness kind="exam" hide={["DRAFT"]} />, { user: fixtures.masterAdmin, locale: "en" });

    const rows = await screen.findAllByRole("radio");
    expect(rows).toHaveLength(2);
    expect(screen.queryByRole("radio", { name: examName(2) })).not.toBeInTheDocument();
  });
});

describe("TargetPicker: paging", () => {
  it("asks for 10 rows only, and fetches the next page only on Load more (120 exams)", async () => {
    const api = mockApi({ [EXAMS]: (req: { url: URL }) => pageOf(req.url, bigExamList), [CATEGORIES]: categoryTree });
    renderWithProviders(<Harness kind="exam" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findAllByRole("radio")).toHaveLength(10);
    expect(searches(api.calls, "/api/v1/live-exams")).toEqual(["?page=0&size=10"]);

    await userEvent.click(screen.getByRole("button", { name: "Load more" }));

    await waitFor(() => expect(screen.getAllByRole("radio")).toHaveLength(20));
    expect(searches(api.calls, "/api/v1/live-exams")).toEqual(["?page=0&size=10", "?page=1&size=10"]);
  });

  it("shows the exact number of exams in a package row, and no type pill on a package", async () => {
    mockApi({ [PACKAGES]: (req: { url: URL }) => pageOf(req.url, [packageRow(1)]), [CATEGORIES]: categoryTree });
    renderWithProviders(<Harness kind="package" />, { user: fixtures.masterAdmin, locale: "en" });

    const row = await screen.findByRole("radio", { name: /Package 1/ });
    expect(row).toHaveTextContent("5 exams");
    expect(row).not.toHaveTextContent("MCQ");
    expect(row).not.toHaveTextContent("Written");
  });

  it("shows the type as a pill on an exam row", async () => {
    mockApi({ [EXAMS]: (req: { url: URL }) => pageOf(req.url, [examRow(2), examRow(3)]), [CATEGORIES]: categoryTree });
    renderWithProviders(<Harness kind="exam" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByRole("radio", { name: examName(2) })).toHaveTextContent("MCQ");
    expect(screen.getByRole("radio", { name: examName(3) })).toHaveTextContent("Written");
  });
});

describe("TargetPicker: choosing", () => {
  it("chooses a row, collapses to the chosen line, and Change clears it and focuses the search", async () => {
    mockApi({ [EXAMS]: (req: { url: URL }) => pageOf(req.url, bigExamList), [CATEGORIES]: categoryTree });
    const onChange = vi.fn();
    renderWithProviders(<Harness kind="exam" onChange={onChange} />, { user: fixtures.masterAdmin, locale: "en" });

    await userEvent.click(await screen.findByRole("radio", { name: examName(3) }));

    expect(onChange).toHaveBeenLastCalledWith({ kind: "exam", id: "e3", title: "Mock exam 3" });
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
    expect(screen.getByText("Chosen")).toBeInTheDocument();
    expect(screen.getByText("Mock exam 3")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Change" }));

    expect(onChange).toHaveBeenLastCalledWith(null);
    await waitFor(() => expect(screen.getByLabelText("Search by title")).toHaveFocus());
    expect(screen.queryByText("Chosen")).not.toBeInTheDocument();
  });

  it("shows the chosen line straight away for a preset target, and asks for no list", async () => {
    const api = mockApi({ [EXAMS]: (req: { url: URL }) => pageOf(req.url, bigExamList), [CATEGORIES]: categoryTree });
    renderWithProviders(
      <Harness kind="exam" initial={{ kind: "exam", id: "e5", title: "Mock exam 5" }} />,
      { user: fixtures.masterAdmin, locale: "en" },
    );

    expect(screen.getByText("Mock exam 5")).toBeInTheDocument();
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
    expect(searches(api.calls, "/api/v1/live-exams")).toEqual([]);
  });

  it("moves between rows with the arrow keys, and Enter chooses the row in focus", async () => {
    mockApi({ [EXAMS]: (req: { url: URL }) => pageOf(req.url, bigExamList), [CATEGORIES]: categoryTree });
    const onChange = vi.fn();
    renderWithProviders(<Harness kind="exam" onChange={onChange} />, { user: fixtures.masterAdmin, locale: "en" });

    const rows = await screen.findAllByRole("radio");
    rows[0].focus();
    await userEvent.keyboard("{ArrowDown}");

    expect(rows[1]).toHaveFocus();
    expect(rows[1]).toHaveAttribute("aria-checked", "true");
    expect(rows[0]).toHaveAttribute("aria-checked", "false");

    await userEvent.keyboard("{End}");
    expect(rows[9]).toHaveFocus();
    await userEvent.keyboard("{ArrowDown}");
    expect(rows[0]).toHaveFocus();

    await userEvent.keyboard("{ArrowDown}{Enter}");
    expect(onChange).toHaveBeenLastCalledWith({ kind: "exam", id: "e2", title: "Mock exam 2" });
  });
});

describe("TargetPicker: states", () => {
  it("says so when nothing matches", async () => {
    mockApi({ [EXAMS]: (req: { url: URL }) => pageOf(req.url, []), [CATEGORIES]: categoryTree });
    renderWithProviders(<Harness kind="exam" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByText("No exams match.")).toBeInTheDocument();
  });

  it("shows an error with a retry that asks again", async () => {
    let failures = 1;
    const api = mockApi({
      [EXAMS]: (req: { url: URL }) => {
        if (failures > 0) {
          failures -= 1;
          return apiError(500, "INTERNAL_ERROR");
        }
        return pageOf(req.url, [examRow(1)]);
      },
      [CATEGORIES]: categoryTree,
    });
    renderWithProviders(<Harness kind="exam" />, { user: fixtures.masterAdmin, locale: "en" });

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByRole("radio", { name: examName(1) })).toBeInTheDocument();
    expect(searches(api.calls, "/api/v1/live-exams")).toHaveLength(2);
  });

  it("switches between packages and exams, and reports the switch", async () => {
    const api = mockApi({
      [EXAMS]: (req: { url: URL }) => pageOf(req.url, [examRow(1)]),
      [PACKAGES]: (req: { url: URL }) => pageOf(req.url, [packageRow(1)]),
      [CATEGORIES]: categoryTree,
    });
    const onKindChange = vi.fn();
    renderWithProviders(<Harness kind="either" onKindChange={onKindChange} />, { user: fixtures.masterAdmin, locale: "en" });

    await screen.findByRole("radio", { name: /Package 1/ });
    expect(screen.queryByLabelText("Type")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Exam" }));

    expect(onKindChange).toHaveBeenLastCalledWith("exam");
    expect(await screen.findByRole("radio", { name: examName(1) })).toBeInTheDocument();
    expect(screen.getByLabelText("Type")).toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/live-exams")).toBe(true);
  });
});
