import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ReviewCard } from "@/components/student/reviews/review-card";
import type { MyReview, ReviewState } from "@/lib/api/exam/reviews";
import en from "@/i18n/messages/en/reviews";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const ROUTE = "/api/v1/me/reviews/exam/e1";

function review(extra: Partial<MyReview> = {}): MyReview {
  return {
    id: "r1",
    rating: 4,
    comment: null,
    status: "APPROVED",
    hideReason: null,
    createdAt: "2026-10-28T10:00:00Z",
    updatedAt: "2026-10-28T10:00:00Z",
    ...extra,
  };
}

/** A small server: the state a GET returns, and what a PUT or DELETE leaves behind. */
function server(initial: ReviewState) {
  let state = initial;
  return mockApi({
    [`GET ${ROUTE}`]: () => state,
    [`PUT ${ROUTE}`]: ({ body }: { body: unknown }) => {
      const input = body as { rating: number; comment?: string | null };
      const saved = review({
        rating: input.rating,
        comment: input.comment ?? null,
        status: input.comment ? "PENDING" : "APPROVED",
      });
      state = { canReview: true, reviewed: true, review: saved };
      return saved;
    },
    [`DELETE ${ROUTE}`]: () => {
      state = { canReview: true, reviewed: false };
      return undefined;
    },
  });
}

/** Lets a read that renders nothing finish before the assertion. */
async function settle() {
  await act(() => new Promise((resolve) => setTimeout(resolve, 20)));
}

describe("review card", () => {
  it("renders nothing when the student may not review and has no review", async () => {
    const api = server({ canReview: false, reviewed: false });
    const { container } = renderWithProviders(<ReviewCard kind="exam" id="e1" />, {
      user: fixtures.student,
      locale: "en",
    });

    await waitFor(() => expect(api.calls.some((call) => call.path === ROUTE)).toBe(true));
    await settle();
    expect(container.querySelector("section")).toBeNull();
  });

  it("makes no request for a visitor or a non-student", async () => {
    const api = server({ canReview: true, reviewed: false });
    const { container } = renderWithProviders(<ReviewCard kind="exam" id="e1" />, { user: null, locale: "en" });

    await settle();
    expect(api.calls).toHaveLength(0);
    expect(container.querySelector("section")).toBeNull();
  });

  it("shows five star buttons, each named by its count, when the student may review", async () => {
    server({ canReview: true, reviewed: false });
    renderWithProviders(<ReviewCard kind="exam" id="e1" />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("heading", { name: en.card.titleExam })).toBeInTheDocument();
    const group = screen.getByRole("group", { name: en.card.ratingLabel });
    const stars = within(group).getAllByRole("button");
    expect(stars.map((button) => button.getAttribute("aria-label"))).toEqual([
      "1 star",
      "2 stars",
      "3 stars",
      "4 stars",
      "5 stars",
    ]);
  });

  it("saves a tap at once with the right path and body, then offers a comment", async () => {
    const api = server({ canReview: true, reviewed: false });
    renderWithProviders(<ReviewCard kind="exam" id="e1" />, { user: fixtures.student, locale: "en" });

    await userEvent.click(await screen.findByRole("button", { name: "3 stars" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "PUT")).toBe(true));
    const put = api.calls.find((call) => call.method === "PUT");
    expect(put).toMatchObject({ path: ROUTE, body: { rating: 3 } });
    expect(await screen.findByRole("button", { name: en.card.addComment })).toBeInTheDocument();
    expect(screen.getByText(en.card.statusApproved)).toBeInTheDocument();
  });

  it("opens the comment box with a counter, and saves the comment with the stars", async () => {
    const api = server({ canReview: true, reviewed: true, review: review({ rating: 4 }) });
    renderWithProviders(<ReviewCard kind="exam" id="e1" />, { user: fixtures.student, locale: "en" });

    await userEvent.click(await screen.findByRole("button", { name: en.card.addComment }));
    const box = screen.getByRole("textbox", { name: en.card.commentLabel });
    await userEvent.type(box, "Great paper");
    expect(screen.getByText("11 / 1000")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "PUT")).toBe(true));
    expect(api.calls.find((call) => call.method === "PUT")).toMatchObject({
      path: ROUTE,
      body: { rating: 4, comment: "Great paper" },
    });
    expect(await screen.findByText(en.card.thanks)).toBeInTheDocument();
    expect(await screen.findByText(en.card.statusPending)).toBeInTheDocument();
  });

  it("does not let the comment go past 1000 characters", async () => {
    server({ canReview: true, reviewed: true, review: review({ rating: 4 }) });
    renderWithProviders(<ReviewCard kind="exam" id="e1" />, { user: fixtures.student, locale: "en" });

    await userEvent.click(await screen.findByRole("button", { name: en.card.addComment }));
    const box = screen.getByRole("textbox", { name: en.card.commentLabel });
    expect(box).toHaveAttribute("maxlength", "1000");
  });

  it.each([
    ["PENDING", null, en.card.statusPending],
    ["APPROVED", null, en.card.statusApproved],
    ["HIDDEN", "Rude language", en.card.statusHidden.replace("{reason}", "Rude language")],
  ] as const)("shows the %s status as its text", async (status, hideReason, text) => {
    server({
      canReview: true,
      reviewed: true,
      review: review({ status, hideReason, comment: "A comment", rating: 2 }),
    });
    renderWithProviders(<ReviewCard kind="exam" id="e1" />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText(text)).toBeInTheDocument();
    expect(screen.getByText("A comment")).toBeInTheDocument();
  });

  it("says Not published with no reason when staff gave none", async () => {
    server({
      canReview: true,
      reviewed: true,
      review: review({ status: "HIDDEN", hideReason: null, comment: "A comment" }),
    });
    renderWithProviders(<ReviewCard kind="exam" id="e1" />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText(en.card.statusHiddenNoReason)).toBeInTheDocument();
  });

  it("asks before deleting, then removes the review and shows the form again", async () => {
    const api = server({ canReview: true, reviewed: true, review: review({ rating: 5 }) });
    renderWithProviders(<ReviewCard kind="exam" id="e1" />, { user: fixtures.student, locale: "en" });

    await userEvent.click(await screen.findByRole("button", { name: en.card.delete }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(en.card.deleteTitle)).toBeInTheDocument();
    expect(api.calls.some((call) => call.method === "DELETE")).toBe(false);

    await userEvent.click(within(dialog).getByRole("button", { name: en.card.delete }));

    await waitFor(() => expect(api.calls.some((call) => call.method === "DELETE")).toBe(true));
    expect(api.calls.find((call) => call.method === "DELETE")).toMatchObject({ path: ROUTE });
    expect(await screen.findByRole("button", { name: "1 star" })).toBeInTheDocument();
  });
});
