import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { NoticeBoardDetail } from "@/components/notice-board/notice-board-detail";
import { NoticeBoardList } from "@/components/notice-board/notice-board-list";
import { mockApi, renderWithProviders } from "../helpers";

function photo(id: string) {
  return { id, url: `/api/v1/public/site-notices/n1/photos/${id}?v=1` };
}

function notice(id: string, photos: { id: string; url: string }[]) {
  return {
    id,
    titleEn: "Routine is out",
    titleBn: "রুটিন প্রকাশিত",
    bodyEn: "Body text",
    bodyBn: "বিষয়বস্তু",
    publishedAt: "2026-11-01T06:00:00Z",
    photos,
    pdf: null,
  };
}

function page(content: ReturnType<typeof notice>[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 };
}

describe("notice photos: one at a time, a tap shows the next", () => {
  it("the list card shows one photo, and each tap on the grey button shows the next, then wraps to the first", async () => {
    mockApi({ "GET /api/v1/public/site-notices": page([notice("n1", [photo("p1"), photo("p2"), photo("p3")])]) });
    renderWithProviders(<NoticeBoardList />, { user: null, locale: "en" });

    expect(await screen.findByRole("img", { name: /photo 1 of 3/ })).toBeInTheDocument();
    expect(screen.getAllByRole("img")).toHaveLength(1);
    expect(screen.getByText("1 / 3")).toBeInTheDocument();

    const next = screen.getByRole("button", { name: "Next photo" });
    await userEvent.click(next);
    expect(await screen.findByRole("img", { name: /photo 2 of 3/ })).toBeInTheDocument();
    await userEvent.click(next);
    expect(await screen.findByRole("img", { name: /photo 3 of 3/ })).toBeInTheDocument();
    await userEvent.click(next);
    expect(await screen.findByRole("img", { name: /photo 1 of 3/ })).toBeInTheDocument();
  });

  it("the Read more button sits on the card and goes to the notice", async () => {
    mockApi({ "GET /api/v1/public/site-notices": page([notice("n1", [photo("p1"), photo("p2")])]) });
    renderWithProviders(<NoticeBoardList />, { user: null, locale: "en" });

    expect(await screen.findByRole("link", { name: /Read more/ })).toHaveAttribute("href", "/notice-board/n1");
  });

  it("a card with no photos shows no photo and no grey button, and still has its Read more button", async () => {
    mockApi({ "GET /api/v1/public/site-notices": page([notice("n2", [])]) });
    renderWithProviders(<NoticeBoardList />, { user: null, locale: "en" });

    expect(await screen.findByRole("link", { name: /Read more/ })).toHaveAttribute("href", "/notice-board/n2");
    expect(screen.queryByRole("img")).toBeNull();
    expect(screen.queryByRole("button", { name: "Next photo" })).toBeNull();
  });

  it("the notice page shows one photo at a time, and its grey button advances it", async () => {
    mockApi({ "GET /api/v1/public/site-notices/n1": notice("n1", [photo("p1"), photo("p2")]) });
    renderWithProviders(<NoticeBoardDetail id="n1" />, { user: null, locale: "en" });

    expect(await screen.findByRole("img", { name: /photo 1 of 2/ })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Next photo" }));
    expect(await screen.findByRole("img", { name: /photo 2 of 2/ })).toBeInTheDocument();
  });
});
