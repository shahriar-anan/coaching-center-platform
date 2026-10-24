import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import SandboxPage from "@/app/sandbox/page";
import { SessionProvider } from "@/lib/auth/session-context";
import { setAccessToken } from "@/lib/auth/token-store";

const navigation = vi.hoisted(() => ({
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/sandbox",
  useRouter: () => ({ replace: navigation.replace, push: vi.fn() }),
}));

const assign = vi.fn();

type Call = { path: string; method: string; body?: string; search: string };

const calls: Call[] = [];

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function failure(status: number, code: string, extra: Record<string, unknown> = {}): Response {
  return json(status, {
    code,
    message: code,
    timestamp: "2026-10-24T00:00:00Z",
    requestId: "req-1",
    ...extra,
  });
}

function pageOf<T>(content: T[]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

const student = {
  id: "student-1",
  phone: "01700000000",
  email: "s@example.com",
  emailVerifiedAt: null,
  role: "STUDENT",
  status: "ACTIVE",
  fullName: "Seed",
  studentCode: "S1",
};

const priced = { id: "exam-1", titleEn: "Practice exam", price: 10 };
const free = { id: "exam-2", titleEn: "Free paper", price: null };

function install(handle: (call: Call) => Response | Promise<Response> | undefined) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input));
      const call: Call = {
        path: url.pathname,
        method: init?.method ?? "GET",
        body: typeof init?.body === "string" ? init.body : undefined,
        search: url.search,
      };
      calls.push(call);
      if (call.path === "/api/v1/auth/refresh" && call.method === "POST") {
        return json(200, { accessToken: "token", expiresInSeconds: 900 });
      }
      if (call.path === "/api/v1/me" && call.method === "GET") {
        return json(200, student);
      }
      const response = handle(call);
      if (!response) {
        throw new Error(`unexpected ${call.method} ${call.path}`);
      }
      return response;
    }),
  );
}

function catalog(call: Call): Response | undefined {
  if (call.path === "/api/v1/public/live-exam-modules" && call.method === "GET") {
    return json(200, pageOf([{ id: "mod-1" }]));
  }
  if (call.path === "/api/v1/public/live-exam-modules/mod-1/exams" && call.method === "GET") {
    return json(200, pageOf([priced, free]));
  }
  return undefined;
}

function emptyCatalog(call: Call): Response | undefined {
  if (call.path === "/api/v1/public/live-exam-modules" && call.method === "GET") {
    return json(200, pageOf([]));
  }
  return undefined;
}

function renderPage() {
  render(
    <SessionProvider>
      <SandboxPage />
    </SessionProvider>,
  );
}

beforeEach(() => {
  calls.length = 0;
  navigation.replace.mockReset();
  assign.mockReset();
  sessionStorage.clear();
  setAccessToken(null);
  Reflect.deleteProperty(window, "location");
  Object.defineProperty(window, "location", {
    configurable: true,
    value: { assign },
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

it("hides an exam that has no price", async () => {
  install(catalog);
  renderPage();
  expect(await screen.findByText("Practice exam 10.00")).toBeInTheDocument();
  expect(screen.queryByText(/Free paper/)).not.toBeInTheDocument();
  const moduleCall = calls.find((call) => call.path === "/api/v1/public/live-exam-modules");
  const examCall = calls.find((call) => call.path.endsWith("/exams"));
  expect(moduleCall?.search).toContain("size=100");
  expect(examCall?.search).toContain("size=100");
});

it("sends only the exam id when buying", async () => {
  const user = userEvent.setup();
  install((call) => {
    const listed = catalog(call);
    if (listed) {
      return listed;
    }
    if (call.path === "/api/v1/live-exam-orders" && call.method === "POST") {
      return json(201, {
        orderId: "order-1",
        tranId: "tran-1",
        redirectUrl: "https://gateway.test/pay",
        status: "INITIATED",
      });
    }
    return undefined;
  });
  renderPage();
  await user.click(await screen.findByRole("button", { name: "Buy" }));
  await waitFor(() =>
    expect(calls.some((call) => call.path === "/api/v1/live-exam-orders" && call.method === "POST")).toBe(true),
  );
  const buy = calls.find((call) => call.path === "/api/v1/live-exam-orders" && call.method === "POST");
  expect(JSON.parse(buy?.body ?? "")).toEqual({ examId: "exam-1" });
});

it("follows redirectUrl and keeps a rejected session on the page", async () => {
  const user = userEvent.setup();
  let buys = 0;
  install((call) => {
    const listed = catalog(call);
    if (listed) {
      return listed;
    }
    if (call.path === "/api/v1/live-exam-orders" && call.method === "POST") {
      buys += 1;
      if (buys === 1) {
        return json(201, {
          orderId: "order-1",
          tranId: "tran-1",
          redirectUrl: "https://gateway.test/pay",
          status: "INITIATED",
        });
      }
      return failure(503, "PAYMENT_GATEWAY_UNAVAILABLE", { orderId: "order-2", tranId: "tran-2" });
    }
    if (call.path === "/api/v1/live-exam-orders/order-2/cancel" && call.method === "POST") {
      return json(200, { status: "CANCELLED", orderId: "order-2" });
    }
    return undefined;
  });
  renderPage();
  await user.click(await screen.findByRole("button", { name: "Buy" }));
  await waitFor(() => expect(assign).toHaveBeenCalledWith("https://gateway.test/pay"));
  await waitFor(() => expect(screen.getByRole("button", { name: "Buy" })).toBeEnabled());
  expect(JSON.parse(sessionStorage.getItem("sandbox-order") ?? "")).toEqual({
    orderId: "order-1",
    tranId: "tran-1",
    examId: "exam-1",
    examName: "Practice exam",
  });

  assign.mockClear();
  await user.click(screen.getByRole("button", { name: "Buy" }));
  expect(await screen.findByText("The payment page did not open.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
  expect(assign).not.toHaveBeenCalled();
  expect(JSON.parse(sessionStorage.getItem("sandbox-order") ?? "")).toEqual({
    orderId: "order-2",
    tranId: "tran-2",
    examId: "exam-1",
    examName: "Practice exam",
  });

  await user.click(screen.getByRole("button", { name: "Cancel" }));
  await waitFor(() => expect(sessionStorage.getItem("sandbox-order")).toBeNull());
  expect(screen.queryByText("The payment page did not open.")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Cancel" })).not.toBeInTheDocument();
  expect(assign).not.toHaveBeenCalled();
});

it("stores nothing when payment is not configured", async () => {
  const user = userEvent.setup();
  install((call) => {
    const listed = catalog(call);
    if (listed) {
      return listed;
    }
    if (call.path === "/api/v1/live-exam-orders" && call.method === "POST") {
      return failure(503, "PAYMENT_GATEWAY_UNAVAILABLE");
    }
    return undefined;
  });
  renderPage();
  await user.click(await screen.findByRole("button", { name: "Buy" }));
  expect(await screen.findByText("Payments are not configured on this API.")).toBeInTheDocument();
  expect(sessionStorage.getItem("sandbox-order")).toBeNull();
  expect(screen.queryByRole("button", { name: "Cancel" })).not.toBeInTheDocument();
  expect(assign).not.toHaveBeenCalled();
});

it("clears a stored order that is no longer pending", async () => {
  sessionStorage.setItem(
    "sandbox-order",
    JSON.stringify({
      orderId: "order-9",
      tranId: "tran-9",
      examId: "exam-9",
      examName: "Stored exam",
    }),
  );
  install((call) => {
    const listed = emptyCatalog(call);
    if (listed) {
      return listed;
    }
    if (call.path === "/api/v1/live-exam-orders/order-9" && call.method === "GET") {
      return json(200, { status: "SUCCEEDED", orderId: "order-9" });
    }
    return undefined;
  });
  renderPage();
  expect(await screen.findByText("No priced exam is published yet.")).toBeInTheDocument();
  await waitFor(() => expect(sessionStorage.getItem("sandbox-order")).toBeNull());
  expect(screen.queryByRole("button", { name: "Cancel" })).not.toBeInTheDocument();
  expect(screen.queryByText("Stored exam")).not.toBeInTheDocument();
});

it("cancels the stored order without leaving the page", async () => {
  const user = userEvent.setup();
  sessionStorage.setItem(
    "sandbox-order",
    JSON.stringify({
      orderId: "order-9",
      tranId: "tran-9",
      examId: "exam-9",
      examName: "Stored exam",
    }),
  );
  install((call) => {
    const listed = emptyCatalog(call);
    if (listed) {
      return listed;
    }
    if (call.path === "/api/v1/live-exam-orders/order-9" && call.method === "GET") {
      return json(200, { status: "INITIATED", orderId: "order-9" });
    }
    if (call.path === "/api/v1/live-exam-orders/order-9/cancel" && call.method === "POST") {
      return json(200, { status: "CANCELLED", orderId: "order-9" });
    }
    return undefined;
  });
  renderPage();
  await user.click(await screen.findByRole("button", { name: "Cancel" }));
  await waitFor(() => expect(sessionStorage.getItem("sandbox-order")).toBeNull());
  expect(calls.some((call) => call.method === "POST" && call.path === "/api/v1/live-exam-orders/order-9/cancel")).toBe(
    true,
  );
  expect(assign).not.toHaveBeenCalled();
  expect(navigation.replace).not.toHaveBeenCalled();
});

it("does not treat a failed exam list as an empty catalog", async () => {
  install((call) => {
    if (call.path === "/api/v1/public/live-exam-modules" && call.method === "GET") {
      return failure(500, "INTERNAL_ERROR");
    }
    return undefined;
  });
  renderPage();
  expect(await screen.findByText("The exam list could not be loaded.")).toBeInTheDocument();
  expect(screen.queryByText("No priced exam is published yet.")).not.toBeInTheDocument();
});

it("reads the stored order again when the page is restored", async () => {
  sessionStorage.setItem(
    "sandbox-order",
    JSON.stringify({
      orderId: "order-9",
      tranId: "tran-9",
      examId: "exam-9",
      examName: "Stored exam",
    }),
  );
  install((call) => {
    const listed = emptyCatalog(call);
    if (listed) {
      return listed;
    }
    if (call.path === "/api/v1/live-exam-orders/order-9" && call.method === "GET") {
      return json(200, { status: "SUCCEEDED", orderId: "order-9" });
    }
    if (call.path === "/api/v1/live-exam-orders/order-2" && call.method === "GET") {
      return json(200, { status: "INITIATED", orderId: "order-2" });
    }
    return undefined;
  });
  renderPage();
  await waitFor(() => expect(sessionStorage.getItem("sandbox-order")).toBeNull());
  sessionStorage.setItem(
    "sandbox-order",
    JSON.stringify({
      orderId: "order-2",
      tranId: "tran-2",
      examId: "exam-1",
      examName: "Restored exam",
    }),
  );
  window.dispatchEvent(new Event("pageshow"));
  expect(await screen.findByRole("button", { name: "Cancel" })).toBeInTheDocument();
  expect(screen.getByText("Restored exam")).toBeInTheDocument();
});

it("keeps a newer stored order when an older read fails", async () => {
  sessionStorage.setItem(
    "sandbox-order",
    JSON.stringify({
      orderId: "order-9",
      tranId: "tran-9",
      examId: "exam-9",
      examName: "Stored exam",
    }),
  );
  let finish: (response: Response) => void = () => {};
  const gate = new Promise<Response>((resolve) => {
    finish = resolve;
  });
  install((call) => {
    const listed = emptyCatalog(call);
    if (listed) {
      return listed;
    }
    if (call.path === "/api/v1/live-exam-orders/order-9" && call.method === "GET") {
      return gate;
    }
    return undefined;
  });
  renderPage();
  await waitFor(() =>
    expect(calls.some((call) => call.path === "/api/v1/live-exam-orders/order-9")).toBe(true),
  );
  sessionStorage.setItem(
    "sandbox-order",
    JSON.stringify({
      orderId: "order-2",
      tranId: "tran-2",
      examId: "exam-1",
      examName: "Newer exam",
    }),
  );
  finish(failure(404, "RESOURCE_NOT_FOUND"));
  await act(async () => {
    await gate;
  });
  expect(JSON.parse(sessionStorage.getItem("sandbox-order") ?? "")).toMatchObject({ orderId: "order-2" });
  expect(screen.queryByText("Stored exam")).not.toBeInTheDocument();
});

it("keeps a student on /sandbox after sign-in", async () => {
  const user = userEvent.setup();
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input));
      const method = init?.method ?? "GET";
      if (url.pathname === "/api/v1/auth/refresh") {
        return failure(401, "UNAUTHENTICATED");
      }
      if (url.pathname === "/api/v1/auth/login" && method === "POST") {
        return json(200, { accessToken: "token", expiresInSeconds: 900 });
      }
      if (url.pathname === "/api/v1/me") {
        return json(200, student);
      }
      if (url.pathname === "/api/v1/public/live-exam-modules") {
        return json(200, pageOf([]));
      }
      throw new Error(`unexpected ${method} ${url.pathname}`);
    }),
  );
  renderPage();
  await user.type(await screen.findByLabelText("Phone or email"), "s@example.com");
  await user.type(screen.getByLabelText("Password"), "password1");
  await user.click(screen.getByRole("button", { name: "Sign in" }));
  expect(await screen.findByText("No priced exam is published yet.")).toBeInTheDocument();
  expect(navigation.replace).not.toHaveBeenCalled();
});
