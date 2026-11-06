import { act, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Reviews } from "@/components/student/landing/reviews";
import type { FeaturedReview } from "@/lib/api/exam/reviews";
import bn from "@/i18n/messages/bn/reviews";
import en from "@/i18n/messages/en/reviews";
import { mockApi, renderWithProviders } from "../helpers";

const ROUTE = "/api/v1/public/reviews/featured";

function featured(id: string, extra: Partial<FeaturedReview> = {}): FeaturedReview {
  return {
    id,
    name: "Farhana A.",
    rating: 5,
    comment: `Good course ${id}`,
    createdAt: "2026-10-20T04:00:00Z",
    kind: "exam",
    targetId: "e1",
    targetTitleEn: "BJS Prelims",
    targetTitleBn: "বিজেএস প্রিলি",
    ...extra,
  };
}

/** Lets pending reads and timers settle, without polling. */
function flush() {
  return act(() => new Promise((resolve) => setTimeout(resolve, 30)));
}

describe("landing reviews", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders nothing when no review is featured", async () => {
    const api = mockApi({ [`GET ${ROUTE}`]: [] });
    const { container } = renderWithProviders(<Reviews />, { user: null, locale: "en" });

    await waitFor(() => expect(api.calls).toHaveLength(1));
    await flush();
    expect(screen.queryByRole("heading", { name: en.landing.title })).not.toBeInTheDocument();
    expect(container.querySelector("section")).toBeNull();
  });

  it("renders nothing when the read fails", async () => {
    mockApi({});
    renderWithProviders(<Reviews />, { user: null, locale: "en" });

    await waitFor(() => expect(screen.queryByRole("heading", { name: en.landing.title })).not.toBeInTheDocument());
  });

  it("renders a card per featured review with the name, the title, the stars and the comment", async () => {
    mockApi({
      [`GET ${ROUTE}`]: [
        featured("r1"),
        featured("r2", {
          name: "Rafi K.",
          rating: 4,
          comment: "Second comment",
          kind: "package",
          targetId: "m1",
          targetTitleEn: "BCS Package",
          targetTitleBn: "বিসিএস প্যাকেজ",
        }),
      ],
    });
    renderWithProviders(<Reviews />, { user: null, locale: "en" });

    const section = await screen.findByRole("region", { name: en.landing.title });
    expect(within(section).getByText("Good course r1")).toBeInTheDocument();
    expect(within(section).getByText("Second comment")).toBeInTheDocument();
    expect(within(section).getByText("Rafi K.")).toBeInTheDocument();
    expect(within(section).getByText("BCS Package")).toBeInTheDocument();
    expect(within(section).getByRole("img", { name: "4 out of 5" })).toBeInTheDocument();
    // The initial in the circle, from the name.
    // Without a picture the avatar shows the initials of the name: "Farhana A." gives "FA".
    expect(within(section).getAllByText("FA")).not.toHaveLength(0);
  });

  it("shows the reviewer's picture on a featured card, and the initials when there is none", async () => {
    mockApi({
      [`GET ${ROUTE}`]: [
        featured("r1", { photoUrl: "/api/v1/public/reviews/r1/photo?v=1729396800000" }),
        featured("r2", { name: "Rafi K.", photoUrl: null }),
      ],
    });
    renderWithProviders(<Reviews />, { user: null, locale: "en" });

    const section = await screen.findByRole("region", { name: en.landing.title });
    const withPicture = within(section).getByRole("img", { name: "Farhana A." });
    const picture = withPicture.querySelector("img");
    expect(picture).not.toBeNull();
    expect(picture?.getAttribute("src")).toContain("/api/v1/public/reviews/r1/photo?v=1729396800000");
    // No picture: the card shows the initials, and no image.
    const initials = within(section).getByRole("img", { name: "Rafi K." });
    expect(initials.querySelector("img")).toBeNull();
    expect(initials).toHaveTextContent("RK");
  });

  it("shows the title in Bangla when the page is in Bangla", async () => {
    mockApi({ [`GET ${ROUTE}`]: [featured("r1")] });
    renderWithProviders(<Reviews />, { user: null, locale: "bn" });

    expect(await screen.findByRole("heading", { name: bn.landing.title })).toBeInTheDocument();
    expect(screen.getByText("বিজেএস প্রিলি")).toBeInTheDocument();
  });

  it("does not move the cards by itself (no autoplay)", async () => {
    // Only a plain timer is used to wait here: waitFor polls on an interval itself, which the spy would count.
    const interval = vi.spyOn(window, "setInterval");
    mockApi({ [`GET ${ROUTE}`]: [featured("r1"), featured("r2")] });
    renderWithProviders(<Reviews />, { user: null, locale: "en" });

    await flush();
    expect(screen.getByText("Good course r2")).toBeInTheDocument();
    await flush();
    expect(interval).not.toHaveBeenCalled();
  });
});
