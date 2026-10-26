import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AccountStatusToggle, DeleteAccountButton } from "@/components/staff/status/account-controls";
import { fixtures, renderWithProviders } from "../helpers";
import { apiError, mockApi } from "../helpers/mock-api";
import "./copy-setup";

const instructorId = "44444444-0000-4000-8000-000000000001";

describe("account controls", () => {
  it("shows nothing to System Admin", () => {
    renderWithProviders(
      <>
        <AccountStatusToggle kind="instructors" id={instructorId} status="ACTIVE" />
        <DeleteAccountButton kind="instructors" id={instructorId} name="ফারিহা রহমান" />
      </>,
      { user: fixtures.systemAdmin },
    );

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("suspends an active instructor with one click", async () => {
    const { calls } = mockApi({
      [`PATCH /api/v1/instructors/${instructorId}/status`]: null,
    });
    renderWithProviders(<AccountStatusToggle kind="instructors" id={instructorId} status="ACTIVE" />, {
      user: fixtures.masterAdmin,
    });
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "নিষ্ক্রিয় করুন" }));

    await waitFor(() => expect(calls.find((call) => call.method === "PATCH")?.body).toEqual({ status: "INACTIVE" }));
  });

  it("keeps delete disabled until the name is typed", async () => {
    mockApi({});
    renderWithProviders(<DeleteAccountButton kind="instructors" id={instructorId} name="ফারিহা রহমান" />, {
      user: fixtures.masterAdmin,
    });
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "মুছে ফেলুন" }));
    const confirm = screen.getAllByRole("button", { name: "মুছে ফেলুন" }).at(-1)!;
    expect(confirm).toBeDisabled();

    await user.type(screen.getByLabelText(/নাম লিখে/), "ফারিহা রহমান");
    expect(confirm).toBeEnabled();
  });

  it("shows the marked-work refusal, and offers to suspend instead", async () => {
    const { calls } = mockApi({
      [`DELETE /api/v1/instructors/${instructorId}`]: apiError(409, "ACCOUNT_HAS_MARKED_WORK", "Marked work exists."),
      [`PATCH /api/v1/instructors/${instructorId}/status`]: null,
    });
    renderWithProviders(<DeleteAccountButton kind="instructors" id={instructorId} name="ফারিহা রহমান" />, {
      user: fixtures.masterAdmin,
    });
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "মুছে ফেলুন" }));
    await user.type(screen.getByLabelText(/নাম লিখে/), "ফারিহা রহমান");
    await user.click(screen.getAllByRole("button", { name: "মুছে ফেলুন" }).at(-1)!);

    expect(
      await screen.findByText("এই ইনস্ট্রাক্টরের মূল্যায়ন করা কাজ আছে, তাই মুছে ফেলা যাবে না। অ্যাকাউন্ট নিষ্ক্রিয় করুন।"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "নিষ্ক্রিয় করুন" }));
    await waitFor(() => expect(calls.find((call) => call.method === "PATCH")?.body).toEqual({ status: "INACTIVE" }));
  });
});
