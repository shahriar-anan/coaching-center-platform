import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { MyAdmissionPage } from "@/components/student/my-admission/my-admission-page";
import { apiError, fixtures, mockApi, renderWithProviders, routerState } from "../helpers";
import { admission, due, examItem, packageItem, payment, schedule } from "./fixture";

// The routine viewer loads a PDF in the browser. Here it only reports the label and asks for the file once.
vi.mock("@/components/pdf/pdf-viewer", async () => {
  const React = await import("react");
  return {
    PdfViewer: ({ load, label }: { load: () => Promise<Blob>; label?: string }) => {
      React.useEffect(() => {
        void load().catch(() => undefined);
      }, [load]);
      return React.createElement("div", { "aria-label": label }, "routine viewer");
    },
  };
});

const ROUTINE = "GET /api/v1/me/admission/batch-routine.pdf";

function renderPage(admissionAnswer: unknown, feesAnswer?: unknown, itemsAnswer: unknown = []) {
  const routes: Record<string, unknown> = {
    "GET /api/v1/me/admission": admissionAnswer,
    "GET /api/v1/me/admission/items": itemsAnswer,
    [ROUTINE]: { routine: true },
    "GET /api/v1/me/admission/routine": [],
    "GET /api/v1/me/admissions/adm-1/results": { batchNameEn: null, batchNameBn: null, roll: null, exams: [] },
  };
  if (feesAnswer !== undefined) {
    routes["GET /api/v1/me/admission/fees"] = feesAnswer;
  }
  const api = mockApi(routes);
  renderWithProviders(<MyAdmissionPage />, { user: fixtures.student, locale: "en", route: "/my-admission" });
  return api;
}

describe("a student without an admission (D22)", () => {
  it("sees the not-found page, not an empty My admission page", async () => {
    renderPage(apiError(404, "RESOURCE_NOT_FOUND", "Resource not found."));

    expect(await screen.findByRole("heading", { name: "Page not found" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Payments" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "My form" })).not.toBeInTheDocument();
  });
});

describe("the page sections (plan 6.4)", () => {
  it("shows the state and the glance strip, then the sections in the order a student uses them", async () => {
    renderPage(admission("ONBOARDED"), schedule());

    expect(await screen.findByText("Active")).toBeInTheDocument();
    const headings = (await screen.findAllByRole("heading", { level: 2 })).map((h) => h.textContent);
    expect(headings).toEqual(["Routine", "Results", "Payments", "Exams and packages", "My form"]);
    const glance = screen.getByRole("region", { name: "At a glance" });
    expect(glance).toHaveTextContent("Next class");
    expect(glance).toHaveTextContent("Next payment");
    expect(glance).toHaveTextContent("Exams and packages");
    expect(await screen.findByText("Your exams and packages will appear here.")).toBeInTheDocument();
    expect(screen.getByText("Your routine will appear here")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View / print my form" })).toHaveAttribute("href", "/admission/print");
  });

  it("shows the next payment: the earliest unpaid due", async () => {
    renderPage(
      admission("ONBOARDED"),
      schedule({ dues: [due(1, "2026-11-15", 3000, 3000), due(2, "2026-12-15", 3000, 1000)] }),
    );

    // The next-payment tile of the glance strip shows what is still owed on the earliest unpaid due.
    const glance = await screen.findByRole("region", { name: "At a glance" });
    await waitFor(() => expect(glance).toHaveTextContent("৳2,000.00"));
    expect(glance).toHaveTextContent(/Due on 15 Dec/);
    expect(screen.getByText("Next payment")).toBeInTheDocument();
    // In the schedule the paid due says so, and the part-paid one is marked as the next, with what is left on it.
    expect(screen.getByText("Paid")).toBeInTheDocument();
    expect(screen.getByText("Next")).toBeInTheDocument();
    expect(screen.getByText("৳2,000.00 left")).toBeInTheDocument();
  });

  it("marks a closed admission as course ended, with the same history", async () => {
    renderPage(admission("CLOSED"), schedule());

    expect(await screen.findByText("Course ended")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Payments" })).toBeInTheDocument();
  });
});

describe("the header (batch and dates)", () => {
  it("shows the batch name and the admitted and end dates when present", async () => {
    renderPage(
      admission("ONBOARDED", {
        batchNameEn: "Batch 3 (Evening)",
        batchNameBn: "ব্যাচ ৩ (সন্ধ্যা)",
        admittedOn: "2026-11-01",
        courseEndsOn: "2027-03-31",
        monthlyFee: 3000,
      }),
      schedule(),
    );

    expect(await screen.findByText("Batch 3 (Evening)")).toBeInTheDocument();
    expect(screen.getByText("Batch")).toBeInTheDocument();
    expect(screen.getByText("Admitted on")).toBeInTheDocument();
    expect(screen.getByText("1 November")).toBeInTheDocument();
    expect(screen.getByText("Course ends on")).toBeInTheDocument();
    expect(screen.getByText("31 March 2027")).toBeInTheDocument();
  });

  it("shows the monthly fee in the payments card, not in the header", async () => {
    renderPage(admission("ONBOARDED", { monthlyFee: 3000 }), schedule());

    // The schedule also shows 3,000 per instalment, so check the fee inside its own block in the payments card.
    expect(await screen.findByText("Course fee (per month)")).toBeInTheDocument();
    expect(screen.getByText("Course fee (per month)").parentElement).toHaveTextContent("৳3,000.00");
  });

  it("leaves out the batch and date rows the API does not give yet", async () => {
    renderPage(admission("ONBOARDED"), schedule());

    expect(await screen.findByText("Active")).toBeInTheDocument();
    expect(screen.queryByText("Batch")).not.toBeInTheDocument();
    expect(screen.queryByText("Admitted on")).not.toBeInTheDocument();
    expect(screen.queryByText("Course ends on")).not.toBeInTheDocument();
  });
});

describe("exams and packages (items)", () => {
  it("lists an exam with a link and a package with its exam count, each with its access date", async () => {
    renderPage(
      admission("ONBOARDED"),
      schedule(),
      [examItem("exam-1", "Physics Mock 1"), packageItem("mod-1", "Admission Package", 12)],
    );

    const exam = await screen.findByRole("link", { name: /Physics Mock 1/ });
    expect(exam).toHaveAttribute("href", "/exam/exam-1");
    expect(screen.getByText("Exam")).toBeInTheDocument();

    const pkg = screen.getByRole("link", { name: /Admission Package/ });
    expect(pkg).toHaveAttribute("href", "/mcq/m/mod-1");
    expect(pkg).toHaveTextContent("12 exams");

    expect(screen.getAllByText("Access until 28 February 2027")).toHaveLength(2);
    expect(screen.queryByText("Your exams and packages will appear here.")).not.toBeInTheDocument();
  });
});

describe("routine (hasRoutine)", () => {
  it("shows the empty text and asks for no PDF when the batch has no routine", async () => {
    const api = renderPage(admission("ONBOARDED", { hasRoutine: false }), schedule());

    expect(await screen.findByText("Your routine will appear here")).toBeInTheDocument();
    expect(screen.queryByText("routine viewer")).not.toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/me/admission/batch-routine.pdf")).toBe(false);
  });

  it("opens the routine PDF viewer from the Open the PDF button when the batch has one", async () => {
    const api = renderPage(admission("ONBOARDED", { hasRoutine: true }), schedule());

    expect(await screen.findByRole("button", { name: "Open the PDF" })).toBeInTheDocument();
    expect(screen.queryByText("routine viewer")).not.toBeInTheDocument();
    expect(api.calls.some((call) => call.path === "/api/v1/me/admission/batch-routine.pdf")).toBe(false);
    expect(screen.queryByText("Your routine will appear here")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Open the PDF" }));
    expect(await screen.findByText("routine viewer")).toBeInTheDocument();
    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/me/admission/batch-routine.pdf")).toBe(true));
  });
});

describe("a closed admission is read-only history", () => {
  it("shows the history with no pay buttons", async () => {
    renderPage(
      admission("CLOSED", { batchNameEn: "Batch 3 (Evening)", hasRoutine: true }),
      schedule({ payments: [payment("p-1", 1000, "RCPT-0001")] }),
      [examItem("exam-1", "Physics Mock 1")],
    );

    expect(await screen.findByText("Course ended")).toBeInTheDocument();
    expect(await screen.findByText(/RCPT-0001/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Physics Mock 1/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download receipt" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /pay/i })).not.toBeInTheDocument();
    // The glance strip's "Payments" link jumps to the history; only a link that starts with "Pay" would be an action.
    expect(screen.queryByRole("link", { name: /^pay/i })).not.toBeInTheDocument();
  });
});

describe("the overdue line (D21)", () => {
  it("appears in the warning colour when a due has passed unpaid", async () => {
    renderPage(
      admission("ONBOARDED"),
      schedule({ dues: [due(1, "2026-01-15", 3000, 0, true)] }),
    );

    const line = await screen.findByText(/was due on 15 Jan/);
    expect(line).toHaveTextContent("৳3,000.00 was due on 15 Jan");
    expect(line).toHaveClass("text-warn");
    expect(screen.getByText("A payment is overdue")).toBeInTheDocument();
    expect(screen.getByText("Overdue since 15 January")).toBeInTheDocument();
  });

  it("does not appear when nothing is overdue", async () => {
    renderPage(admission("ONBOARDED"), schedule({ dues: [due(1, "2026-12-15", 3000, 0)] }));

    expect(await screen.findByText("Next payment")).toBeInTheDocument();
    expect(screen.queryByText(/was due on/)).not.toBeInTheDocument();
  });
});

describe("payments (approved only)", () => {
  it("does not list a payment still waiting for approval", async () => {
    renderPage(
      admission("ONBOARDED"),
      schedule({
        payments: [payment("p-1", 1000, "RCPT-0001"), payment("p-2", 1500, "RCPT-PENDING-1", "PENDING")],
      }),
    );

    expect(await screen.findByText(/RCPT-0001/)).toBeInTheDocument();
    expect(screen.queryByText(/RCPT-PENDING-1/)).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Download receipt" })).toHaveLength(1);
  });

  it("sends a student with a pending admission to the form, and shows none of the sections", async () => {
    renderPage(admission("AWAITING_FORM"));

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/admission/form"));
    expect(screen.queryByRole("heading", { name: "Payments" })).not.toBeInTheDocument();
  });
});
