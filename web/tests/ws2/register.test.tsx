import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import RegisterPage from "@/app/(auth)/register/page";
import { apiError, fixtures, mockApi, renderWithProviders, routerState } from "../helpers";

async function fillForm() {
  await userEvent.type(screen.getByLabelText(/^নাম/), "রহিম উদ্দিন");
  await userEvent.type(screen.getByLabelText(/^ফোন/), "01711000001");
  await userEvent.type(screen.getByLabelText(/^ইমেইল/), "rahim@example.com");
  await userEvent.type(screen.getByLabelText(/^পাসওয়ার্ড/, { selector: "input" }), "password1");
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
