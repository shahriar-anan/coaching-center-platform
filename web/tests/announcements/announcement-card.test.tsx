import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AnnouncementCard } from "@/components/student/announcements/announcement-card";
import { apiError, fixtures, mockApi, renderWithProviders } from "../helpers";

/** Lets the query settle before a test asserts that nothing rendered. */
async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 30));
  });
}

/** Five announcements, newest first, as the API sends them. */
const five = [1, 2, 3, 4, 5].map((n) => ({
  id: `a${n}`,
  titleEn: `Notice ${n}`,
  titleBn: `বিজ্ঞপ্তি ${n}`,
  bodyEn: `Body ${n}`,
  bodyBn: `বিবরণ ${n}`,
  createdAt: `2026-11-0${6 - n}T04:00:00Z`,
}));

describe("the announcements card on a package or an exam page", () => {
  it("renders nothing when there are no announcements, or when the read fails", async () => {
    const { calls } = mockApi({ "GET /api/v1/me/announcements": [] });
    const { container } = renderWithProviders(<AnnouncementCard kind="package" id="m1" enabled />, {
      user: fixtures.student,
      locale: "en",
    });
    await waitFor(() => expect(calls).toHaveLength(1));
    await settle();
    expect(container.querySelector("section")).toBeNull();
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  });

  it("renders nothing when the read fails, with no error message", async () => {
    mockApi({ "GET /api/v1/me/announcements": () => apiError(404, "RESOURCE_NOT_FOUND") });
    const { container } = renderWithProviders(<AnnouncementCard kind="exam" id="e1" enabled />, {
      user: fixtures.student,
      locale: "en",
    });
    await settle();
    expect(container.querySelector("section")).toBeNull();
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  });

  it("makes no request when the page did not ask for it", async () => {
    const { calls } = mockApi({ "GET /api/v1/me/announcements": five });
    const { container } = renderWithProviders(<AnnouncementCard kind="package" id="m1" enabled={false} />, {
      user: fixtures.student,
      locale: "en",
    });
    await settle();
    expect(calls).toHaveLength(0);
    expect(container.querySelector("section")).toBeNull();
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
  });

  it("shows the latest three, newest first, and reveals the rest with Show all", async () => {
    const { calls } = mockApi({ "GET /api/v1/me/announcements": five });
    renderWithProviders(<AnnouncementCard kind="package" id="m1" enabled />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("heading", { level: 2, name: "Announcements" })).toBeInTheDocument();
    const shown = () => screen.getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent);
    expect(shown()).toEqual(["Notice 1", "Notice 2", "Notice 3"]);
    expect(calls[0]?.search).toBe("?kind=package&id=m1");

    await userEvent.click(screen.getByRole("button", { name: "Show all" }));
    expect(shown()).toEqual(["Notice 1", "Notice 2", "Notice 3", "Notice 4", "Notice 5"]);

    await userEvent.click(screen.getByRole("button", { name: "Show fewer" }));
    expect(shown()).toEqual(["Notice 1", "Notice 2", "Notice 3"]);
  });

  it("has no Show all button when there are three or fewer", async () => {
    mockApi({ "GET /api/v1/me/announcements": five.slice(0, 3) });
    renderWithProviders(<AnnouncementCard kind="package" id="m1" enabled />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("Notice 3")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Show all" })).not.toBeInTheDocument();
  });

  it("shows the Bangla text to a Bangla reader and falls back to English when a Bangla field is blank", async () => {
    mockApi({
      "GET /api/v1/me/announcements": [
        { id: "x", titleEn: "Schedule change", titleBn: "", bodyEn: "Moved to Friday", bodyBn: null, createdAt: "2026-11-01T04:00:00Z" },
      ],
    });
    renderWithProviders(<AnnouncementCard kind="package" id="m1" enabled />, { user: fixtures.student, locale: "bn" });

    expect(await screen.findByRole("heading", { level: 3, name: "Schedule change" })).toBeInTheDocument();
    expect(screen.getByText("Moved to Friday")).toBeInTheDocument();
  });

  it("falls back to Bangla for an English reader when the English title and body are blank", async () => {
    mockApi({
      "GET /api/v1/me/announcements": [
        { id: "y", titleEn: null, titleBn: "সময়সূচি বদল", bodyEn: "  ", bodyBn: "শুক্রবারে হবে", createdAt: "2026-11-01T04:00:00Z" },
      ],
    });
    renderWithProviders(<AnnouncementCard kind="exam" id="e1" enabled />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByRole("heading", { level: 3, name: "সময়সূচি বদল" })).toBeInTheDocument();
    expect(screen.getByText("শুক্রবারে হবে")).toBeInTheDocument();
  });

  it("shows the first three lines of a long body, and expands it on request", async () => {
    mockApi({
      "GET /api/v1/me/announcements": [
        {
          id: "long",
          titleEn: "Long one",
          titleBn: null,
          bodyEn: ["Line one", "Line two", "Line three", "Line four", "Line five"].join("\n"),
          bodyBn: null,
          createdAt: "2026-11-01T04:00:00Z",
        },
      ],
    });
    renderWithProviders(<AnnouncementCard kind="package" id="m1" enabled />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText(/Line one/)).toBeInTheDocument();
    expect(screen.queryByText(/Line four/)).not.toBeInTheDocument();

    const more = screen.getByRole("button", { name: "Read more" });
    expect(more).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(more);
    expect(screen.getByText(/Line five/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Show less" })).toHaveAttribute("aria-expanded", "true");
  });

  it("shows the text as plain text: markup in a body is shown as tags, not rendered", async () => {
    mockApi({
      "GET /api/v1/me/announcements": [
        {
          id: "html",
          titleEn: "Plain",
          titleBn: null,
          bodyEn: "<b>Bold</b> <img src=x onerror=alert(1)>",
          bodyBn: null,
          createdAt: "2026-11-01T04:00:00Z",
        },
      ],
    });
    renderWithProviders(<AnnouncementCard kind="package" id="m1" enabled />, { user: fixtures.student, locale: "en" });

    expect(await screen.findByText("<b>Bold</b> <img src=x onerror=alert(1)>")).toBeInTheDocument();
    expect(document.querySelector("b")).toBeNull();
    expect(document.querySelector("img")).toBeNull();
  });
});
