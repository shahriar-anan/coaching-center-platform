import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiBlob, apiRequest, onSessionLost } from "@/lib/api/client";
import { getAccessToken, setAccessToken } from "@/lib/auth/token-store";

/**
 * A fake API with the real refresh rules: the cookie rotates on every refresh,
 * and presenting a used one ends the session (AuthService.refresh).
 */
function fakeApi({ refreshWorks = true, slowPaths = {} as Record<string, number> } = {}) {
  let cookie = "rt0";
  let liveRefresh: string | null = "rt0";
  let validAccess: string | null = null;
  let issued = 0;
  const calls = { refresh: 0 };

  const json = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
  const unauthenticated = () =>
    json(401, { code: "UNAUTHENTICATED", message: "x", timestamp: "", requestId: "" });

  const fetchMock = vi.fn(async (url: string, init: RequestInit) => {
    const path = new URL(url).pathname;
    if (path === "/api/v1/auth/refresh") {
      calls.refresh++;
      const presented = cookie;
      await new Promise((r) => setTimeout(r, 10));
      if (!refreshWorks || presented !== liveRefresh) {
        liveRefresh = null;
        return json(401, { code: "INVALID_REFRESH_TOKEN", message: "x", timestamp: "", requestId: "" });
      }
      issued++;
      liveRefresh = `rt${issued}`;
      cookie = liveRefresh;
      validAccess = `at${issued}`;
      return json(200, { accessToken: validAccess, expiresInSeconds: 900 });
    }
    // The token is checked when the request arrives; the answer may come back later.
    const ok = new Headers(init.headers).get("Authorization") === `Bearer ${validAccess}`;
    await new Promise((r) => setTimeout(r, slowPaths[path] ?? 5));
    if (!ok) {
      return unauthenticated();
    }
    return path.startsWith("/api/v1/files/") ? new Response("file", { status: 200 }) : json(200, { path });
  });

  vi.stubGlobal("fetch", fetchMock);
  return calls;
}

describe("access token refresh", () => {
  let lost: number;

  beforeEach(() => {
    lost = 0;
    onSessionLost(() => {
      lost++;
    });
    setAccessToken("at-expired");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    onSessionLost(null);
    setAccessToken(null);
  });

  it("requests that expire together share one refresh and all go through", async () => {
    const calls = fakeApi();

    const results = await Promise.allSettled([
      apiRequest("/api/v1/live-exam-participation/summary"),
      apiRequest("/api/v1/live-exam-participation?size=20"),
      apiRequest("/api/v1/live-exam-orders?status=INITIATED&size=1"),
    ]);

    expect(results.map((r) => r.status)).toEqual(["fulfilled", "fulfilled", "fulfilled"]);
    expect(calls.refresh).toBe(1);
    expect(lost).toBe(0);
    expect(getAccessToken()).toBe("at1");
  });

  it("a 401 that arrives after another request refreshed retries with the new token", async () => {
    const calls = fakeApi({ slowPaths: { "/api/v1/slow": 60 } });

    const slow = apiRequest("/api/v1/slow");
    const fast = await Promise.allSettled([apiRequest("/api/v1/fast")]);
    const results = [...fast, ...(await Promise.allSettled([slow]))];

    expect(results.map((r) => r.status)).toEqual(["fulfilled", "fulfilled"]);
    expect(calls.refresh).toBe(1);
    expect(lost).toBe(0);
  });

  it("file downloads and API calls share the same refresh", async () => {
    const calls = fakeApi();

    const results = await Promise.allSettled([apiBlob("/api/v1/files/f1"), apiRequest("/api/v1/me")]);

    expect(results.map((r) => r.status)).toEqual(["fulfilled", "fulfilled"]);
    expect(calls.refresh).toBe(1);
    expect(lost).toBe(0);
  });

  it("a refused refresh ends the session once for every waiting request", async () => {
    const calls = fakeApi({ refreshWorks: false });

    const results = await Promise.allSettled([apiRequest("/api/v1/a"), apiRequest("/api/v1/b")]);

    expect(results.map((r) => r.status)).toEqual(["rejected", "rejected"]);
    expect(calls.refresh).toBe(1);
    expect(lost).toBeGreaterThan(0);
    expect(getAccessToken()).toBeNull();
  });

  it("a later expiry refreshes again with the rotated cookie", async () => {
    const calls = fakeApi();

    await apiRequest("/api/v1/a");
    setAccessToken("at-expired-again");
    await apiRequest("/api/v1/b");

    expect(calls.refresh).toBe(2);
    expect(lost).toBe(0);
  });
});
