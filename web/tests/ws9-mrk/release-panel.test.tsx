import { describe, expect, it } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ReleasePanel } from "@/components/staff/results/release-panel";
import { ErrorBody, fixtures, mockApi, renderWithProviders } from "../helpers";

const STATUS_GATED = { awaiting: 2, open: 0, drafts: 1, ready: 1, failedToSubmit: 1, canRelease: false };
const STATUS_OPEN = { awaiting: 0, open: 0, drafts: 3, ready: 1, failedToSubmit: 1, canRelease: true };
const RELEASE = "POST /api/v1/live-exams/e1/publish-results";
const STATUS = "GET /api/v1/live-exams/e1/release-status";

describe("release panel on the results page (Master Admin)", () => {
  it("shows the counts, disables release-all while papers await a mark, and keeps ready papers releasable", async () => {
    mockApi({ [STATUS]: STATUS_GATED });
    renderWithProviders(<ReleasePanel examId="e1" examTitle="রচনা পত্র" />, { user: fixtures.masterAdmin });

    expect(await screen.findByText("মূল্যায়ন বাকি: ২")).toBeInTheDocument();
    expect(screen.getByText("প্রকাশের জন্য প্রস্তুত: ১")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /সব ফল প্রকাশ/ })).toBeDisabled();
    expect(screen.getByRole("button", { name: /প্রস্তুত খাতার ফল প্রকাশ/ })).toBeEnabled();
  });

  it("releases only the ready papers when asked, with the ready query", async () => {
    const api = mockApi({ [STATUS]: STATUS_OPEN, [RELEASE]: { published: 1 } });
    renderWithProviders(<ReleasePanel examId="e1" examTitle="রচনা পত্র" />, { user: fixtures.masterAdmin });

    await userEvent.click(await screen.findByRole("button", { name: /প্রস্তুত খাতার ফল প্রকাশ/ }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "প্রকাশ করুন" }));

    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "POST" && call.path === "/api/v1/live-exams/e1/publish-results" && call.search === "?ready=true")).toBe(true),
    );
  });

  it("shows the server's counts when a release is refused", async () => {
    const refused = new ErrorBody(409, {
      code: "EXAM_MARKING_INCOMPLETE",
      message: "Every paper must be marked and every sitting closed before the results are released.",
      details: { awaiting: 2, open: 1 },
    } as { code: string; message: string });
    mockApi({ [STATUS]: STATUS_OPEN, [RELEASE]: refused });
    renderWithProviders(<ReleasePanel examId="e1" examTitle="রচনা পত্র" />, { user: fixtures.masterAdmin });

    await userEvent.click(await screen.findByRole("button", { name: /সব ফল প্রকাশ/ }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "প্রকাশ করুন" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("২টি খাতার নম্বর এখনো বাকি");
    expect(screen.getByRole("alert")).toHaveTextContent("১টি পরীক্ষা চলছে");
  });
});
