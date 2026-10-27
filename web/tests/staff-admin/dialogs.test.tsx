import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AnswerReleaseSwitch } from "@/components/staff/access/answer-release-switch";
import { StudentAccessSection } from "@/components/staff/access/student-access-section";
import { AnnouncementForm } from "@/components/staff/announcements/announcement-form";
import { PendingOrders } from "@/components/staff/orders/pending-orders";
import { DeleteAccountButton } from "@/components/staff/status/account-controls";
import bnStaffAdmin from "@/i18n/messages/bn/staffAdmin";
import { fixtures, renderWithProviders } from "../helpers";
import { mockApi } from "../helpers/mock-api";
import "./copy-setup";

/*
 * Keyboard and focus for every confirm dialog (AG-08): focus moves into the
 * dialog when it opens, Escape closes it, and focus goes back to the control
 * that opened it.
 */

const studentId = "11111111-1111-4111-8111-111111111111";
const orderId = "oooooooo-0000-4000-8000-000000000001";

const access = {
  content: [
    {
      moduleId: "aaaaaaaa-0000-4000-8000-000000000001",
      moduleTitleEn: "19th BJS",
      moduleTitleBn: "১৯তম বিজেএস",
      via: "MANUAL",
      exams: [],
      orderId: null,
      tranId: null,
      grantId: "gggggggg-0000-4000-8000-000000000001",
    },
  ],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
};

const orders = {
  content: [
    {
      orderId,
      tranId: "TRAN-DIALOG-1",
      status: "INITIATED",
      amount: 2500,
      scope: "MODULE",
      moduleTitleEn: "19th BJS",
      moduleTitleBn: "১৯তম বিজেএস",
      examTitleEn: null,
      examTitleBn: null,
      studentId,
      studentName: "রহিম উদ্দিন",
      studentPhone: "01711000003",
      studentCode: "STU-0003",
      createdAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};


const modules = {
  content: [{ id: "aaaaaaaa-0000-4000-8000-000000000001", titleEn: "19th BJS", titleBn: "১৯তম বিজেএস", status: "PUBLISHED" }],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
};

/** Opens the dialog through `trigger`, checks focus moved inside, then Escape, then focus back on `trigger`. */
async function openAndEscape(trigger: HTMLElement, name: string) {
  const user = userEvent.setup();
  await user.click(trigger);

  const dialog = await screen.findByRole("dialog", { name });
  await waitFor(() => expect(dialog).toContainElement(document.activeElement as HTMLElement));

  await user.keyboard("{Escape}");
  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  await waitFor(() => expect(trigger).toHaveFocus());
}

describe("confirm dialogs: focus and Escape", () => {
  it("revoke: focus goes in, Escape closes it, and focus returns to the revoke button", async () => {
    mockApi({ [`GET /api/v1/students/${studentId}/live-exam-access`]: access });
    renderWithProviders(<StudentAccessSection studentId={studentId} studentName="রহিম" />, {
      user: fixtures.masterAdmin,
    });

    const trigger = (await screen.findAllByRole("button", { name: bnStaffAdmin.access.revoke }))[0];
    await openAndEscape(trigger, "রহিম এর অ্যাক্সেস বাতিল করবেন?");
  });

  it("confirm payment: focus goes in, Escape closes it, and focus returns to the confirm button", async () => {
    mockApi({ "GET /api/v1/live-exam-orders": orders });
    renderWithProviders(<PendingOrders />, { user: fixtures.masterAdmin, locale: "bn" });

    const trigger = (await screen.findAllByRole("button", { name: bnStaffAdmin.orders.confirm }))[0];
    await openAndEscape(trigger, "এই পেমেন্ট নিশ্চিত করবেন?");
  });

  it("reject payment: focus goes in, Escape closes it, and focus returns to the reject button", async () => {
    mockApi({ "GET /api/v1/live-exam-orders": orders });
    renderWithProviders(<PendingOrders />, { user: fixtures.masterAdmin, locale: "bn" });

    const trigger = (await screen.findAllByRole("button", { name: bnStaffAdmin.orders.reject }))[0];
    await openAndEscape(trigger, "এই পেমেন্ট ব্যর্থ বলে চিহ্নিত করবেন?");
  });

  it("release answers: focus goes in, Escape closes it, and focus returns to the switch", async () => {
    const examId = "eeeeeeee-0000-4000-8000-000000000001";
    const release = { id: examId, examType: "MCQ", titleEn: "CRPC", titleBn: "সিআরপিসি", answersReleasedAt: null };
    mockApi({ [`GET /api/v1/live-exams/${examId}`]: release });
    renderWithProviders(<AnswerReleaseSwitch examId={examId} />, { user: fixtures.masterAdmin, locale: "bn" });

    const trigger = await screen.findByRole("switch");
    await openAndEscape(trigger, "সিআরপিসি এর উত্তর প্রকাশ করবেন?");
  });

  it("announcement: focus goes in, Escape closes it, and focus returns to the send button", async () => {
    mockApi({ "GET /api/v1/live-exam-modules": modules });
    renderWithProviders(<AnnouncementForm />, { user: fixtures.masterAdmin, locale: "bn" });
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(bnStaffAdmin.announce.titleEn), "Exam moved");
    const trigger = screen.getByRole("button", { name: bnStaffAdmin.announce.send });
    await openAndEscape(trigger, "এই ঘোষণা পাঠাবেন?");
  });

  it("delete account: focus goes in, Escape closes it, and focus returns to the delete button", async () => {
    mockApi({});
    renderWithProviders(<DeleteAccountButton kind="students" id={studentId} name="রহিম" />, {
      user: fixtures.masterAdmin,
      locale: "bn",
    });

    const trigger = screen.getByRole("button", { name: bnStaffAdmin.account.deleteAction });
    await openAndEscape(trigger, "রহিম কে মুছে ফেলবেন?");
  });
});
