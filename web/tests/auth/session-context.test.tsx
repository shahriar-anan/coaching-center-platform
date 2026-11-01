import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SessionProvider, useSession } from "@/lib/auth/session-context";
import { setAccessToken } from "@/lib/auth/token-store";

const student = {
  id: "s1",
  phone: "+8801711000000",
  email: "s@example.com",
  emailVerifiedAt: null,
  role: "STUDENT",
  status: "ACTIVE",
  fullName: "Student",
  studentCode: "S10000001",
  phoneMissing: false,
};

function Status() {
  const { session } = useSession();
  return <p>{session.status}</p>;
}

function renderSession() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <SessionProvider>
        <Status />
      </SessionProvider>
    </QueryClientProvider>,
  );
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

/**
 * A fake API for the session routes. The probe reports whether the browser has
 * the refresh cookie; refresh succeeds only with that cookie. Every request path
 * is recorded in order.
 */
function fakeApi({
  cookie,
  probe = "ok",
  refreshOk = true,
}: {
  cookie: boolean;
  probe?: "ok" | "server-error" | "network-error";
  refreshOk?: boolean;
}) {
  const requests: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      const path = new URL(url).pathname;
      requests.push(path);
      if (path === "/api/v1/auth/session") {
        if (probe === "network-error") {
          throw new TypeError("Failed to fetch");
        }
        if (probe === "server-error") {
          return json(503, { code: "INTERNAL_ERROR", message: "x", timestamp: "", requestId: "" });
        }
        return json(200, { hasSession: cookie });
      }
      if (path === "/api/v1/auth/refresh") {
        if (cookie && refreshOk) {
          return json(200, { accessToken: "a1", expiresInSeconds: 900 });
        }
        return json(401, { code: "INVALID_REFRESH_TOKEN", message: "Refresh token is invalid.", timestamp: "", requestId: "" });
      }
      if (path === "/api/v1/me") {
        return json(200, student);
      }
      return json(404, { code: "NOT_FOUND", message: "x", timestamp: "", requestId: "" });
    }),
  );
  return requests;
}

describe("session on page load", () => {
  afterEach(() => {
    setAccessToken(null);
  });

  it("a visitor with no refresh cookie is anonymous and never calls refresh", async () => {
    const requests = fakeApi({ cookie: false });

    renderSession();

    await waitFor(() => expect(screen.getByText("anonymous")).toBeInTheDocument());
    expect(requests).toEqual(["/api/v1/auth/session"]);
  });

  it("a returning user with the cookie probes first, then refreshes and loads the account", async () => {
    const requests = fakeApi({ cookie: true });

    renderSession();

    await waitFor(() => expect(screen.getByText("authenticated")).toBeInTheDocument());
    expect(requests).toEqual(["/api/v1/auth/session", "/api/v1/auth/refresh", "/api/v1/me"]);
  });

  it("a refresh token that expired on the server ends anonymous after the one refresh call", async () => {
    const requests = fakeApi({ cookie: true, refreshOk: false });

    renderSession();

    await waitFor(() => expect(screen.getByText("anonymous")).toBeInTheDocument());
    expect(requests.filter((path) => path === "/api/v1/auth/refresh")).toHaveLength(1);
    expect(requests).not.toContain("/api/v1/me");
  });

  it.each(["server-error", "network-error"] as const)(
    "a failed probe (%s) falls back to the refresh call",
    async (probe) => {
      const requests = fakeApi({ cookie: true, probe });

      renderSession();

      await waitFor(() => expect(screen.getByText("authenticated")).toBeInTheDocument());
      expect(requests).toContain("/api/v1/auth/refresh");
    },
  );
});
