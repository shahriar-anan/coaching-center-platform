import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import NoticesPage from "@/app/(site)/notices/page";
import { announcementText } from "@/app/(site)/notices/_components/announcement";
import { fixtures, mockApi, renderWithProviders } from "../helpers";

const longBody = ["প্রথম লাইন", "দ্বিতীয় লাইন", "তৃতীয় লাইন", "চতুর্থ লাইন", "পঞ্চম লাইন"].join("\n");

const announcement = {
  id: "a1",
  kind: "ANNOUNCEMENT",
  createdAt: "2026-11-01T04:00:00Z",
  readAt: null,
  exam: null,
  score: null,
  maximum: null,
  passed: null,
  announcement: { titleEn: "Centre closed", titleBn: "কেন্দ্র বন্ধ", bodyEn: "Closed on Friday", bodyBn: longBody },
};

describe("announcement text (S-020)", () => {
  const both = { titleEn: "Centre closed", titleBn: "কেন্দ্র বন্ধ", bodyEn: "Closed", bodyBn: "বন্ধ" };

  it("uses the student's language", () => {
    expect(announcementText(both, "bn")).toEqual({ title: "কেন্দ্র বন্ধ", body: "বন্ধ" });
    expect(announcementText(both, "en")).toEqual({ title: "Centre closed", body: "Closed" });
  });

  it("falls back to the other language when one is blank", () => {
    const noBangla = { ...both, titleBn: "", bodyBn: null };
    expect(announcementText(noBangla, "bn")).toEqual({ title: "Centre closed", body: "Closed" });
    const noEnglish = { ...both, titleEn: null, bodyEn: "  " };
    expect(announcementText(noEnglish, "en")).toEqual({ title: "কেন্দ্র বন্ধ", body: "বন্ধ" });
  });

  it("is empty when both languages are blank, and when there is no announcement", () => {
    expect(announcementText({ titleEn: null, titleBn: null, bodyEn: null, bodyBn: null }, "bn")).toEqual({
      title: "",
      body: "",
    });
    expect(announcementText(null, "bn")).toEqual({ title: "", body: "" });
  });
});

describe("announcement row (S-020)", () => {
  it("shows the first lines, expands on request, and marks the notice read", async () => {
    const { calls } = mockApi({
      "GET /api/v1/me/notices": { content: [announcement], page: 0, totalPages: 1 },
      "POST /api/v1/me/notices/a1/read": { ...announcement, readAt: "2026-11-01T05:00:00Z" },
    });
    renderWithProviders(<NoticesPage />, { user: fixtures.student });

    expect(await screen.findByText("কেন্দ্র বন্ধ")).toBeInTheDocument();
    expect(screen.queryByText(/পঞ্চম লাইন/)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "আরও দেখুন" }));
    expect(screen.getByText(/পঞ্চম লাইন/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "কম দেখুন" })).toHaveAttribute("aria-expanded", "true");
    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
  });

  it("shows the English text to an English reader and has no edit or delete control", async () => {
    mockApi({ "GET /api/v1/me/notices": { content: [announcement], page: 0, totalPages: 1 } });
    renderWithProviders(<NoticesPage />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("Centre closed")).toBeInTheDocument();
    expect(screen.getByText("Closed on Friday")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /edit|delete|সম্পাদনা|মুছুন/i })).not.toBeInTheDocument();
  });
});
