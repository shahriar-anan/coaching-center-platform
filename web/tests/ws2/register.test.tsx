import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RegisterPage from "@/app/(auth)/register/page";
import { apiError, fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

/** The password label is matched with its asterisk, so the confirm label ("পাসওয়ার্ড নিশ্চিত করুন") is not picked up too. */
async function fillForm(confirm = "password1") {
  await userEvent.type(screen.getByLabelText(/^নাম/), "রহিম উদ্দিন");
  await userEvent.type(screen.getByLabelText(/^ফোন/), "01711000001");
  await userEvent.type(screen.getByLabelText(/^ইমেইল/), "rahim@example.com");
  await userEvent.type(screen.getByLabelText(/^পাসওয়ার্ড\*/, { selector: "input" }), "password1");
  await userEvent.type(screen.getByLabelText(/^পাসওয়ার্ড নিশ্চিত/, { selector: "input" }), confirm);
}

beforeEach(() => {
  window.localStorage.clear();
});

describe("register", () => {
  it("shows a duplicate phone error under the phone field and keeps the other values", async () => {
    mockApi({ "POST /api/v1/auth/register": () => apiError(409, "DUPLICATE_PHONE") });
    renderWithProviders(<RegisterPage />);
    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: "অ্যাকাউন্ট খুলুন" }));
    expect(await screen.findByText("এই ফোন নম্বরে আগেই একটি অ্যাকাউন্ট আছে।")).toBeInTheDocument();
    expect(screen.getByLabelText(/^ইমেইল/)).toHaveValue("rahim@example.com");
    expect(screen.queryByText("এই ইমেইলে আগেই একটি অ্যাকাউন্ট আছে।")).toBeNull();
  });

  it("shows a duplicate email error separately", async () => {
    mockApi({ "POST /api/v1/auth/register": () => apiError(409, "DUPLICATE_EMAIL") });
    renderWithProviders(<RegisterPage />);
    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: "অ্যাকাউন্ট খুলুন" }));
    expect(await screen.findByText("এই ইমেইলে আগেই একটি অ্যাকাউন্ট আছে।")).toBeInTheDocument();
    expect(screen.queryByText("এই ফোন নম্বরে আগেই একটি অ্যাকাউন্ট আছে।")).toBeNull();
  });

  it("falls back to the login link when the automatic sign-in fails, and sets the welcome flag", async () => {
    mockApi({ "POST /api/v1/auth/register": { id: "u1" } });
    renderWithProviders(<RegisterPage />);
    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: "অ্যাকাউন্ট খুলুন" }));
    expect(await screen.findByText("অ্যাকাউন্ট তৈরি হয়েছে")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "লগইন করুন" })).toHaveAttribute("href", "/login");
    await waitFor(() => expect(window.localStorage.getItem("exam-portal.welcome")).toBe("1"));
  });

  it("signs the new student in, takes the emailed code, and goes home", async () => {
    const verify = vi.fn(() => undefined);
    mockApi({ "POST /api/v1/auth/register": { id: "u1" }, "POST /api/v1/auth/verify-email": verify });
    const login = vi.fn(async () => ({ ...fixtures.student, email: "rahim@example.com" }));
    renderWithProviders(<RegisterPage />, { session: { login } });
    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: "অ্যাকাউন্ট খুলুন" }));

    expect(await screen.findByText("ইমেইল যাচাই করুন")).toBeInTheDocument();
    expect(login).toHaveBeenCalledWith("01711000001", "password1", { redirect: false });
    expect(screen.getByText(/rahim@example\.com/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/^যাচাই কোড/), "123456");
    await userEvent.click(screen.getByRole("button", { name: "যাচাই করুন" }));

    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/"));
    expect(verify).toHaveBeenCalled();
  });

  it("blocks the request when the two passwords differ, and moves focus to the confirm field", async () => {
    const { calls } = mockApi({ "POST /api/v1/auth/register": { id: "u1" } });
    renderWithProviders(<RegisterPage />);
    await fillForm("password2");
    await userEvent.click(screen.getByRole("button", { name: "অ্যাকাউন্ট খুলুন" }));

    const confirm = screen.getByLabelText(/^পাসওয়ার্ড নিশ্চিত/, { selector: "input" });
    expect(await screen.findByText("পাসওয়ার্ড দুটি মেলেনি।")).toBeInTheDocument();
    expect(confirm).toHaveAttribute("aria-invalid", "true");
    expect(confirm.getAttribute("aria-describedby")).toMatch(/-error/);
    expect(confirm).toHaveFocus();
    expect(calls.some((call) => call.path === "/api/v1/auth/register")).toBe(false);
  });

  it("submits when the passwords match, and the confirm value is not in the request body", async () => {
    const { calls } = mockApi({ "POST /api/v1/auth/register": { id: "u1" } });
    const login = vi.fn(async () => fixtures.student);
    renderWithProviders(<RegisterPage />, { session: { login } });
    await fillForm("password1");
    await userEvent.click(screen.getByRole("button", { name: "অ্যাকাউন্ট খুলুন" }));

    expect(await screen.findByText("ইমেইল যাচাই করুন")).toBeInTheDocument();
    const sent = calls.find((call) => call.path === "/api/v1/auth/register");
    expect(sent).toBeDefined();
    const body = sent?.body as Record<string, unknown>;
    expect(Object.keys(body).sort()).toEqual(["email", "fullName", "password", "phone"]);
    expect(body.password).toBe("password1");
  });

  it("shows and hides the confirm password on its own toggle", async () => {
    renderWithProviders(<RegisterPage />);
    await fillForm("password1");
    const confirm = screen.getByLabelText(/^পাসওয়ার্ড নিশ্চিত/, { selector: "input" });
    const toggle = confirm.parentElement?.querySelector("button") as HTMLButtonElement;
    expect(confirm).toHaveAttribute("type", "password");
    await userEvent.click(toggle);
    expect(confirm).toHaveAttribute("type", "text");
    expect(toggle).toHaveAttribute("aria-pressed", "true");
    await userEvent.click(toggle);
    expect(confirm).toHaveAttribute("type", "password");
  });

  it("lets the student skip the code and returns them to the exam they came from", async () => {
    mockApi({ "POST /api/v1/auth/register": { id: "u1" } });
    const login = vi.fn(async () => fixtures.student);
    renderWithProviders(<RegisterPage />, { session: { login }, route: "/register?next=%2Fexam%2Fe1" });
    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: "অ্যাকাউন্ট খুলুন" }));

    await userEvent.click(await screen.findByRole("button", { name: "পরে করব" }));
    expect(routerState.replace).toHaveBeenCalledWith("/exam/e1");
  });
});
