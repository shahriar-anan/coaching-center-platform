import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SubmissionForm } from "@/components/staff/marking/submission-form";
import { fixtures, mockApi, renderWithProviders } from "../helpers";
import { paper } from "../ws9-mrk/fixtures";
import "./copy-setup";

// The PDF viewer loads pdf.js. This stub asks for the file the way the viewer does, so the request can be checked.
vi.mock("@/components/pdf/pdf-viewer", () => ({
  PdfViewer: ({ load }: { load: () => Promise<unknown> }) => {
    void load();
    return <p>খাতার ভিউয়ার</p>;
  },
}));

const VIEW = "দেখুন";

beforeEach(() => {
  window.localStorage.clear();
});

describe("marked PDF on the marking page", () => {
  it("shows the View button only when a marked PDF is stored", () => {
    mockApi({});
    const { unmount } = renderWithProviders(
      <SubmissionForm paper={paper({ evaluatedPdf: true })} canMark canPublish={false} />,
      { user: fixtures.masterAdmin },
    );
    expect(screen.getByRole("button", { name: VIEW })).toBeInTheDocument();
    unmount();

    const { unmount: unmountNone } = renderWithProviders(
      <SubmissionForm paper={paper({ evaluatedPdf: false })} canMark canPublish={false} />,
      { user: fixtures.masterAdmin },
    );
    expect(screen.queryByRole("button", { name: VIEW })).toBeNull();
    unmountNone();

    renderWithProviders(<SubmissionForm paper={paper({ evaluatedPdf: null })} canMark canPublish={false} />, {
      user: fixtures.masterAdmin,
    });
    expect(screen.queryByRole("button", { name: VIEW })).toBeNull();
  });

  it("opens the stored marked PDF from the evaluated-pdf route", async () => {
    const marked = paper({ evaluatedPdf: true });
    const api = mockApi({
      [`GET /api/v1/live-exam-participants/${marked.id}/evaluated-pdf`]: { ok: true },
    });
    renderWithProviders(<SubmissionForm paper={marked} canMark canPublish={false} />, { user: fixtures.masterAdmin });
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: VIEW }));

    expect(await screen.findByText("খাতার ভিউয়ার")).toBeInTheDocument();
    await waitFor(() =>
      expect(api.calls.some((call) => call.method === "GET" && call.path === `/api/v1/live-exam-participants/${marked.id}/evaluated-pdf`)).toBe(true),
    );
  });

  it("shows the View button to System Admin, who reads the paper without a form", () => {
    mockApi({});
    renderWithProviders(<SubmissionForm paper={paper({ evaluatedPdf: true })} canMark={false} canPublish={false} />, {
      user: fixtures.systemAdmin,
    });
    expect(screen.getByRole("button", { name: VIEW })).toBeInTheDocument();
  });
});
