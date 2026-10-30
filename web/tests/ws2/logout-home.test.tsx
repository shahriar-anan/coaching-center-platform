import { afterEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SessionProvider, useSession } from "@/lib/auth/session-context";
import { RequireStudent } from "@/lib/auth/guards";
import { LocaleProvider } from "@/i18n/locale-context";

const nav = vi.hoisted(() => ({ pathname: "/account", replaced: [] as string[] }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: (href: string) => nav.replaced.push(href), push: vi.fn() }),
  usePathname: () => nav.pathname,
  useSearchParams: () => new URLSearchParams(""),
}));

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

function LogoutButton() {
  const { logout } = useSession();
  return <button onClick={() => void logout()}>log out</button>;
}

function App() {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <LocaleProvider initialLocale="en">
        <SessionProvider>
          <RequireStudent>
            <LogoutButton />
          </RequireStudent>
        </SessionProvider>
      </LocaleProvider>
    </QueryClientProvider>
  );
}

function signedInApi() {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (url.endsWith("/api/v1/auth/refresh")) {
        return new Response(JSON.stringify({ accessToken: "a1", expiresInSeconds: 900 }), { status: 200 });
      }
      if (url.endsWith("/api/v1/auth/logout")) {
        return new Response(null, { status: 204 });
      }
      if (url.endsWith("/api/v1/me")) {
        return new Response(JSON.stringify(student), { status: 200 });
      }
      return new Response("{}", { status: 200 });
    }),
  );
}

async function logOutFromAccount() {
  const view = render(<App />);
  await waitFor(() => screen.getByText("log out"));
  window.history.replaceState(null, "", "/account");
  await act(async () => {
    screen.getByText("log out").click();
    await new Promise((resolve) => setTimeout(resolve, 30));
  });
  return view;
}

describe("logout", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    nav.pathname = "/account";
    nav.replaced = [];
  });

  it("from a signed-in page lands on home, not on the login page with that page as next", async () => {
    signedInApi();
    await logOutFromAccount();

    expect(nav.replaced).toEqual(["/"]);
  });

  it("does not stop a later visit to a signed-in page from asking for a login", async () => {
    signedInApi();
    const view = await logOutFromAccount();

    nav.pathname = "/";
    view.rerender(<App />);
    nav.pathname = "/notices";
    view.rerender(<App />);

    await waitFor(() => expect(nav.replaced.at(-1)).toBe("/login?next=%2Fnotices"));
  });
});
