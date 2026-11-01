import { screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ReviewsSection } from "@/components/student/reviews/reviews-section";
import type { PublicReview, PublicReviews } from "@/lib/api/exam/reviews";
import en from "@/i18n/messages/en/reviews";
import { mockApi, renderWithProviders } from "../helpers";

const ROUTE = "/api/v1/public/reviews/exam/e1";

const zeroStars = { "1": 0, "2": 0, "3": 0, "4": 0, "5": 0 };

function comment(id: string, extra: Partial<PublicReview> = {}): PublicReview {
  return {
    id,
    name: "Farhana A.",
    rating: 5,
    comment: `Comment ${id}`,
    createdAt: "2026-10-20T04:00:00Z",
    ...extra,
  };
}

/** One page of the public read. The summary is the same on every page, as the API sends it. */
function page(summary: Pick<PublicReviews, "ratingAverage" | "ratingCount" | "stars">, content: PublicReview[], number = 0, totalPages = 1): PublicReviews {
  return {
    ...summary,
    page: { content, page: number, size: 10, totalElements: content.length, totalPages },
  };
}

describe("reviews section", () => {
  it("is hidden when there are no ratings", async () => {
    mockApi({
      [`GET ${ROUTE}`]: page({ ratingAverage: null, ratingCount: 0, stars: zeroStars }, []),
    });
    renderWithProviders(<ReviewsSection kind="exam" id="e1" />, { user: null, locale: "en" });

    await waitFor(() => expect(screen.queryByRole("heading", { name: en.section.title })).not.toBeInTheDocument());
    expect(screen.queryByRole("list", { name: en.section.breakdown })).not.toBeInTheDocument();
  });

  it("is hidden when the read fails", async () => {
    mockApi({});
    renderWithProviders(<ReviewsSection kind="exam" id="e1" />, { user: null, locale: "en" });

    await waitFor(() => expect(screen.queryByRole("heading", { name: en.section.title })).not.toBeInTheDocument());
  });

  it("hides the average and the breakdown below three ratings, but still shows the comments", async () => {
    mockApi({
      [`GET ${ROUTE}`]: page(
        { ratingAverage: null, ratingCount: 2, stars: { ...zeroStars, "5": 2 } },
        [comment("c1")],
      ),
    });
    renderWithProviders(<ReviewsSection kind="exam" id="e1" />, { user: null, locale: "en" });

    expect(await screen.findByText("Comment c1")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: en.section.title })).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: en.section.breakdown })).not.toBeInTheDocument();
    expect(screen.queryByText(en.section.count.replace("{count}", "2"))).not.toBeInTheDocument();
  });

  it("shows the average, the count and a five-to-one breakdown from three ratings", async () => {
    mockApi({
      [`GET ${ROUTE}`]: page(
        { ratingAverage: 4.3, ratingCount: 4, stars: { "1": 0, "2": 0, "3": 1, "4": 1, "5": 2 } },
        [comment("c1")],
      ),
    });
    renderWithProviders(<ReviewsSection kind="exam" id="e1" />, { user: null, locale: "en" });

    expect(await screen.findByText("4.3")).toBeInTheDocument();
    expect(screen.getByText(en.section.count.replace("{count}", "4"))).toBeInTheDocument();
    const breakdown = screen.getByRole("list", { name: en.section.breakdown });
    const rows = within(breakdown).getAllByRole("listitem");
    expect(rows).toHaveLength(5);
    expect(rows[0]).toHaveTextContent("5");
    expect(rows[0]).toHaveTextContent("2");
    expect(rows[4]).toHaveTextContent("1");
    expect(rows[4]).toHaveTextContent("0");
  });

  it("loads the next page of comments on Show more, and stops when there are none left", async () => {
    mockApi({
      [`GET ${ROUTE}`]: ({ url }: { url: URL }) => {
        const number = Number(url.searchParams.get("page") ?? "0");
        return number === 0
          ? page({ ratingAverage: 5, ratingCount: 3, stars: { ...zeroStars, "5": 3 } }, [comment("c1")], 0, 2)
          : page({ ratingAverage: 5, ratingCount: 3, stars: { ...zeroStars, "5": 3 } }, [comment("c2")], 1, 2);
      },
    });
    renderWithProviders(<ReviewsSection kind="exam" id="e1" />, { user: null, locale: "en" });

    expect(await screen.findByText("Comment c1")).toBeInTheDocument();
    expect(screen.queryByText("Comment c2")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: en.section.showMore }));

    expect(await screen.findByText("Comment c2")).toBeInTheDocument();
    expect(screen.getByText("Comment c1")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: en.section.showMore })).not.toBeInTheDocument();
  });
});
