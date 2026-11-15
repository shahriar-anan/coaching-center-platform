import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import ActivatePage from "@/app/(auth)/activate/page";
import { mockApi, renderWithProviders } from "../helpers";

async function fillActivation(user: ReturnType<typeof userEvent.setup>, confirm: string) {
  await user.type(screen.getByLabelText(/^Phone/), "01711000001");
  await user.type(screen.getByLabelText(/^Code/), "K7Q2-9PXA");
  await user.type(screen.getByLabelText(/^New password/), "password1");
  await user.type(screen.getByLabelText(/^Confirm password/), confirm);
  await user.click(screen.getByRole("button", { name: "Activate account" }));
}

describe("the activate page asks for the password twice", () => {
  it("refuses mismatched passwords with a field error and sends nothing", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "POST /api/v1/auth/activate": {} });
    renderWithProviders(<ActivatePage />, { locale: "en", route: "/activate" });

    await fillActivation(user, "password2");
    expect(await screen.findByText("The passwords do not match.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });

  it("sends the activation when the two passwords match", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "POST /api/v1/auth/activate": {} });
    renderWithProviders(<ActivatePage />, { locale: "en", route: "/activate" });

    await fillActivation(user, "password1");
    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/auth/activate")).toBe(true));
    expect(api.calls.find((call) => call.path === "/api/v1/auth/activate")?.body).toEqual({
      phone: "01711000001",
      code: "K7Q2-9PXA",
      password: "password1",
    });
  });
});

describe("the activate page takes the email or the phone (plan Q5)", () => {
  it("activates with the email the code was sent to, when no phone is typed", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "POST /api/v1/auth/activate": {} });
    renderWithProviders(<ActivatePage />, { locale: "en", route: "/activate" });

    await user.type(screen.getByLabelText(/^Email/), "rahim@example.com");
    await user.type(screen.getByLabelText(/^Code/), "K7Q2-9PXA");
    await user.type(screen.getByLabelText(/^New password/), "password1");
    await user.type(screen.getByLabelText(/^Confirm password/), "password1");
    await user.click(screen.getByRole("button", { name: "Activate account" }));

    await waitFor(() => expect(api.calls.some((call) => call.path === "/api/v1/auth/activate")).toBe(true));
    expect(api.calls.find((call) => call.path === "/api/v1/auth/activate")?.body).toEqual({
      email: "rahim@example.com",
      code: "K7Q2-9PXA",
      password: "password1",
    });
  });

  it("asks for an email or a phone when neither is typed, and sends nothing", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "POST /api/v1/auth/activate": {} });
    renderWithProviders(<ActivatePage />, { locale: "en", route: "/activate" });

    await user.type(screen.getByLabelText(/^Code/), "K7Q2-9PXA");
    await user.type(screen.getByLabelText(/^New password/), "password1");
    await user.type(screen.getByLabelText(/^Confirm password/), "password1");
    await user.click(screen.getByRole("button", { name: "Activate account" }));

    expect(await screen.findAllByText("Enter your email or your phone number.")).toHaveLength(2);
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });

  it("refuses an email that is not an address", async () => {
    const user = userEvent.setup();
    const api = mockApi({ "POST /api/v1/auth/activate": {} });
    renderWithProviders(<ActivatePage />, { locale: "en", route: "/activate" });

    await user.type(screen.getByLabelText(/^Email/), "rahim-at-example");
    await user.type(screen.getByLabelText(/^Code/), "K7Q2-9PXA");
    await user.type(screen.getByLabelText(/^New password/), "password1");
    await user.type(screen.getByLabelText(/^Confirm password/), "password1");
    await user.click(screen.getByRole("button", { name: "Activate account" }));

    expect(await screen.findByText("Enter a valid email address.")).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "POST")).toBe(false);
  });
});
