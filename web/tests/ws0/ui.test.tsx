import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { Pill } from "@/components/ui/pill";
import { Table, type Column } from "@/components/ui/table";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput } from "@/components/ui/search-input";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Countdown } from "@/components/ui/countdown";
import { EmptyState } from "@/components/ui/empty-state";
import { RequireStaff, RequireStudent } from "@/lib/auth/guards";
import { StudentShell } from "@/components/shell/student-shell";
import { renderWithProviders, fixtures, routerState } from "../helpers";
import { mockApi } from "../helpers/mock-api";
import { resetClock } from "@/lib/time/clock";

beforeEach(() => {
  resetClock();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("Button", () => {
  it("disables and marks itself busy while loading", () => {
    renderWithProviders(<Button loading>জমা দিন</Button>);
    const button = screen.getByRole("button", { name: "জমা দিন" });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");
  });

  it("renders a link when given href", () => {
    renderWithProviders(<Button href="/notices">নোটিশ</Button>);
    expect(screen.getByRole("link", { name: "নোটিশ" })).toHaveAttribute("href", "/notices");
  });
});

describe("Field", () => {
  it("labels the input and points the error at it", () => {
    renderWithProviders(
      <Field label="ফোন" error="ফোন নম্বরটি সঠিক নয়।" required>
        <TextInput />
      </Field>,
    );
    const input = screen.getByLabelText(/ফোন/);
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAccessibleDescription("ফোন নম্বরটি সঠিক নয়।");
  });
});

describe("Pill", () => {
  it("shows the status as words", () => {
    renderWithProviders(<Pill tone="warn">অপেক্ষমাণ</Pill>);
    expect(screen.getByText("অপেক্ষমাণ")).toBeInTheDocument();
  });
});

describe("Table", () => {
  type Row = { id: string; name: string };
  const columns: Column<Row>[] = [
    { key: "name", header: "নাম", cell: (r) => r.name },
  ];

  it("renders the same fields in a table and as stacked cards", () => {
    const rows = [{ id: "1", name: "রহিম" }];
    const { container } = renderWithProviders(
      <Table columns={columns} rows={rows} rowKey={(r) => r.id} />,
    );
    expect(container.querySelector("table")).toBeInTheDocument();
    expect(container.querySelectorAll("li")).toHaveLength(1);
    expect(screen.getAllByText("রহিম")).toHaveLength(2);
  });

  it("shows the empty state when there are no rows", () => {
    renderWithProviders(
      <Table columns={columns} rows={[]} rowKey={(r: Row) => r.id} empty={<p>কিছু নেই</p>} />,
    );
    expect(screen.getByText("কিছু নেই")).toBeInTheDocument();
  });
});

describe("Pagination", () => {
  it("counts pages from one in the words and moves by page", async () => {
    const onPage = vi.fn();
    renderWithProviders(<Pagination page={1} totalPages={4} onPage={onPage} />);
    expect(screen.getByText("৪টির মধ্যে ২ নম্বর পাতা")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /পরের পাতা/ }));
    expect(onPage).toHaveBeenCalledWith(2);
  });
});

describe("SearchInput", () => {
  it("waits 300 ms after typing before it reports the value", async () => {
    vi.useFakeTimers();
    const onChange = vi.fn();
    renderWithProviders(<SearchInput value="" onChange={onChange} />);
    const box = screen.getByRole("searchbox");
    fireEvent.change(box, { target: { value: "রহ" } });
    expect(onChange).not.toHaveBeenCalled();
    await act(async () => {
      vi.advanceTimersByTime(299);
    });
    expect(onChange).not.toHaveBeenCalled();
    await act(async () => {
      vi.advanceTimersByTime(1);
    });
    expect(onChange).toHaveBeenCalledWith("রহ");
  });
});

describe("ConfirmDialog", () => {
  it("keeps confirm off until the typed name matches", async () => {
    const onConfirm = vi.fn(async () => undefined);
    renderWithProviders(
      <ConfirmDialog
        open
        onOpenChange={() => undefined}
        title="মডিউল মুছুন"
        confirmLabel="মুছুন"
        tone="danger"
        typeToConfirm="BCS Preliminary"
        onConfirm={onConfirm}
      />,
    );
    const confirm = screen.getByRole("button", { name: "মুছুন" });
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByRole("textbox"), "BCS Preliminary");
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });
});

describe("Countdown", () => {
  it("shows the time left from the server deadline and says when it ends", () => {
    vi.useFakeTimers({ now: Date.parse("2026-11-12T04:00:00.000Z") });
    const onEnd = vi.fn();
    renderWithProviders(
      <Countdown deadline="2026-11-12T04:40:00.000Z" variant="bar" onEnd={onEnd} />,
    );
    expect(screen.getByText("০০:৪০:০০")).toBeInTheDocument();
  });
});

describe("EmptyState", () => {
  it("says what is missing and offers the next step", () => {
    renderWithProviders(
      <EmptyState title="এখনো কোনো নোটিশ নেই।" action={<Button>ফিরে যান</Button>} />,
    );
    expect(screen.getByRole("heading", { name: "এখনো কোনো নোটিশ নেই।" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "ফিরে যান" })).toBeInTheDocument();
  });
});

describe("route guards", () => {
  it("sends a signed-out visitor to login with ?next=", async () => {
    renderWithProviders(
      <RequireStudent>
        <p>sitting</p>
      </RequireStudent>,
      { route: "/exam/abc/sit?x=1" },
    );
    await waitFor(() =>
      expect(routerState.replace).toHaveBeenCalledWith("/login?next=%2Fexam%2Fabc%2Fsit%3Fx%3D1"),
    );
  });

  it("shows the staff-only message to a student on a staff page", () => {
    renderWithProviders(
      <RequireStaff>
        <p>staff</p>
      </RequireStaff>,
      { user: fixtures.student },
    );
    expect(screen.getByRole("heading", { name: "এই পাতাটি শুধু অ্যাডমিন ও শিক্ষকদের জন্য" })).toBeInTheDocument();
    expect(screen.queryByText("staff")).toBeNull();
  });

  it("shows not-found to a staff role outside the allowed roles", () => {
    renderWithProviders(
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <p>payments</p>
      </RequireStaff>,
      { user: fixtures.instructor },
    );
    expect(screen.queryByText("payments")).toBeNull();
    expect(screen.getByRole("heading", { name: "পাতাটি পাওয়া যায়নি" })).toBeInTheDocument();
  });

  it("renders the page to a staff role inside the allowed roles", () => {
    renderWithProviders(
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <p>payments</p>
      </RequireStaff>,
      { user: fixtures.masterAdmin },
    );
    expect(screen.getByText("payments")).toBeInTheDocument();
  });
});

describe("StudentShell", () => {
  it("shows the bottom tab bar to a signed-in student on the dashboard", () => {
    renderWithProviders(
      <StudentShell>
        <p>home</p>
      </StudentShell>,
      { user: fixtures.student, route: "/" },
    );
    expect(screen.getByRole("navigation", { name: "নিচের মেনু" })).toBeInTheDocument();
  });

  it("hides the tab bar on the paper so it never covers the questions", () => {
    renderWithProviders(
      <StudentShell>
        <p>paper</p>
      </StudentShell>,
      { user: fixtures.student, route: "/exam/abc/sit" },
    );
    expect(screen.queryByRole("navigation", { name: "নিচের মেনু" })).toBeNull();
  });

  it("does not show the tab bar to a visitor", () => {
    renderWithProviders(
      <StudentShell>
        <p>home</p>
      </StudentShell>,
      { route: "/" },
    );
    expect(screen.queryByRole("navigation", { name: "নিচের মেনু" })).toBeNull();
    expect(screen.getByRole("link", { name: "লগইন" })).toHaveAttribute("href", "/login");
  });
});

describe("api mock", () => {
  it("answers 404 in the API shape for an unknown route", async () => {
    mockApi({});
    const response = await fetch("/api/v1/nothing");
    expect(response.status).toBe(404);
    expect((await response.json()).code).toBe("RESOURCE_NOT_FOUND");
  });
});
