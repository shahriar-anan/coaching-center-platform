import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import RegisterPage from "@/app/(auth)/register/page";
import { apiError, mockApi, renderWithProviders } from "../helpers";

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

  it("says the account is created, does not sign in, and sets the welcome flag", async () => {
    mockApi({ "POST /api/v1/auth/register": { id: "u1" } });
    renderWithProviders(<RegisterPage />);
    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: "অ্যাকাউন্ট খুলুন" }));
    expect(await screen.findByText("অ্যাকাউন্ট তৈরি হয়েছে")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "লগইন করুন" })).toHaveAttribute("href", "/login");
    await waitFor(() => expect(window.localStorage.getItem("exam-portal.welcome")).toBe("1"));
  });
});
