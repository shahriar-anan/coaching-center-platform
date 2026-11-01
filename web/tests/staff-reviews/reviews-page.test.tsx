import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReviewsPage } from "@/components/staff/reviews/reviews-page";
import type { StaffReviewRow } from "@/lib/api/exam/staff-reviews";
import type { Me } from "@/lib/api/exam/types";
import { fixtures, renderWithProviders } from "../helpers";

// The URL is the page's state, so the navigation mock keeps a real search string that updates on replace and re-renders.
const nav = vi.hoisted(() => ({
  pathname: "/staff/reviews",
  search: "",
  replaced: [] as string[],
  listeners: new Set<() => void>(),
}));

vi.mock("next/navigation", async () => {
  const react = await import("react");
  const subscribe = (listener: () => void) => {
    nav.listeners.add(listener);
    return () => {
      nav.listeners.delete(listener);
    };
  };
  const replace = (url: string) => {
    const [pathname, search = ""] = url.split("?");
    nav.replaced.push(url);
    nav.pathname = pathname || "/";
    nav.search = search;
    nav.listeners.forEach((listener) => listener());
  };
  const router = { replace, push: replace, back: () => undefined, refresh: () => undefined, prefetch: () => undefined };
  return {
    useRouter: () => router,
    usePathname: () => nav.pathname,
    useSearchParams: () => new URLSearchParams(react.useSyncExternalStore(subscribe, () => nav.search)),
    useParams: () => ({}),
    redirect: () => undefined,
    notFound: () => undefined,
  };
});

// The picker has its own tests; here it is a stand-in that makes one choice.
vi.mock("@/components/staff/target-picker", async () => {
  const { createElement: h } = await import("react");
  return {
    TargetPicker: (props: { kind: string; value: { id: string } | null; onChange: (value: unknown) => void }) =>
      h(
        "div",
        null,
        h("p", null, `kind:${props.kind}`),
        h(
          "button",
          { type: "button", onClick: () => props.onChange({ kind: "exam", id: "exam-2", title: "Other exam" }) },
          "Pick other exam",
        ),
        h("span", null, props.value ? `chosen:${props.value.id}` : "none"),
      ),
  };
});

type Call = { method: string; path: string; search: string; body: unknown };

class Failure {
  constructor(
    readonly status: number,
    readonly body: unknown,
  ) {}
}

function row(overrides: Partial<StaffReviewRow> = {}): StaffReviewRow {
  return {
    id: "pending-1",
    kind: "exam",
    targetId: "exam-1",
    targetTitleEn: "Penal Code Mock",
    targetTitleBn: "দণ্ডবিধি মক",
    studentId: "student-1",
    studentName: "Nusrat Jahan",
    rating: 5,
    comment: "Clear paper",
    status: "PENDING",
    hideReason: null,
    featured: false,
    createdAt: "2026-10-20T06:20:00Z",
    updatedAt: "2026-10-20T06:20:00Z",
    moderatedAt: null,
    ...overrides,
  };
}

const ROWS: StaffReviewRow[] = [
  row({ id: "pending-1", studentName: "Nusrat Jahan", status: "PENDING", comment: "Clear paper" }),
  row({ id: "approved-1", studentName: "Rafiq Hasan", status: "APPROVED", rating: 4, comment: "Good mock" }),
  row({ id: "approved-stars", studentName: "Farhana Akter", status: "APPROVED", rating: 3, comment: null }),
  row({ id: "hidden-1", studentName: "Karim Uddin", status: "HIDDEN", rating: 1, comment: "Bad", hideReason: "Spam" }),
];

const COUNTS = { pending: 2, approved: 3, hidden: 1, featured: 1 };

const EXAM = { id: "exam-1", titleEn: "Penal Code Mock", titleBn: "দণ্ডবিধি মক" };

let calls: Call[] = [];

function listPage(url: URL, rows: StaffReviewRow[] = ROWS) {
  const status = url.searchParams.get("status");
  const featured = url.searchParams.get("featured");
  const content = rows.filter((r) => (!status || r.status === status) && (featured !== "true" || r.featured));
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

/** The default answers of the staff reviews API. A test can answer some calls itself and fall back to these. */
function defaultAnswer({ method, path, url, body }: { method: string; path: string; url: URL; body: unknown }): unknown {
  if (method === "GET" && path === "/api/v1/staff/reviews") {
    return listPage(url);
  }
  if (method === "GET" && path === "/api/v1/staff/reviews/counts") {
    return COUNTS;
  }
  if (method === "GET" && path === "/api/v1/live-exams/exam-1") {
    return EXAM;
  }
  const status = /^\/api\/v1\/staff\/reviews\/([^/]+)\/status$/.exec(path);
  if (method === "PUT" && status) {
    const found = ROWS.find((r) => r.id === status[1]) ?? row({ id: status[1] });
    const input = body as { status: string; reason?: string };
    return {
      ...found,
      status: input.status,
      hideReason: input.status === "HIDDEN" ? (input.reason ?? null) : null,
      featured: input.status === "HIDDEN" ? false : found.featured,
    };
  }
  const feature = /^\/api\/v1\/staff\/reviews\/([^/]+)\/featured$/.exec(path);
  if (method === "PUT" && feature) {
    const found = ROWS.find((r) => r.id === feature[1]) ?? row({ id: feature[1] });
    return { ...found, featured: (body as { featured: boolean }).featured };
  }
  return new Failure(404, { code: "RESOURCE_NOT_FOUND", message: "Resource not found.", timestamp: "", requestId: "t" });
}

/** Stubs fetch. Every call is recorded; `answer` returns the body, or a Failure for an error status. */
function serve(answer: (call: { method: string; path: string; url: URL; body: unknown }) => unknown = defaultAnswer) {
  calls = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      const method = (init?.method ?? "GET").toUpperCase();
      const body = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
      calls.push({ method, path: url.pathname, search: url.search, body });
      const reply = answer({ method, path: url.pathname, url, body });
      const json = { "Content-Type": "application/json" };
      if (reply instanceof Failure) {
        return new Response(JSON.stringify(reply.body), { status: reply.status, headers: json });
      }
      return new Response(JSON.stringify(reply ?? null), { status: 200, headers: json });
    }),
  );
}

function listCalls() {
  return calls.filter((c) => c.method === "GET" && c.path === "/api/v1/staff/reviews");
}

function lastListSearch() {
  return listCalls().at(-1)?.search ?? "";
}

function route(url: string) {
  const [pathname, search = ""] = url.split("?");
  nav.pathname = pathname || "/staff/reviews";
  nav.search = search;
}

function renderPage(user: Me = fixtures.masterAdmin) {
  return renderWithProviders(<ReviewsPage />, { user, locale: "en" });
}

beforeEach(() => {
  route("/staff/reviews");
  nav.replaced.length = 0;
  serve();
});

describe("the Reviews page: tabs and counts", () => {
  it("shows the counts on the strip and the tabs, and opens on Waiting", async () => {
    renderPage();
    expect(await screen.findByText("Nusrat Jahan")).toBeInTheDocument();

    const waiting = screen.getByRole("tab", { name: /Waiting/ });
    expect(waiting).toHaveAttribute("aria-selected", "true");
    expect(waiting).toHaveTextContent("2");
    expect(screen.getByRole("tab", { name: /Published/ })).toHaveTextContent("3");
    expect(screen.getByRole("tab", { name: /Hidden/ })).toHaveTextContent("1");
    expect(screen.getByRole("tab", { name: /All/ })).toHaveTextContent("6");

    const strip = screen.getByText("Featured").closest("dl");
    expect(strip?.textContent).toBe("Waiting2Published3Hidden1Featured1");
    expect(lastListSearch()).toContain("status=PENDING");
  });

  it("a tab changes the list request and the page URL, and Waiting leaves the URL clean", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Nusrat Jahan");

    await user.click(screen.getByRole("tab", { name: /Published/ }));
    expect(await screen.findByText("Rafiq Hasan")).toBeInTheDocument();
    expect(nav.replaced.at(-1)).toBe("/staff/reviews?tab=published");
    expect(lastListSearch()).toContain("status=APPROVED");

    await user.click(screen.getByRole("tab", { name: /Waiting/ }));
    await waitFor(() => expect(nav.replaced.at(-1)).toBe("/staff/reviews"));
    expect(lastListSearch()).toContain("status=PENDING");
  });

  it("the All tab sends no status", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Nusrat Jahan");
    await user.click(screen.getByRole("tab", { name: /All/ }));
    await waitFor(() => expect(nav.replaced.at(-1)).toBe("/staff/reviews?tab=all"));
    await waitFor(() => expect(lastListSearch()).not.toContain("status="));
  });
});

describe("the Reviews page: filters live in the URL", () => {
  it("each filter changes the list and the counts request and the page URL", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Nusrat Jahan");

    await user.selectOptions(screen.getByRole("combobox", { name: "Type" }), "exam");
    await waitFor(() => expect(nav.search).toBe("kind=exam"));
    await waitFor(() => expect(lastListSearch()).toContain("kind=exam"));
    expect(calls.filter((c) => c.path.endsWith("/counts")).at(-1)?.search).toContain("kind=exam");

    await user.selectOptions(screen.getByRole("combobox", { name: "Stars" }), "4");
    await waitFor(() => expect(nav.search).toContain("rating=4"));
    await waitFor(() => expect(lastListSearch()).toContain("rating=4"));

    await user.click(screen.getByRole("checkbox", { name: "Featured only" }));
    await waitFor(() => expect(nav.search).toContain("featured=true"));
    await waitFor(() => expect(lastListSearch()).toContain("featured=true"));

    await user.selectOptions(screen.getByRole("combobox", { name: "Sort" }), "oldest");
    await waitFor(() => expect(nav.search).toContain("sort=oldest"));
    await waitFor(() => expect(lastListSearch()).toContain("sort=oldest"));

    await user.type(screen.getByPlaceholderText("Name, student code or phone"), "Nusrat");
    await waitFor(() => expect(nav.search).toContain("q=Nusrat"), { timeout: 2000 });
    await waitFor(() => expect(lastListSearch()).toContain("q=Nusrat"));
    // The counts never send the status, so the tab badges hold under any filter.
    expect(calls.filter((c) => c.path.endsWith("/counts")).at(-1)?.search).not.toContain("status=");
  });

  it("a Clear filters button removes every filter but keeps the tab", async () => {
    route("/staff/reviews?tab=hidden&kind=exam&rating=2&q=Karim");
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Karim Uddin");
    await user.click(screen.getByRole("button", { name: "Clear filters" }));
    await waitFor(() => expect(nav.replaced.at(-1)).toBe("/staff/reviews?tab=hidden"));
  });
});

describe("the Reviews page: the target picker", () => {
  it("opens the picker in a dialog and the chosen target narrows the list", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Nusrat Jahan");

    await user.click(screen.getByRole("button", { name: "Any exam or package" }));
    const dialog = await screen.findByRole("dialog", { name: "Choose an exam or a package" });
    expect(within(dialog).getByText("kind:either")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Pick other exam" }));
    await user.click(within(dialog).getByRole("button", { name: "Show these reviews" }));

    await waitFor(() => expect(nav.search).toBe("kind=exam&targetId=exam-2"));
    expect(await screen.findByRole("button", { name: "Other exam" })).toBeInTheDocument();
    expect(lastListSearch()).toContain("targetId=exam-2");
  });

  it("Clear removes the target without opening anything", async () => {
    route("/staff/reviews?kind=exam&targetId=exam-1");
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("button", { name: "Penal Code Mock" });
    await user.click(screen.getByRole("button", { name: "Clear" }));
    await waitFor(() => expect(nav.replaced.at(-1)).toBe("/staff/reviews?kind=exam"));
    expect(screen.getByRole("button", { name: "Any exam or package" })).toBeInTheDocument();
  });
});

describe("the Reviews page: a deep link presets the filter", () => {
  it("opens with the type and the target from the URL, and names the target", async () => {
    route("/staff/reviews?kind=exam&targetId=exam-1");
    renderPage();
    expect(await screen.findByRole("button", { name: "Penal Code Mock" })).toBeInTheDocument();
    expect(lastListSearch()).toContain("kind=exam");
    expect(lastListSearch()).toContain("targetId=exam-1");
    expect(screen.getByRole("combobox", { name: "Type" })).toHaveValue("exam");
  });
});

describe("the Reviews page: actions", () => {
  it("Approve publishes the row, moves the count and says so", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Approve" }));

    expect(await screen.findByText("Review published.")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText("Nusrat Jahan")).not.toBeInTheDocument());
    expect(screen.getByRole("tab", { name: /Waiting/ })).toHaveTextContent("1");
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.path).toBe("/api/v1/staff/reviews/pending-1/status");
    expect(put?.body).toEqual({ status: "APPROVED" });
  });

  it("Approve and feature publishes the review and puts it on the landing page in one click", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Approve and feature" }));

    expect(await screen.findByText("Review published and featured on the landing page.")).toBeInTheDocument();
    const puts = calls.filter((c) => c.method === "PUT");
    expect(puts.map((c) => c.path)).toEqual([
      "/api/v1/staff/reviews/pending-1/status",
      "/api/v1/staff/reviews/pending-1/featured",
    ]);
    expect(puts[0].body).toEqual({ status: "APPROVED" });
    expect(puts[1].body).toEqual({ featured: true });
  });

  it("Approve and feature says so when the review is published but could not be featured", async () => {
    serve((call) =>
      call.method === "PUT" && call.path.endsWith("/featured")
        ? new Failure(409, { code: "REVIEW_NOT_FEATURABLE", message: "No.", timestamp: "", requestId: "t" })
        : defaultAnswer(call),
    );
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Approve and feature" }));

    expect(await screen.findByText(/Review published, but it could not be featured/)).toBeInTheDocument();
    expect(calls.filter((c) => c.method === "PUT")).toHaveLength(2);
  });

  it("a waiting review with stars only has Approve but no Approve and feature", async () => {
    serve((call) =>
      call.method === "GET" && call.path === "/api/v1/staff/reviews"
        ? { content: [row({ id: "stars-1", studentName: "Stars Only", status: "PENDING", comment: null })], page: 0, size: 20, totalElements: 1, totalPages: 1 }
        : defaultAnswer(call),
    );
    renderPage();
    await screen.findByText("Stars Only");
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve and feature" })).toBeNull();
  });

  it("Hide needs a reason of up to 300 characters before it sends", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Hide" }));

    const dialog = await screen.findByRole("dialog", { name: "Hide this review?" });
    const reason = within(dialog).getByRole("textbox");
    expect(reason).toHaveAttribute("maxlength", "300");
    await user.click(within(dialog).getByRole("button", { name: "Hide review" }));
    expect(within(dialog).getByText("Write a reason. The student sees it.")).toBeInTheDocument();
    expect(calls.some((c) => c.method === "PUT")).toBe(false);

    await user.type(reason, "Spam");
    await user.click(within(dialog).getByRole("button", { name: "Hide review" }));
    expect(await screen.findByText("Review hidden.")).toBeInTheDocument();
    expect(calls.find((c) => c.method === "PUT")?.body).toEqual({ status: "HIDDEN", reason: "Spam" });
    await waitFor(() => expect(screen.queryByText("Nusrat Jahan")).not.toBeInTheDocument());
  });

  it("Feature is offered only on a published review with a comment, and toggles", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Nusrat Jahan");
    // A waiting comment cannot be featured yet.
    expect(screen.queryByRole("button", { name: "Feature" })).toBeNull();

    await user.click(screen.getByRole("tab", { name: /Published/ }));
    const withComment = (await screen.findByText("Rafiq Hasan")).closest("li") as HTMLElement;
    const starsOnly = screen.getByText("Farhana Akter").closest("li") as HTMLElement;
    expect(within(starsOnly).queryByRole("button", { name: "Feature" })).toBeNull();
    expect(within(withComment).getByRole("img", { name: "4 out of 5 stars" })).toBeInTheDocument();

    await user.click(within(withComment).getByRole("button", { name: "Feature" }));
    expect(await screen.findByText("Review featured on the landing page.")).toBeInTheDocument();
    expect(calls.find((c) => c.method === "PUT")?.body).toEqual({ featured: true });
    expect(within(withComment).getByRole("button", { name: "Unfeature" })).toBeInTheDocument();
  });
});

describe("the Reviews page: teachers and states", () => {
  it("a teacher reads the rows and sees no action, with a note that they can only read", async () => {
    renderPage(fixtures.instructor);
    expect(await screen.findByText("Nusrat Jahan")).toBeInTheDocument();
    expect(screen.getByText(/You can read the reviews of your own exams/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Hide" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Feature" })).toBeNull();
  });

  it("each tab has its own empty state, and a filtered empty list says so", async () => {
    serve((call) => (call.path === "/api/v1/staff/reviews" ? { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } : defaultAnswer(call)));
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText("Nothing is waiting")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: /Hidden/ }));
    expect(await screen.findByText("No hidden reviews")).toBeInTheDocument();
  });

  it("a filtered empty list says that no review matches", async () => {
    route("/staff/reviews?q=zzz");
    serve((call) => (call.path === "/api/v1/staff/reviews" ? { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } : defaultAnswer(call)));
    renderPage();
    expect(await screen.findByText("No reviews match these filters")).toBeInTheDocument();
  });

  it("a failed load shows an error with retry, and retry loads the list", async () => {
    let fail = true;
    serve((call) => {
      if (fail && call.path === "/api/v1/staff/reviews") {
        return new Failure(500, { code: "INTERNAL_ERROR", message: "Server error.", timestamp: "", requestId: "t" });
      }
      return defaultAnswer(call);
    });
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByRole("alert")).toBeInTheDocument();

    fail = false;
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("Nusrat Jahan")).toBeInTheDocument();
  });

  it("Load more appends the next page", async () => {
    serve((call) => {
      if (call.path === "/api/v1/staff/reviews") {
        const page = Number(call.url.searchParams.get("page") ?? "0");
        const second = row({ id: "pending-2", studentName: "Second Student" });
        return page === 0
          ? { content: [ROWS[0]], page: 0, size: 20, totalElements: 2, totalPages: 2 }
          : { content: [second], page: 1, size: 20, totalElements: 2, totalPages: 2 };
      }
      return defaultAnswer(call);
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Nusrat Jahan");
    await user.click(screen.getByRole("button", { name: "Load more" }));

    expect(await screen.findByText("Second Student")).toBeInTheDocument();
    expect(screen.getByText("Nusrat Jahan")).toBeInTheDocument();
    expect(listCalls().some((c) => c.search.includes("page=1"))).toBe(true);
  });
});
