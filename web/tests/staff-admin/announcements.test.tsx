import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import AnnouncementsPage from "@/app/(staff)/staff/announcements/page";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";
import "./copy-setup";

const modules = {
  content: [{ id: "aaaaaaaa-0000-4000-8000-000000000001", titleEn: "19th BJS", titleBn: "১৯তম বিজেএস", status: "PUBLISHED" }],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
};

describe("announcements page", () => {
  it("asks for at least one of the four fields before it sends", async () => {
    const { calls } = mockApi({ "GET /api/v1/live-exam-modules": modules });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin });
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: "ঘোষণা পাঠান" }));

    expect(await screen.findByText("অন্তত একটি শিরোনাম বা বার্তা লিখুন।")).toBeInTheDocument();
    expect(calls.some((call) => call.method === "POST")).toBe(false);
  });

  it("needs a module when the audience is one module", async () => {
    mockApi({ "GET /api/v1/live-exam-modules": modules });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin });
    const user = userEvent.setup();

    await user.type(screen.getByLabelText("শিরোনাম (বাংলা)"), "পরীক্ষার সময়সূচি");
    await user.selectOptions(screen.getByLabelText("কাদের জন্য"), "MODULE");
    await user.click(screen.getByRole("button", { name: "ঘোষণা পাঠান" }));

    expect(await screen.findByText("একটি প্যাকেজ বাছুন।")).toBeInTheDocument();
  });

  it("names the audience in the confirm dialog, then sends one announcement", async () => {
    const { calls } = mockApi({
      "GET /api/v1/live-exam-modules": modules,
      "POST /api/v1/announcements": {
        id: "bbbbbbbb-0000-4000-8000-000000000001",
        audience: "ALL",
        moduleId: null,
        recipients: 12,
        createdAt: "2026-10-26T06:00:00Z",
      },
    });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.masterAdmin });
    const user = userEvent.setup();

    await user.type(screen.getByLabelText("শিরোনাম (ইংরেজি)"), "Exam moved");
    await user.click(screen.getByRole("button", { name: "ঘোষণা পাঠান" }));
    expect(await screen.findByText("সব শিক্ষার্থীর কাছে যাবে। পাঠানোর পর বদলানো যাবে না।")).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: "ঘোষণা পাঠান" }).at(-1)!);

    await waitFor(() =>
      expect(calls.find((call) => call.method === "POST")?.body).toEqual({
        titleEn: "Exam moved",
        titleBn: "",
        bodyEn: "",
        bodyBn: "",
        audience: "ALL",
      }),
    );
    expect(await screen.findByText("ঘোষণা পাঠানো হয়েছে। ১২ জন শিক্ষার্থী পেয়েছেন।")).toBeInTheDocument();
  });

  it("gives System Admin no announcements page: no form, and no request", async () => {
    const { calls } = mockApi({ "GET /api/v1/live-exam-modules": modules });
    renderWithProviders(<AnnouncementsPage />, { user: fixtures.systemAdmin });

    expect(screen.queryByText("ঘোষণা পাঠাতে পারেন শুধু মাস্টার অ্যাডমিন। এখানে শুধু তথ্য দেখা যায়।")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "ঘোষণা পাঠান" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("শিরোনাম (বাংলা)")).not.toBeInTheDocument();
    expect(calls.some((call) => call.path === "/api/v1/announcements")).toBe(false);
  });
});
