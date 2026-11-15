import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import LeaderboardRoute from "@/app/(site)/offline-activities/results/[admissionId]/[classId]/page";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";
import { admissionResults, classRows, examResult, leaderboard } from "./fixture";

const BOARD = "GET /api/v1/me/admissions/adm-1/results/class-1";
const RESULTS = "GET /api/v1/me/admissions/adm-1/results";

/** A promise React reads at once, as Next does for route params, so `use()` does not suspend in a test. */
function resolved<T>(value: T): Promise<T> {
  const promise = Promise.resolve(value) as Promise<T> & { status?: string; value?: T };
  promise.status = "fulfilled";
  promise.value = value;
  return promise;
}

/** The leaderboard route for the viewer's admission `adm-1` and exam `class-1`. */
function renderBoard(board: unknown) {
  const api = mockApi({ [BOARD]: board, [RESULTS]: admissionResults([examResult()]) });
  renderWithProviders(<LeaderboardRoute params={resolved({ admissionId: "adm-1", classId: "class-1" })} />, {
    user: fixtures.student,
    locale: "en",
    route: "/offline-activities/results/adm-1/class-1",
  });
  return api;
}

/** The list's rows in the order they are shown. */
async function listItems() {
  const list = await screen.findByRole("list", { name: "Leaderboard" });
  return within(list).getAllByRole("listitem");
}

describe("the exam leaderboard (plan 7.11)", () => {
  it("shows Your result first, with the total out of full marks and the rank among the ranked students", async () => {
    renderBoard(leaderboard(classRows()));

    const yours = await screen.findByRole("heading", { name: "Your result" });
    expect(screen.getByText("45 / 50")).toBeInTheDocument();
    // Four students are ranked; the absent one is not counted.
    expect(screen.getByText("2nd of 4")).toBeInTheDocument();
    expect(screen.getByText("MCQ 45")).toBeInTheDocument();
    const list = screen.getByRole("heading", { name: "Leaderboard" });
    expect(yours.compareDocumentPosition(list) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("highlights the student's own row with aria-current and no other row", async () => {
    renderBoard(leaderboard(classRows()));

    const own = (await screen.findByText("Rahim Uddin")).closest("li") as HTMLElement;
    expect(own).toHaveAttribute("aria-current", "true");
    expect(within(own).getByText("You")).toBeInTheDocument();
    expect(within(own).getByText("Roll 192001")).toBeInTheDocument();

    const other = screen.getByText("Nadia Islam").closest("li") as HTMLElement;
    expect(other).not.toHaveAttribute("aria-current");
    expect((await listItems()).filter((item) => item.hasAttribute("aria-current"))).toHaveLength(1);
  });

  it("lists the absent student last, with Absent and no rank", async () => {
    renderBoard(leaderboard(classRows()));

    const items = await listItems();
    expect(items).toHaveLength(5);
    expect(items[0]).toHaveTextContent("Nadia Islam");
    const absent = items[items.length - 1];
    expect(absent).toHaveTextContent("Tanvir Hasan");
    expect(absent).toHaveTextContent("Absent");
    expect(absent).not.toHaveTextContent("45");
    expect(within(absent).getByText("—")).toBeInTheDocument();
  });

  it("tags a zero row might be absent", async () => {
    renderBoard(leaderboard(classRows()));

    const zero = (await screen.findByText("Sumi Akter")).closest("li") as HTMLElement;
    expect(within(zero).getByText("might be absent")).toBeInTheDocument();
  });

  it("says so when the student has no row in this exam", async () => {
    renderBoard(leaderboard(classRows().filter((row) => !row.mine), { myRoll: null }));

    expect(await screen.findByText("You are not in this exam's sheet.")).toBeInTheDocument();
    expect(document.querySelector('[aria-current="true"]')).toBeNull();
  });

  it("shows the not-found page when the admission is not the student's own", async () => {
    renderBoard(apiError(404, "RESOURCE_NOT_FOUND", "Resource not found."));

    expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument();
  });
});
